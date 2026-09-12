package fodinha.app

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fodinha.app.net.BluetoothClientTransport
import fodinha.app.net.BluetoothHostTransport
import fodinha.app.net.ClientMsg
import fodinha.app.net.ClientTransport
import fodinha.app.net.DiscoveredRoom
import fodinha.app.net.GameHost
import fodinha.app.net.HostMsg
import fodinha.app.net.LobbyInfo
import fodinha.app.net.TransportKind
import fodinha.app.net.WifiClientTransport
import fodinha.app.net.WifiHostTransport
import fodinha.app.net.bluetoothAdapter
import fodinha.app.net.discoverRooms
import fodinha.app.ui.GameSettings
import fodinha.app.ui.SettingsStore
import fodinha.engine.GameAction
import fodinha.engine.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Screen { HOME, LOBBY, TABLE, OPTIONS }

data class UiState(
    val screen: Screen = Screen.HOME,
    val playerName: String = "Voce",
    val kind: TransportKind = TransportKind.LOCAL,
    val isHost: Boolean = false,
    val myId: Int = 0,
    val lobby: LobbyInfo? = null,
    val view: PlayerView? = null,
    val rooms: List<DiscoveredRoom> = emptyList(),
    val btDevices: List<BluetoothDevice> = emptyList(),
    val error: String? = null,
    val connecting: Boolean = false,
    val settings: GameSettings = GameSettings(),
)

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)

    private val _ui = MutableStateFlow(
        UiState(playerName = store.playerName(), settings = store.load()),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var host: GameHost? = null
    private var client: ClientTransport? = null
    private var discoveryJob: Job? = null

    fun setName(name: String) {
        store.savePlayerName(name)
        _ui.update { it.copy(playerName = name) }
    }

    /** Preferencia visual: grava na hora, para valer na proxima abertura. */
    fun setSettings(settings: GameSettings) {
        store.save(settings)
        _ui.update { it.copy(settings = settings) }
    }

    fun openOptions() = _ui.update { it.copy(screen = Screen.OPTIONS) }

    fun closeOptions() = _ui.update { it.copy(screen = Screen.HOME) }

    fun dismissError() = _ui.update { it.copy(error = null) }

    // ---------- Modo bot (tudo local) ----------

    fun startBotGame(bots: Int) {
        val h = newHost(roomName = "Local")
        viewModelScope.launch {
            repeat(bots) { h.addBot() }
            _ui.update { it.copy(kind = TransportKind.LOCAL, isHost = true, myId = 0, screen = Screen.LOBBY) }
            h.publishLobby()
        }
    }

    // ---------- Sala WiFi ----------

    fun hostWifiRoom(roomName: String) {
        val h = newHost(roomName)
        val transport = WifiHostTransport(getApplication(), viewModelScope, roomName)
        h.attachRemote(transport)
        _ui.update {
            it.copy(kind = TransportKind.WIFI, isHost = true, myId = 0, screen = Screen.LOBBY)
        }
        viewModelScope.launch { h.publishLobby() }
    }

    fun startRoomDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            discoverRooms(getApplication()).collect { rooms ->
                _ui.update { it.copy(rooms = rooms) }
            }
        }
    }

    fun stopRoomDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun joinWifiRoom(room: DiscoveredRoom) {
        connectAs(TransportKind.WIFI) {
            WifiClientTransport(room.host, room.port, _ui.value.playerName)
        }
    }

    // ---------- Bluetooth ----------

    fun hostBluetoothRoom(roomName: String) {
        val adapter = bluetoothAdapter(getApplication())
        if (adapter == null || !adapter.isEnabled) {
            _ui.update { it.copy(error = "Ligue o Bluetooth para abrir a sala") }
            return
        }
        val h = newHost(roomName)
        h.attachRemote(BluetoothHostTransport(adapter, viewModelScope))
        _ui.update {
            it.copy(kind = TransportKind.BLUETOOTH, isHost = true, myId = 0, screen = Screen.LOBBY)
        }
        viewModelScope.launch { h.publishLobby() }
    }

    fun loadPairedDevices() {
        val adapter = bluetoothAdapter(getApplication())
        if (adapter == null || !adapter.isEnabled) {
            _ui.update { it.copy(error = "Ligue o Bluetooth") }
            return
        }
        val devices = runCatching { adapter.bondedDevices.toList() }.getOrElse { emptyList() }
        _ui.update { it.copy(btDevices = devices) }
    }

    fun joinBluetooth(device: BluetoothDevice) {
        val adapter = bluetoothAdapter(getApplication()) ?: return
        connectAs(TransportKind.BLUETOOTH) {
            BluetoothClientTransport(adapter, device, _ui.value.playerName)
        }
    }

    // ---------- Comum ----------

    private fun newHost(roomName: String): GameHost {
        closeAll()
        val h = GameHost(viewModelScope, roomName, _ui.value.playerName)
        h.createOwnerSeat()
        host = h
        viewModelScope.launch {
            for (msg in h.localInbox) applyHostMsg(msg)
        }
        return h
    }

    private fun connectAs(kind: TransportKind, factory: () -> ClientTransport) {
        closeAll()
        val c = factory()
        client = c
        _ui.update { it.copy(kind = kind, isHost = false, connecting = true, screen = Screen.LOBBY) }
        viewModelScope.launch {
            launch { c.inbound.collect { applyHostMsg(it) } }
            runCatching { c.connect() }.onFailure { e ->
                _ui.update {
                    it.copy(connecting = false, error = "Falha ao conectar: ${e.message}", screen = Screen.HOME)
                }
            }
        }
    }

    private fun applyHostMsg(msg: HostMsg) {
        when (msg) {
            is HostMsg.Welcome -> _ui.update { it.copy(myId = msg.playerId, connecting = false) }
            is HostMsg.Lobby -> _ui.update { it.copy(lobby = msg.info, connecting = false) }
            is HostMsg.View -> _ui.update {
                it.copy(view = msg.view, screen = Screen.TABLE, connecting = false)
            }
            is HostMsg.Error -> _ui.update { it.copy(error = msg.message) }
        }
    }

    private fun send(msg: ClientMsg) {
        val h = host
        if (h != null) {
            viewModelScope.launch { h.handle(_ui.value.myId, msg) }
        } else {
            viewModelScope.launch { client?.send(msg) }
        }
    }

    fun addBot() = send(ClientMsg.AddBot)

    fun removeBot() {
        val h = host ?: return
        viewModelScope.launch { h.removeLastBot() }
    }

    fun startGame() = send(ClientMsg.StartGame)

    fun bid(amount: Int) = send(ClientMsg.Play(GameAction.Bid(_ui.value.myId, amount)))

    fun playCard(card: fodinha.engine.Card) =
        send(ClientMsg.Play(GameAction.PlayCard(_ui.value.myId, card)))

    /** Rodada cega de 1 carta: mando a carta sem saber qual e. */
    fun playBlind() = send(ClientMsg.Play(GameAction.PlayBlind(_ui.value.myId)))

    /** Rodada cega de 9: escolho a posicao; que carta era, so a mesa conta. */
    fun playBlindAt(index: Int) =
        send(ClientMsg.Play(GameAction.PlayBlindAt(_ui.value.myId, index)))

    fun nextRound() = send(ClientMsg.NextRound)

    fun leave() {
        closeAll()
        _ui.update {
            UiState(playerName = it.playerName, settings = it.settings)
        }
    }

    private fun closeAll() {
        host?.close()
        host = null
        client?.close()
        client = null
        stopRoomDiscovery()
    }

    override fun onCleared() {
        closeAll()
        super.onCleared()
    }
}

private inline fun MutableStateFlow<UiState>.update(block: (UiState) -> UiState) {
    value = block(value)
}
