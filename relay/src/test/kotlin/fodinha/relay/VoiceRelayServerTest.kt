package fodinha.relay

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

class VoiceRelayServerTest {
    private val openRooms = mutableSetOf("ABCDE")
    private lateinit var relay: VoiceRelayServer

    @Before fun up() { relay = VoiceRelayServer(0) { it in openRooms }.also { it.start() } }
    @After fun down() { relay.close() }

    private fun packet(type: Byte, id: Int, code: String, payload: ByteArray = ByteArray(0)): ByteArray =
        byteArrayOf(type, id.toByte(), 0, 0) + code.toByteArray(Charsets.US_ASCII) + payload

    private fun peer(): DatagramSocket = DatagramSocket().apply { soTimeout = 800 }

    private fun DatagramSocket.sendTo(bytes: ByteArray) =
        send(DatagramPacket(bytes, bytes.size, InetSocketAddress("127.0.0.1", relay.port)))

    private fun DatagramSocket.recv(): ByteArray? = try {
        val buf = ByteArray(512)
        val p = DatagramPacket(buf, buf.size)
        receive(p)
        buf.copyOf(p.length)
    } catch (e: SocketTimeoutException) {
        null
    }

    @Test
    fun `audio vai para os outros da mesma sala, nao para quem mandou`() {
        val a = peer(); val b = peer()
        a.sendTo(packet(VOICE_TYPE_PRESENCE, 0, "ABCDE"))
        b.sendTo(packet(VOICE_TYPE_PRESENCE, 1, "ABCDE"))
        Thread.sleep(50)

        val frame = packet(VOICE_TYPE_AUDIO, 0, "ABCDE", byteArrayOf(9, 8, 7))
        a.sendTo(frame)
        assertEquals(frame.toList(), b.recv()!!.toList())
        assertNull(a.recv())
        a.close(); b.close()
    }

    @Test
    fun `salas diferentes nao se ouvem`() {
        openRooms += "ZZZZZ"
        val a = peer(); val z = peer()
        a.sendTo(packet(VOICE_TYPE_PRESENCE, 0, "ABCDE"))
        z.sendTo(packet(VOICE_TYPE_PRESENCE, 0, "ZZZZZ"))
        Thread.sleep(50)
        a.sendTo(packet(VOICE_TYPE_AUDIO, 0, "ABCDE", byteArrayOf(1)))
        assertNull(z.recv())
        a.close(); z.close()
    }

    @Test
    fun `sala que nao existe no relay tcp e ignorada`() {
        val a = peer(); val b = peer()
        a.sendTo(packet(VOICE_TYPE_PRESENCE, 0, "NOPE1"))
        b.sendTo(packet(VOICE_TYPE_PRESENCE, 1, "NOPE1"))
        Thread.sleep(50)
        a.sendTo(packet(VOICE_TYPE_AUDIO, 0, "NOPE1", byteArrayOf(1)))
        assertNull(b.recv())
        assertEquals(0, relay.roomCount)
        a.close(); b.close()
    }
}
