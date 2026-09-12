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
import org.junit.Assert.assertFalse
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
        private var leaver: (suspend (Int) -> Unit)? = null

        override suspend fun start(onJoin: suspend (String) -> Int, onLeave: suspend (Int) -> Unit) {
            joiner = onJoin
            leaver = onLeave
        }

        /** Simula o socket do cliente caindo. */
        suspend fun drop(playerId: Int) {
            leaver!!(playerId)
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

        val view = transport.msgsFor(1).filterIsInstance<HostMsg.View>().firstOrNull()
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
        // A rodada emenda sozinha depois do resumo; aqui basta deixar o tempo
        // virtual correr entre uma leva de jogadas humanas e a seguinte.
        var guard = 0
        while (guard++ < 8) {
            driveHumans(host, transport)
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
    fun `rodada avanca sozinha depois do resumo, sem ninguem pedir`() = runTest {
        val host = GameHost(
            this,
            "Mesa",
            "Dono",
            botDelayMillis = 0,
            bidSeconds = 1,
            playSeconds = 1,
            trickRevealMillis = 100,
            roundOverMillis = 5_000,
        )
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()

        transport.join("Visitante")
        host.addBot("Robo")
        advanceUntilIdle()
        host.startGame()
        advanceUntilIdle()

        // Ninguem mandou mensagem nenhuma depois do start: o relogio do turno
        // cobre os humanos e o host emenda a rodada por conta propria.
        val vistas = transport.msgsFor(1).filterIsInstance<HostMsg.View>().map { it.view }
        assertTrue("nem chegou ao fim da primeira rodada", vistas.any { it.phase == Phase.ROUND_OVER })
        assertTrue(
            "rodada nao avancou sozinha",
            vistas.any { it.roundIndex > 0 },
        )

        host.close()
    }

    // ---------- chat de voz: so o plano de controle; o audio e UDP a parte ----------

    private fun List<HostMsg>.lastLobby() = filterIsInstance<HostMsg.Lobby>().last().info

    @Test
    fun `porta de voz viaja no Welcome`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        host.voicePort = 43210
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()

        transport.join("Visitante")
        advanceUntilIdle()

        val welcome = transport.msgsFor(1).filterIsInstance<HostMsg.Welcome>().first()
        assertEquals(43210, welcome.voicePort)

        host.close()
    }

    @Test
    fun `entrar na voz, fechar o mic e sair aparece na lista de todo mundo`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()

        transport.join("Visitante")
        advanceUntilIdle()

        transport.send(1, ClientMsg.VoiceJoin)
        advanceUntilIdle()
        var seat = transport.msgsFor(1).lastLobby().seats[1]
        assertTrue("entrou na voz mas a lista nao mostra", seat.inVoice)
        assertFalse(seat.micMuted)

        transport.send(1, ClientMsg.VoiceMic(muted = true))
        advanceUntilIdle()
        seat = transport.msgsFor(1).lastLobby().seats[1]
        assertTrue(seat.inVoice)
        assertTrue("fechou o mic mas a lista nao mostra", seat.micMuted)

        transport.send(1, ClientMsg.VoiceLeave)
        advanceUntilIdle()
        seat = transport.msgsFor(1).lastLobby().seats[1]
        assertFalse(seat.inVoice)
        assertFalse("sair da voz tem que zerar o mic tambem", seat.micMuted)

        host.close()
    }

    @Test
    fun `cair do socket tira da voz, sem microfone fantasma`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()

        transport.join("Visitante")
        advanceUntilIdle()
        transport.send(1, ClientMsg.VoiceJoin)
        advanceUntilIdle()
        assertTrue(transport.msgsFor(1).lastLobby().seats[1].inVoice)

        transport.drop(1)
        advanceUntilIdle()

        // O dono continua recebendo o lobby; o assento 1 caiu e saiu da voz.
        val ownerLobby = generateSequence { host.localInbox.tryReceive().getOrNull() }
            .filterIsInstance<HostMsg.Lobby>().last().info
        val seat = ownerLobby.seats[1]
        assertFalse(seat.connected)
        assertFalse("caiu do socket mas ficou na voz", seat.inVoice)

        host.close()
    }

    @Test
    fun `dono da sala entra na voz pelo caminho local`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()
        transport.join("Visitante")
        advanceUntilIdle()

        // O dono nao passa pelo transporte: o ViewModel chama handle(0, ...) direto.
        host.handle(0, ClientMsg.VoiceJoin)
        advanceUntilIdle()
        host.handle(0, ClientMsg.VoiceMic(muted = true))
        advanceUntilIdle()

        // E o visitante, do outro lado, ve o dono na voz com o mic fechado.
        val seat = transport.msgsFor(1).lastLobby().seats[0]
        assertTrue("dono entrou na voz mas o visitante nao ve", seat.inVoice)
        assertTrue(seat.micMuted)

        host.close()
    }

    @Test
    fun `cliente cair depois do host fechar nao explode`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()
        transport.join("Visitante")
        advanceUntilIdle()

        host.close()
        // O transporte so avisa a queda depois que o socket fecha - ou seja,
        // depois do close. Isso derrubava o app com a fila local ja fechada.
        transport.drop(1)
        advanceUntilIdle()
    }

    @Test
    fun `bot nao entra na voz`() = runTest {
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0)
        host.createOwnerSeat()
        val transport = FakeHostTransport(this)
        host.attachRemote(transport)
        advanceUntilIdle()
        host.addBot("Robo")
        advanceUntilIdle()

        // Mensagem forjada em nome do bot (assento 1): tem que ser ignorada.
        transport.send(1, ClientMsg.VoiceJoin)
        advanceUntilIdle()

        val lobby = generateSequence { host.localInbox.tryReceive().getOrNull() }
            .filterIsInstance<HostMsg.Lobby>().last().info
        assertFalse(lobby.seats[1].inVoice)

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
        val host = GameHost(this, "Mesa", "Dono", botDelayMillis = 0, roundOverMillis = 60_000)
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
