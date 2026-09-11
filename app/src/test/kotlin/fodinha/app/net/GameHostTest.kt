package fodinha.app.net

import fodinha.engine.GameAction
import fodinha.engine.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Exercita o host com um transporte de mentira, sem socket nem aparelho.
 * E aqui que se pega o caminho remoto: assento, Welcome, lobby e jogada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameHostTest {

    /**
     * Transporte remoto em memoria. Copia a ordem real do WifiHostTransport:
     * primeiro o assento e reservado, depois o canal de saida e registrado,
     * e so entao o Hello entra pelo inbound.
     */
    private class FakeHostTransport(private val scope: CoroutineScope) : HostTransport {
        private val _inbound = MutableSharedFlow<Pair<Int, ClientMsg>>(extraBufferCapacity = 64)
        override val inbound: Flow<Pair<Int, ClientMsg>> = _inbound.asSharedFlow()

        val outbox = ConcurrentHashMap<Int, MutableList<HostMsg>>()
        private var joiner: (suspend (String) -> Int)? = null

        override suspend fun start(onJoin: suspend (String) -> Int, onLeave: suspend (Int) -> Unit) {
            joiner = onJoin
        }

        override suspend fun sendTo(playerId: Int, msg: HostMsg) {
            outbox.getOrPut(playerId) { mutableListOf() }.add(msg)
        }

        /** Simula um cliente conectando e mandando o Hello. */
        suspend fun join(name: String): Int {
            val id = joiner!!(name)
            if (id >= 0) {
                outbox.getOrPut(id) { mutableListOf() }
                _inbound.emit(id to ClientMsg.Hello(name))
            }
            return id
        }

        suspend fun send(playerId: Int, msg: ClientMsg) {
            _inbound.emit(playerId to msg)
        }

        fun msgsFor(id: Int): List<HostMsg> = outbox[id].orEmpty().toList()
        override fun close() = Unit
    }

    /**
     * Toca os dois humanos da mesa ate a rodada fechar. Mesmo na rodada cega
     * de 1 carta o humano precisa prever: so a jogada e automatica.
     */
    private suspend fun TestScope.driveHumans(host: GameHost, transport: FakeHostTransport) {
        var ownerView: fodinha.engine.PlayerView? = null
        var guard = 0
        while (guard++ < 60) {
            drainOwnerView(host)?.let { ownerView = it }
            val remoteView = transport.msgsFor(1).filterIsInstance<HostMsg.View>().lastOrNull()?.view

            val acted = when {
                ownerView?.isMyTurn == true -> {
                    act(host, 0, ownerView!!)
                    true
                }
                remoteView?.isMyTurn == true -> {
                    act(host, 1, remoteView)
                    true
                }
                else -> false
            }
            if (!acted) return
            advanceUntilIdle()
        }
    }

    private suspend fun act(host: GameHost, id: Int, v: fodinha.engine.PlayerView) {
        when {
            v.phase == Phase.BIDDING -> host.submit(id, GameAction.Bid(id, v.legalBids.first()))
            v.canPlayBlind -> host.submit(id, GameAction.PlayBlind(id))
            v.phase == Phase.PLAYING ->
                v.legalPlays.firstOrNull()?.let { host.submit(id, GameAction.PlayCard(id, it)) }
            else -> Unit
        }
    }

    /** A view do dono chega pelo Channel local; pega a mais recente. */
    private fun drainOwnerView(host: GameHost): fodinha.engine.PlayerView? {
        var latest: fodinha.engine.PlayerView? = null
        while (true) {
            val r = host.localInbox.tryReceive()
            val msg = r.getOrNull() ?: break
            if (msg is HostMsg.View) latest = msg.view
        }
        return latest
    }

    @Test
    fun `cliente remoto recebe Welcome e lobby ao entrar`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()

        val id = transport.join("Visitante")
        advanceUntilIdle()

        assertEquals(1, id)
        val msgs = transport.msgsFor(1)
        val welcome = msgs.filterIsInstance<HostMsg.Welcome>().firstOrNull()
        assertNotNull("cliente nunca recebeu Welcome", welcome)
        assertEquals(1, welcome!!.playerId)
        assertTrue("cliente nao e dono da sala", !welcome.isOwner)

        val lobby = msgs.filterIsInstance<HostMsg.Lobby>().lastOrNull()
        assertNotNull("cliente nunca recebeu o lobby", lobby)
        assertEquals(2, lobby!!.info.seats.size)
        assertEquals("Visitante", lobby.info.seats[1].name)

        host.close()
    }

    @Test
    fun `partida remota anda ate a vez de um humano`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()

        transport.join("Visitante")
        host.addBot("Robo")
        advanceUntilIdle()

        // O dono comeca o jogo. Rodada 1 tem 1 carta: cega, mas quem manda a
        // carta e o jogador, entao a mesa para na vez dele.
        host.startGame()
        advanceUntilIdle()

        val view = transport.msgsFor(1).filterIsInstance<HostMsg.View>().lastOrNull()
        assertNotNull("cliente nunca recebeu a mesa", view)
        assertEquals(3, view!!.view.players.size)
        assertEquals(1, view.view.cardsThisRound)
        // Rodada cega: o cliente ve a carta dos outros dois, nunca a dele.
        assertTrue(view.view.myHand.isEmpty())
        assertEquals(2, view.view.revealedHands.size)

        host.close()
    }

    @Test
    fun `view mandada ao cliente nunca contem a mao dos outros`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()

        transport.join("Visitante")
        host.addBot("Robo")
        advanceUntilIdle()
        host.startGame()
        advanceUntilIdle()

        // Toca varias rodadas para varrer os dois casos: a cega de 1 carta e as normais.
        var guard = 0
        while (guard++ < 8) {
            driveHumans(host, transport)
            host.advanceRound()
            advanceUntilIdle()
        }

        // Varre TUDO que o cliente recebeu, nao so o ultimo estado.
        val vistas = transport.msgsFor(1).filterIsInstance<HostMsg.View>().map { it.view }
        assertTrue("cliente recebeu poucas views", vistas.size > 5)

        for (v in vistas) {
            if (v.cardsThisRound > 1) {
                assertTrue(
                    "vazou mao alheia numa rodada de ${v.cardsThisRound} cartas",
                    v.revealedHands.isEmpty(),
                )
            } else {
                // Na cega, o que se ve e a mao dos outros, nunca a propria.
                assertTrue("cliente viu a propria carta na rodada cega", v.myHand.isEmpty())
                assertTrue(v.revealedHands.keys.none { it == v.me })
            }
        }

        // E em alguma rodada normal ele recebeu a mao dele inteira.
        assertTrue(
            "cliente nunca recebeu a propria mao",
            vistas.any { it.cardsThisRound > 1 && it.myHand.size == it.cardsThisRound },
        )

        host.close()
    }

    @Test
    fun `so o dono da sala avanca a rodada`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()

        transport.join("Visitante")
        host.addBot("Robo")
        advanceUntilIdle()
        host.startGame()
        advanceUntilIdle()

        val antes = transport.msgsFor(1).filterIsInstance<HostMsg.View>().last().view
        if (antes.phase == Phase.ROUND_OVER) {
            transport.send(1, ClientMsg.NextRound)
            advanceUntilIdle()
            val depois = transport.msgsFor(1).filterIsInstance<HostMsg.View>().last().view
            assertEquals("cliente comum avancou a rodada", antes.roundIndex, depois.roundIndex)
        }

        host.close()
    }

    @Test
    fun `jogada invalida volta como erro so para quem errou`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()

        transport.join("Visitante")
        advanceUntilIdle()
        host.startGame()
        advanceUntilIdle()

        // Previsao fora de hora / fora da vez: tem que virar Error, nao crash.
        transport.send(1, ClientMsg.Play(GameAction.Bid(1, 99)))
        advanceUntilIdle()

        val erros = transport.msgsFor(1).filterIsInstance<HostMsg.Error>()
        assertTrue("host deveria devolver erro ao cliente", erros.isNotEmpty())

        host.close()
    }

    @Test
    fun `carta cega nao sai sozinha antes do tempo`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        host.addBot("Robo")
        host.startGame()
        // Tempo suficiente para os bots, longe do prazo de 60s do humano.
        advanceTimeBy(5_000)
        runCurrent()

        var v = drainOwnerView(host)!!
        assertEquals(1, v.cardsThisRound)

        // Previsao ainda e do humano: nada foi jogado por ele.
        if (v.phase == Phase.BIDDING) {
            host.submit(0, GameAction.Bid(0, v.legalBids.first()))
            advanceTimeBy(5_000)
            runCurrent()
            v = drainOwnerView(host)!!
        }

        assertEquals(Phase.PLAYING, v.phase)
        assertTrue("humano deveria estar no comando da carta cega", v.canPlayBlind)
        assertTrue(
            "a carta do humano saiu sem ele mandar",
            v.currentTrick.none { it.playerId == 0 },
        )

        // Agora sim, ele manda. A vaza fecha, mas fica exposta na mesa.
        host.submit(0, GameAction.PlayBlind(0))
        advanceTimeBy(1_000)
        runCurrent()
        val exposta = drainOwnerView(host)!!
        assertEquals("mesa deveria ficar exposta", Phase.TRICK_REVEAL, exposta.phase)
        assertEquals("as duas cartas deveriam estar na mesa", 2, exposta.currentTrick.size)
        assertNotNull("vaza exposta sem vencedor", exposta.trickWinnerId)

        // Passados os 5s, a mesa e recolhida e a rodada fecha.
        advanceTimeBy(6_000)
        runCurrent()
        val depois = drainOwnerView(host)!!
        assertEquals(Phase.ROUND_OVER, depois.phase)
        assertTrue("mesa nao foi recolhida", depois.currentTrick.isEmpty())

        host.close()
    }

    @Test
    fun `estourado o prazo o host joga pelo jogador`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        host.addBot("Robo")
        host.startGame()
        advanceTimeBy(5_000)
        runCurrent()

        val antes = drainOwnerView(host)!!
        assertEquals(Phase.BIDDING, antes.phase)
        assertNotNull("view deveria trazer o relogio", antes.turnSecondsLeft)
        assertTrue("prazo de previsao deveria ser 60s", antes.turnSecondsLeft!! in 55..60)

        // Passa dos 60s da previsao e dos 40s da jogada sem ninguem tocar em nada.
        advanceTimeBy(61_000)
        runCurrent()
        advanceTimeBy(41_000)
        runCurrent()
        // Mais a pausa de 5s com as cartas na mesa.
        advanceTimeBy(6_000)
        runCurrent()

        val depois = drainOwnerView(host)!!
        assertEquals("host deveria ter previsto e jogado sozinho", Phase.ROUND_OVER, depois.phase)
        // A vaza unica foi para alguem: a rodada fechou de verdade.
        assertEquals(1, depois.lastRoundSummary.sumOf { it.won })

        host.close()
    }

    @Test
    fun `codificacao ida e volta preserva a mensagem`() = runTest {
        val msg: ClientMsg = ClientMsg.Play(GameAction.Bid(2, 1))
        val back = decodeClient(msg.encode())
        assertEquals(msg, back)

        val lobby: HostMsg = HostMsg.Lobby(
            LobbyInfo("Mesa", listOf(LobbySeat(0, "Dono", false, true)), started = false)
        )
        assertEquals(lobby, decodeHost(lobby.encode()))
    }
}
