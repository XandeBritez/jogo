package fodinha.app.net

import fodinha.relay.RelayServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ponta a ponta na JVM: GameHost de verdade, transportes de relay de verdade,
 * RelayServer de verdade em 127.0.0.1. So nao tem celular.
 */
class RelayTransportTest {
    private lateinit var relay: RelayServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun up() { relay = RelayServer(0).also { it.start() } }
    @After fun down() { scope.cancel(); relay.close() }

    private suspend fun <T> soon(block: suspend () -> T): T = withTimeout(5_000) { block() }

    @Test
    fun `host abre sala pelo relay e cliente entra com o codigo`() = runBlocking {
        val host = GameHost(scope, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val ht = RelayHostTransport("127.0.0.1", relay.port)
        host.attachRemote(ht)
        val code = soon { ht.code.await() }.getOrThrow()
        assertEquals(5, code.length)

        val ct = RelayClientTransport("127.0.0.1", relay.port, code, "Ana")
        val got = Channel<HostMsg>(Channel.UNLIMITED)
        scope.launch { ct.inbound.collect { got.send(it) } }
        scope.launch { ct.connect() }

        val welcome = soon { got.receive() }
        assertTrue("esperava Welcome, veio $welcome", welcome is HostMsg.Welcome)
        assertEquals(1, (welcome as HostMsg.Welcome).playerId)
        assertEquals(0, welcome.voicePort) // sem voz pela internet

        val lobby = soon {
            var m = got.receive()
            while (m !is HostMsg.Lobby) m = got.receive()
            m
        }
        assertEquals(listOf("Dono", "Ana"), lobby.info.seats.map { it.name })

        // O dono do aparelho ve a Ana na fila local dele tambem.
        val ownerLobby = soon {
            var m = host.localInbox.receive()
            while (m !is HostMsg.Lobby || m.info.seats.size < 2) m = host.localInbox.receive()
            m
        }
        assertEquals("Ana", ownerLobby.info.seats[1].name)

        // Cliente cai: o host marca desconectado e avisa o dono.
        ct.close()
        val after = soon {
            var m = host.localInbox.receive()
            while (m !is HostMsg.Lobby || m.info.seats[1].connected) m = host.localInbox.receive()
            m
        }
        assertEquals(false, after.info.seats[1].connected)

        host.close()
    }

    private class Client(val transport: RelayClientTransport, val got: Channel<HostMsg>)

    private fun CoroutineScope.join(port: Int, code: String, name: String): Client {
        val ct = RelayClientTransport("127.0.0.1", port, code, name)
        val got = Channel<HostMsg>(Channel.UNLIMITED)
        launch { ct.inbound.collect { got.send(it) } }
        launch { ct.connect() }
        return Client(ct, got)
    }

    private suspend inline fun <reified T : HostMsg> Channel<HostMsg>.next(crossinline ok: (T) -> Boolean = { true }): T =
        soon {
            while (true) {
                val m = receive()
                if (m is T && ok(m)) return@soon m
            }
            @Suppress("UNREACHABLE_CODE") throw IllegalStateException()
        }

    @Test
    fun `dois clientes ao mesmo tempo, cada um recebe a sua view`() = runBlocking {
        val host = GameHost(scope, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val ht = RelayHostTransport("127.0.0.1", relay.port)
        host.attachRemote(ht)
        val code = soon { ht.code.await() }.getOrThrow()

        val ana = scope.join(relay.port, code, "Ana")
        assertEquals(1, ana.got.next<HostMsg.Welcome>().playerId)
        val bruno = scope.join(relay.port, code, "Bruno")
        assertEquals(2, bruno.got.next<HostMsg.Welcome>().playerId)

        host.startGame()
        // A View e redigida por assento: a mao que chega e a do proprio jogador.
        val va = ana.got.next<HostMsg.View>()
        val vb = bruno.got.next<HostMsg.View>()
        assertEquals(1, va.view.me)
        assertEquals(2, vb.view.me)

        ana.transport.close(); bruno.transport.close(); host.close()
    }

    @Test
    fun `cair e voltar com o mesmo nome recupera o assento na conexao nova`() = runBlocking {
        val host = GameHost(scope, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val ht = RelayHostTransport("127.0.0.1", relay.port)
        host.attachRemote(ht)
        val code = soon { ht.code.await() }.getOrThrow()

        val first = scope.join(relay.port, code, "Ana")
        assertEquals(1, first.got.next<HostMsg.Welcome>().playerId)
        first.transport.close()
        // Espera o host registrar a queda antes de voltar.
        soon {
            var m = host.localInbox.receive()
            while (m !is HostMsg.Lobby || m.info.seats[1].connected) m = host.localInbox.receive()
        }

        val second = scope.join(relay.port, code, "Ana")
        assertEquals(1, second.got.next<HostMsg.Welcome>().playerId)
        // E o que o host manda ao assento 1 chega na conexao NOVA.
        host.publishLobby()
        val lobby = second.got.next<HostMsg.Lobby> { it.info.seats[1].connected }
        assertEquals("Ana", lobby.info.seats[1].name)

        second.transport.close(); host.close()
    }

    @Test
    fun `codigo errado falha com mensagem do relay`() = runBlocking {
        val ct = RelayClientTransport("127.0.0.1", relay.port, "ZZZZZ", "Ana")
        val err = runCatching { soon { ct.connect() } }.exceptionOrNull()
        assertEquals("sala nao existe", err?.message)
    }

    @Test
    fun `host fechar a sala avisa o cliente`() = runBlocking {
        val host = GameHost(scope, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val ht = RelayHostTransport("127.0.0.1", relay.port)
        host.attachRemote(ht)
        val code = soon { ht.code.await() }.getOrThrow()

        val ct = RelayClientTransport("127.0.0.1", relay.port, code, "Ana")
        val got = Channel<HostMsg>(Channel.UNLIMITED)
        scope.launch { ct.inbound.collect { got.send(it) } }
        val done = Channel<Unit>(1)
        scope.launch { ct.connect(); done.send(Unit) }
        soon { got.receive() } // Welcome

        host.close()
        val end = soon {
            var m = got.receive()
            while (m !is HostMsg.Error) m = got.receive()
            m
        }
        assertEquals("O host saiu da sala", end.message)
        soon { done.receive() } // connect() terminou normalmente
    }

    @Test
    fun `relay fora do ar vira erro, nao excecao solta`() = runBlocking {
        val ht = RelayHostTransport("127.0.0.1", 1) // porta fechada
        val host = GameHost(scope, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        host.attachRemote(ht)
        val r = soon { ht.code.await() }
        assertTrue(r.isFailure)
        host.close()
    }

    @Test
    fun `endereco do relay`() {
        assertEquals("1.2.3.4" to 5555, parseRelayAddress("1.2.3.4"))
        assertEquals("meu.host" to 7000, parseRelayAddress(" meu.host:7000 "))
        assertEquals(null, parseRelayAddress(""))
        assertEquals(null, parseRelayAddress("host:abc"))
    }
}
