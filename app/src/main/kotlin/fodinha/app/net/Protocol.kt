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
data class LobbySeat(
    val id: Int,
    val name: String,
    val isBot: Boolean,
    val connected: Boolean,
    /** Esta no chat de voz da sala. So faz sentido em sala WiFi. */
    val inVoice: Boolean = false,
    /** Microfone fechado por vontade propria. */
    val micMuted: Boolean = false,
)

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

    // Chat de voz. So o controle passa por aqui: o audio vai por UDP, num
    // canal proprio, para nao disputar o socket do jogo.

    @Serializable
    data object VoiceJoin : ClientMsg

    @Serializable
    data object VoiceLeave : ClientMsg

    @Serializable
    data class VoiceMic(val muted: Boolean) : ClientMsg
}

/** Host -> cliente. */
@Serializable
sealed interface HostMsg {
    /**
     * Confirma o assento atribuido ao cliente. `voicePort` e a porta UDP do
     * chat de voz no host; 0 quando a sala nao oferece voz (Bluetooth, local).
     */
    @Serializable
    data class Welcome(val playerId: Int, val isOwner: Boolean, val voicePort: Int = 0) : HostMsg

    @Serializable
    data class Lobby(val info: LobbyInfo) : HostMsg

    @Serializable
    data class View(val view: PlayerView) : HostMsg

    @Serializable
    data class Error(val message: String) : HostMsg
}
