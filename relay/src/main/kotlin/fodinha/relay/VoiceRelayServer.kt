package fodinha.relay

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap

/*
 * Espelho de audio pela internet. UDP, na mesma porta do relay TCP.
 *
 * Igual ao VoiceRelay do app (WiFi), so que com varias salas: cada datagrama
 * traz o codigo da sala, e o relay so repassa entre quem esta na mesma.
 *
 * Datagrama:
 *   [0]    tipo: 1 = audio, 2 = presenca (keepalive)
 *   [1]    id do assento de quem manda
 *   [2..3] sequencia (so audio)
 *   [4..8] codigo da sala, 5 bytes ASCII
 *   [9..]  quadro AMR-WB (audio) ou nada (presenca)
 *
 * Nao decodifica nada: repassa bytes. O NAT do celular deixa a resposta
 * passar porque o celular mandou primeiro (a presenca a cada segundo mantem
 * o buraco aberto).
 */

const val VOICE_HEADER = 9
const val VOICE_CODE_OFFSET = 4
const val VOICE_CODE_LEN = 5
const val VOICE_TYPE_AUDIO: Byte = 1
const val VOICE_TYPE_PRESENCE: Byte = 2
const val VOICE_PEER_TIMEOUT_MS = 5_000L
const val VOICE_MAX_PACKET = 512

class VoiceRelayServer(
    port: Int,
    /** Sala existe no relay TCP? Sem sala nao ha voz: evita virar espelho publico. */
    private val roomExists: (String) -> Boolean,
) {
    private class Peer(@Volatile var address: SocketAddress, @Volatile var lastSeen: Long)

    private val socket = DatagramSocket(port)
    private val rooms = ConcurrentHashMap<String, ConcurrentHashMap<Int, Peer>>()

    @Volatile private var running = true

    val port: Int get() = socket.localPort
    val roomCount: Int get() = rooms.size

    fun start() {
        Thread({ loop() }, "voice-relay").apply { isDaemon = true }.start()
    }

    private fun loop() {
        val buf = ByteArray(VOICE_MAX_PACKET)
        val packet = DatagramPacket(buf, buf.size)
        var lastSweep = 0L
        while (running) {
            runCatching { socket.receive(packet) }.onFailure { return }
            if (packet.length < VOICE_HEADER) continue
            val type = buf[0]
            val from = buf[1].toInt()
            val code = String(buf, VOICE_CODE_OFFSET, VOICE_CODE_LEN, Charsets.US_ASCII)
            val now = System.currentTimeMillis()

            if (!roomExists(code)) {
                rooms.remove(code)
                continue
            }
            val peers = rooms.getOrPut(code) { ConcurrentHashMap() }
            val peer = peers[from]
            if (peer == null) {
                peers[from] = Peer(packet.socketAddress, now)
            } else {
                peer.address = packet.socketAddress
                peer.lastSeen = now
            }

            if (now - lastSweep > 1_000) {
                lastSweep = now
                sweep(now)
            }

            if (type != VOICE_TYPE_AUDIO) continue
            val out = DatagramPacket(buf, packet.length)
            for ((id, p) in peers) {
                if (id == from) continue
                out.socketAddress = p.address
                runCatching { socket.send(out) }
            }
        }
    }

    private fun sweep(now: Long) {
        for ((code, peers) in rooms) {
            peers.entries.removeIf { now - it.value.lastSeen > VOICE_PEER_TIMEOUT_MS }
            if (peers.isEmpty() || !roomExists(code)) rooms.remove(code)
        }
    }

    fun close() {
        running = false
        socket.close()
        rooms.clear()
    }
}
