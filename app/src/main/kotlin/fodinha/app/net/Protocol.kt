package fodinha.app.net

import fodinha.engine.GameAction
import fodinha.engine.PlayerView
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Uma linha JSON por mensagem. Mesmo protocolo em WiFi e Bluetooth. */
val ProtocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

@Serializable
data class LobbyInfo(
    val roomName: String,
    val seats: List<LobbySeat>,
    val started: Boolean = false,
)

@Serializable
data class LobbySeat(val id: Int, val name: String, val isBot: Boolean, val connected: Boolean)

/** Cliente -> host. */
@Serializable
sealed interface ClientMsg {
    @Serializable
    data class Hello(val name: String) : ClientMsg

    @Serializable
    data class Play(val action: GameAction) : ClientMsg

    /** So o dono da sala. */
    @Serializable
    data object StartGame : ClientMsg

    @Serializable
    data object AddBot : ClientMsg
}

/** Host -> cliente. */
@Serializable
sealed interface HostMsg {
    /** Confirma o assento atribuido ao cliente. */
    @Serializable
    data class Welcome(val playerId: Int, val isOwner: Boolean) : HostMsg

    @Serializable
    data class Lobby(val info: LobbyInfo) : HostMsg

    @Serializable
    data class View(val view: PlayerView) : HostMsg

    @Serializable
    data class Error(val message: String) : HostMsg
}
