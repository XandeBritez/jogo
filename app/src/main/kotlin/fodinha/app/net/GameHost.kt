package fodinha.app.net

import fodinha.engine.Bot
import fodinha.engine.Engine
import fodinha.engine.GameAction
import fodinha.engine.GameState
import fodinha.engine.Phase
import fodinha.engine.viewFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Host autoritativo. Roda a Engine, dirige os bots e transmite uma
 * PlayerView redigida por assento. Clientes so mandam acao e renderizam.
 *
 * A mesma classe serve para bot local, WiFi e Bluetooth: muda so o transporte.
 */
class GameHost(
    private val scope: CoroutineScope,
    private val roomName: String,
    private val ownerName: String,
    private val botDelayMillis: Long = 700L,
    /** Tempo para o humano prever. Estourou, o host sorteia uma previsao legal. */
    private val bidSeconds: Int = 60,
    /** Tempo para o humano jogar. Estourou, o host sorteia uma carta da mao dele. */
    private val playSeconds: Int = 40,
    /** Quanto a vaza fechada fica exposta na mesa antes de ser recolhida. */
    private val trickRevealMillis: Long = 5_000L,
) {
    private data class Seat(
        val id: Int,
        var name: String,
        var isBot: Boolean,
        var connected: Boolean,
        val sink: (suspend (HostMsg) -> Unit)?,
    )

    private val seats = mutableListOf<Seat>()
    private val mutex = Mutex()
    private var state: GameState? = null
    private var started = false
    private var driving = false

    /** Coroutines que este host abriu; canceladas no close para nao vazarem. */
    private val jobs = mutableListOf<Job>()

    /** Laco que toca bots e recolhe a mesa; roda solto, nunca preso a quem chamou. */
    private var driveJob: Job? = null

    /** Relogio do turno do humano da vez. */
    private var turnJob: Job? = null
    private var turnDeadlineMs: Long? = null

    /** Sobe a cada turno novo; um relogio velho que dispare atrasado se reconhece e desiste. */
    private var turnEpoch = 0L

    /** Fila de mensagens para o jogador local (o dono do aparelho). */
    val localInbox = Channel<HostMsg>(Channel.BUFFERED)

    private var remote: HostTransport? = null

    /** Assento 0 e sempre o dono do aparelho. */
    fun createOwnerSeat(): Int {
        seats += Seat(0, ownerName, isBot = false, connected = true, sink = { localInbox.send(it) })
        return 0
    }

    suspend fun addBot(name: String? = null): Int = mutex.withLock {
        require(!started) { "jogo ja comecou" }
        require(seats.size < 6) { "mesa cheia" }
        val id = seats.size
        seats += Seat(id, name ?: "Bot ${botCount() + 1}", isBot = true, connected = true, sink = null)
        broadcastLobbyLocked()
        id
    }

    /**
     * Remove o ultimo bot da mesa. So o ultimo: os ids precisam continuar
     * contiguos porque a Engine indexa jogadores pela posicao, e reindexar
     * invalidaria o sink de quem ja esta conectado.
     */
    suspend fun removeLastBot() = mutex.withLock {
        if (started) return@withLock
        val last = seats.lastOrNull() ?: return@withLock
        if (!last.isBot) return@withLock
        seats.removeAt(seats.size - 1)
        broadcastLobbyLocked()
    }

    private fun botCount() = seats.count { it.isBot }

    /** Liga um transporte remoto (WiFi ou Bluetooth) a esta mesa. */
    fun attachRemote(transport: HostTransport) {
        remote = transport
        jobs += scope.launch {
            transport.start(
                onJoin = { name -> reserveSeat(name, transport) },
                onLeave = { id -> markDisconnected(id) },
            )
        }
        jobs += scope.launch {
            transport.inbound.collect { (playerId, msg) -> handle(playerId, msg) }
        }
    }

    /**
     * Reserva o assento e devolve o id. NAO manda nada: o transporte ainda nao
     * registrou o canal de saida deste cliente, entao qualquer envio aqui se
     * perderia. A resposta sai no `handle` do Hello, que chega pelo `inbound`
     * depois do canal estar pronto.
     */
    private suspend fun reserveSeat(name: String, transport: HostTransport): Int = mutex.withLock {
        // Reaproveita assento de quem caiu com o mesmo nome; senao abre um novo.
        val existing = seats.firstOrNull { it.name == name && !it.connected && !it.isBot }
        if (existing != null) {
            existing.connected = true
            return@withLock existing.id
        }
        if (started || seats.size >= 6) return@withLock -1
        val id = seats.size
        seats += Seat(id, name, isBot = false, connected = true, sink = { transport.sendTo(id, it) })
        id
    }

    private suspend fun markDisconnected(id: Int) = mutex.withLock {
        seats.firstOrNull { it.id == id }?.connected = false
        broadcastLobbyLocked()
    }

    /** Manda o estado atual a quem acabou de entrar ou de reconectar. */
    private suspend fun greetLocked(id: Int) {
        val s = seats.firstOrNull { it.id == id } ?: return
        s.sink?.invoke(HostMsg.Welcome(id, isOwner = id == 0))
        val g = state
        if (g == null) broadcastLobbyLocked() else s.sink?.invoke(HostMsg.View(g.viewFor(id)))
    }

    suspend fun handle(playerId: Int, msg: ClientMsg) {
        when (msg) {
            is ClientMsg.Hello -> mutex.withLock {
                seats.firstOrNull { it.id == playerId }?.name = msg.name
                greetLocked(playerId)
                broadcastLobbyLocked()
            }

            ClientMsg.AddBot -> if (playerId == 0) addBot()

            ClientMsg.StartGame -> if (playerId == 0) startGame()

            is ClientMsg.Play -> submit(playerId, msg.action)

            // So o dono da sala avanca a rodada: senao um cliente apressado
            // pularia o resumo dos outros.
            ClientMsg.NextRound -> if (playerId == 0) advanceRound()
        }
    }

    suspend fun startGame() {
        mutex.withLock {
            if (started) return@withLock
            if (seats.size < 2) {
                seats.firstOrNull { it.id == 0 }?.sink?.invoke(HostMsg.Error("Precisa de pelo menos 2 jogadores"))
                return@withLock
            }
            started = true
            state = Engine.newGame(seats.map { it.name to it.isBot })
            syncLocked()
        }
        kickDrive()
    }

    suspend fun submit(playerId: Int, action: GameAction) {
        mutex.withLock {
            val g = state ?: return@withLock
            if (action.playerId != playerId) return@withLock
            val next = runCatching { Engine.reduce(g, action) }
            next.onFailure { e ->
                seats.firstOrNull { it.id == playerId }?.sink?.invoke(
                    HostMsg.Error(e.message ?: "jogada invalida")
                )
            }
            next.onSuccess {
                state = it
                syncLocked()
            }
        }
        kickDrive()
    }

    suspend fun advanceRound() {
        mutex.withLock {
            val g = state ?: return@withLock
            if (g.phase != Phase.ROUND_OVER) return@withLock
            state = Engine.nextRound(g)
            syncLocked()
        }
        kickDrive()
    }

    /**
     * Toca os bots ate travar num jogador de carne e osso. A Engine nao
     * distingue bot de humano, entao basta injetar a acao.
     *
     * Humano nunca joga sozinho, nem na rodada cega de 1 carta: ele nao sabe
     * qual e a carta, mas e ele quem aperta. Quem cobre a demora e o relogio
     * do turno, nao este laco.
     */
    /**
     * Dispara o laco sem prender quem chamou. Importa porque o laco dorme:
     * 5s exibindo a vaza, mais a pausa do bot. Quem submeteu a jogada nao
     * pode ficar esperando isso.
     */
    private fun kickDrive() {
        driveJob = scope.launch { driveBots() }
    }

    private suspend fun driveBots() {
        // Um loop de cada vez. Sem isto, uma acao remota e uma local chegando
        // juntas disparam dois loops que calculam a mesma jogada de bot e a
        // segunda falha silenciosamente no reduce.
        mutex.withLock {
            if (driving) return
            driving = true
        }
        try {
            driveLoop()
        } finally {
            mutex.withLock { driving = false }
        }
    }

    private suspend fun driveLoop() {
        while (true) {
            // Vaza fechada: segura as cartas na mesa para todo mundo ver.
            val revealing = mutex.withLock { state?.phase == Phase.TRICK_REVEAL }
            if (revealing) {
                delay(trickRevealMillis)
                mutex.withLock {
                    val g = state ?: return
                    if (g.phase != Phase.TRICK_REVEAL) return@withLock
                    state = Engine.closeTrick(g)
                    syncLocked()
                }
                continue
            }

            val action = mutex.withLock {
                val g = state ?: return
                if (g.phase != Phase.BIDDING && g.phase != Phase.PLAYING) return
                val current = g.currentPlayerId ?: return
                if (!g.player(current).isBot) return
                when (g.phase) {
                    Phase.BIDDING -> Bot.decideBid(g, current)
                    Phase.PLAYING -> Bot.decidePlay(g, current)
                    else -> return
                }
            }
            delay(botDelayMillis)
            mutex.withLock {
                val g = state ?: return
                state = runCatching { Engine.reduce(g, action) }.getOrElse { return }
                syncLocked()
            }
        }
    }

    /** Rearma o relogio do turno e manda o estado novo. Chame com o mutex segurado. */
    private suspend fun syncLocked() {
        armTurnTimerLocked()
        broadcastViewsLocked()
    }

    /**
     * Da corda no relogio do humano da vez. Bot nao tem prazo: ele responde
     * sozinho. Fora de BIDDING e PLAYING nao ha turno, entao nao ha relogio.
     */
    private fun armTurnTimerLocked() {
        turnJob?.cancel()
        turnJob = null
        turnDeadlineMs = null

        val g = state ?: return
        if (g.phase != Phase.BIDDING && g.phase != Phase.PLAYING) return
        val current = g.currentPlayerId ?: return
        if (g.player(current).isBot) return

        val limitMs = (if (g.phase == Phase.BIDDING) bidSeconds else playSeconds) * 1000L
        val epoch = ++turnEpoch
        turnDeadlineMs = System.currentTimeMillis() + limitMs
        turnJob = scope.launch {
            delay(limitMs)
            onTurnTimeout(epoch, current)
        }
    }

    /** Estourou o tempo: o host sorteia uma jogada legal no lugar do jogador. */
    private suspend fun onTurnTimeout(epoch: Long, playerId: Int) {
        val action = mutex.withLock {
            // Chegou atrasado, depois de o jogador ja ter agido.
            if (epoch != turnEpoch) return
            val g = state ?: return
            if (g.currentPlayerId != playerId) return
            when (g.phase) {
                Phase.BIDDING -> GameAction.Bid(playerId, Engine.legalBids(g).random())
                Phase.PLAYING ->
                    if (g.isBlindOneCard) GameAction.PlayBlind(playerId)
                    else GameAction.PlayCard(playerId, Engine.legalPlays(g, playerId).random())
                else -> return
            }
        }
        submit(playerId, action)
    }

    /** Chame com o mutex ja segurado. */
    private suspend fun broadcastViewsLocked() {
        val g = state ?: return
        val left = turnDeadlineMs?.let { deadline ->
            ((deadline - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L).toInt()
        }
        for (seat in seats) {
            seat.sink?.invoke(HostMsg.View(g.viewFor(seat.id, left)))
        }
    }

    private suspend fun broadcastLobbyLocked() {
        val info = LobbyInfo(
            roomName = roomName,
            seats = seats.map { LobbySeat(it.id, it.name, it.isBot, it.connected) },
            started = started,
        )
        for (seat in seats) seat.sink?.invoke(HostMsg.Lobby(info))
    }

    suspend fun publishLobby() = mutex.withLock { broadcastLobbyLocked() }

    fun close() {
        turnJob?.cancel()
        turnJob = null
        driveJob?.cancel()
        driveJob = null
        jobs.forEach { it.cancel() }
        jobs.clear()
        remote?.close()
        remote = null
        localInbox.close()
    }
}
