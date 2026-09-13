package fodinha.relay

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

class RelayServerTest {
    private lateinit var server: RelayServer

    private class Peer(port: Int) {
        val socket = Socket("127.0.0.1", port).apply { soTimeout = 3_000 }
        val reader: BufferedReader = socket.getInputStream().bufferedReader()
        val writer: BufferedWriter = socket.getOutputStream().bufferedWriter()
        fun send(line: String) { writer.write(line); writer.write("\n"); writer.flush() }
        fun read(): String? = reader.readLine()
        fun close() = socket.close()
    }

    @Before fun up() { server = RelayServer(0).also { it.start() } }
    @After fun down() { server.close() }

    private fun openRoom(): Pair<Peer, String> {
        val host = Peer(server.port)
        host.send("HOST")
        val line = host.read()!!
        assertTrue(line, line.startsWith("CODE "))
        return host to line.removePrefix("CODE ")
    }

    @Test
    fun `host abre sala e cliente entra com o codigo`() {
        val (host, code) = openRoom()
        assertEquals(5, code.length)

        val client = Peer(server.port)
        client.send("JOIN $code")
        assertEquals("OK", client.read())

        client.send("""{"t":"Hello","name":"Ana"}""")
        val from = host.read()!!
        assertTrue(from, from.startsWith("FROM "))
        val conn = from.split(" ")[1].toInt()
        assertEquals("""{"t":"Hello","name":"Ana"}""", from.substringAfter("$conn "))

        host.send("""TO $conn {"t":"Welcome","playerId":1}""")
        assertEquals("""{"t":"Welcome","playerId":1}""", client.read())

        client.close()
        assertEquals("LEFT $conn", host.read())
        host.close()
    }

    @Test
    fun `codigo minusculo tambem entra`() {
        val (host, code) = openRoom()
        val client = Peer(server.port)
        client.send("JOIN ${code.lowercase()}")
        assertEquals("OK", client.read())
        client.close(); host.close()
    }

    @Test
    fun `sala inexistente e recusada`() {
        val client = Peer(server.port)
        client.send("JOIN ZZZZZ")
        assertEquals("ERR sala nao existe", client.read())
    }

    @Test
    fun `host sair encerra os clientes`() {
        val (host, code) = openRoom()
        val client = Peer(server.port)
        client.send("JOIN $code")
        assertEquals("OK", client.read())

        host.close()
        assertEquals("END", client.read())
        // A sala some: quem tentar entrar depois nao acha.
        Thread.sleep(100)
        val late = Peer(server.port)
        late.send("JOIN $code")
        assertEquals("ERR sala nao existe", late.read())
    }

    @Test
    fun `sala cheia recusa o sexto`() {
        val (host, code) = openRoom()
        val ok = (1..MAX_CLIENTS).map { Peer(server.port).also { it.send("JOIN $code"); assertEquals("OK", it.read()) } }
        val extra = Peer(server.port)
        extra.send("JOIN $code")
        assertEquals("ERR sala cheia", extra.read())
        ok.forEach { it.close() }; host.close()
    }

    @Test
    fun `quem responde PONG fica, quem cala cai`() {
        val quick = RelayServer(0, pingMs = 100, idleTimeoutMs = 400).also { it.start() }
        try {
            val host = Peer(quick.port)
            host.send("HOST")
            val code = host.read()!!.removePrefix("CODE ")
            val client = Peer(quick.port)
            client.send("JOIN $code")
            assertEquals("OK", client.read())

            // Host responde os pings por 1 s; cliente fica mudo.
            val until = System.currentTimeMillis() + 1_000
            while (System.currentTimeMillis() < until) {
                when (val line = host.read()) {
                    "PING" -> host.send("PONG")
                    null -> throw AssertionError("host respondendo PONG caiu")
                    else -> if (line.startsWith("LEFT ")) break
                }
            }
            // Cliente mudo foi derrubado pelo prazo, e a sala continua de pe.
            val late = Peer(quick.port)
            late.send("JOIN $code")
            assertEquals("OK", late.read())
            host.close()
        } finally {
            quick.close()
        }
    }

    @Test
    fun `host expulsa cliente`() {
        val (host, code) = openRoom()
        val client = Peer(server.port)
        client.send("JOIN $code")
        assertEquals("OK", client.read())
        client.send("oi")
        val conn = host.read()!!.split(" ")[1].toInt()
        host.send("KICK $conn")
        assertEquals(null, client.read())
        assertEquals("LEFT $conn", host.read())
        host.close()
    }
}
