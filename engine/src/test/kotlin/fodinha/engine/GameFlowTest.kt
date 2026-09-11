package fodinha.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFlowTest {

    private fun game(n: Int, seed: Long = 42L) =
        Engine.newGame((1..n).map { "P$it" to true }, seed)

    @Test
    fun `jogo novo distribui uma carta e vira a manilha`() {
        val s = game(4)
        assertEquals(1, s.cardsThisRound)
        assertNotNull(s.turned)
        assertEquals(4, s.hands.size)
        assertTrue(s.hands.values.all { it.size == 1 })
        assertTrue(s.players.all { it.lives == 10 })
        assertEquals(Phase.BIDDING, s.phase)
    }

    @Test
    fun `cartas distribuidas sao todas distintas e incluem a virada`() {
        var s = game(6)
        repeat(8) { s = playRound(s) }
        // revisita uma rodada grande
        val todas = s.hands.values.flatten() + listOfNotNull(s.turned)
        assertEquals(todas.size, todas.toSet().size)
    }

    @Test
    fun `soma das previsoes nunca pode igualar o numero de cartas`() {
        var s = game(4)
        repeat(30) {
            if (s.phase == Phase.GAME_OVER) return@repeat
            s = bidAll(s)
            assertTrue(
                "soma ${s.bids.values.sum()} igualou ${s.cardsThisRound}",
                s.bids.values.sum() != s.cardsThisRound,
            )
            s = playAllTricks(s)
            if (s.phase == Phase.ROUND_OVER) s = Engine.nextRound(s)
        }
    }

    @Test
    fun `ultimo apostador sempre tem ao menos uma opcao legal`() {
        for (cards in 1..9) {
            for (players in 2..6) {
                for (sumSoFar in 0..(cards * (players - 1))) {
                    val legais = (0..cards).toSet() - (cards - sumSoFar)
                    assertTrue("cards=$cards sum=$sumSoFar", legais.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun `previsao ilegal do ultimo jogador e rejeitada`() {
        var s = game(2)
        // 1 carta, 2 jogadores. Primeiro preve 0 -> segundo nao pode prever 1.
        val p0 = s.currentPlayerId!!
        s = Engine.reduce(s, GameAction.Bid(p0, 0))
        val legais = Engine.legalBids(s)
        assertFalse(legais.contains(1))
        assertTrue(legais.contains(0))

        val erro = runCatching { Engine.reduce(s, GameAction.Bid(s.currentPlayerId!!, 1)) }
        assertTrue(erro.isFailure)
    }

    @Test
    fun `pontuacao tira a diferenca entre previsao e vazas`() {
        var s = game(3)
        s = bidAll(s)
        val bids = s.bids.toMap()
        val antes = s.players.associate { it.id to it.lives }
        s = playAllTricks(s)
        assertEquals(Phase.ROUND_OVER, s.phase)
        for (r in s.lastRoundSummary) {
            assertEquals(kotlin.math.abs(bids[r.playerId]!! - r.won), r.livesLost)
            assertEquals(antes[r.playerId]!! - r.livesLost, r.livesAfter)
            assertEquals(r.livesAfter, s.player(r.playerId).lives)
        }
    }

    @Test
    fun `total de vazas ganhas e igual ao numero de cartas`() {
        var s = game(5)
        repeat(6) {
            s = bidAll(s)
            val cards = s.cardsThisRound
            s = playAllTricks(s)
            assertEquals(cards, s.tricksWon.values.sum())
            if (s.phase == Phase.ROUND_OVER) s = Engine.nextRound(s) else return@repeat
        }
    }

    @Test
    fun `dealer rotaciona a cada rodada`() {
        var s = game(4)
        val primeiro = s.order.first()
        s = playRound(s)
        assertTrue("ordem nao rotacionou", s.order.first() != primeiro)
    }

    @Test
    fun `seis jogadores nunca recebem mais que seis cartas`() {
        var s = game(6)
        repeat(12) {
            if (s.phase == Phase.GAME_OVER) return@repeat
            assertTrue("recebeu ${s.cardsThisRound} cartas", s.cardsThisRound <= 6)
            s = playRound(s)
        }
    }

    @Test
    fun `jogo termina com um sobrevivente e ranking completo`() {
        var s = game(4, seed = 7L)
        var guard = 0
        while (s.phase != Phase.GAME_OVER && guard++ < 500) {
            s = playRound(s)
        }
        assertEquals(Phase.GAME_OVER, s.phase)
        assertTrue(s.players.count { it.alive } <= 1)
        assertEquals(4, s.eliminationOrder.size)
        assertEquals(4, s.eliminationOrder.toSet().size)
    }

    @Test
    fun `view esconde a mao dos outros nas rodadas normais`() {
        var s = game(4)
        while (s.cardsThisRound < 3) s = playRound(s)
        val v = s.viewFor(s.order.first())
        assertTrue(v.revealedHands.isEmpty())
        assertEquals(s.cardsThisRound, v.myHand.size)
    }

    @Test
    fun `rodada cega de uma carta esconde a propria e revela as alheias`() {
        val s = game(4)
        assertEquals(1, s.cardsThisRound)
        val me = s.order.first()
        val v = s.viewFor(me)
        assertTrue("nao deveria ver a propria carta", v.myHand.isEmpty())
        assertEquals(3, v.revealedHands.size)
        assertFalse(v.revealedHands.containsKey(me))
        assertTrue(v.revealedHands.values.all { it.size == 1 })
    }

    @Test
    fun `vaza completa fica exposta ate ser recolhida`() {
        var s = game(3)
        s = bidAll(s)
        assertEquals(Phase.PLAYING, s.phase)

        // Todo mundo joga a unica carta da rodada.
        repeat(3) { s = Engine.reduce(s, Bot.decidePlay(s, s.currentPlayerId!!)) }

        // A mesa NAO foi recolhida: as tres cartas continuam la, com vencedor.
        assertEquals(Phase.TRICK_REVEAL, s.phase)
        assertEquals(3, s.currentTrick.size)
        assertNotNull(s.trickWinnerId)
        assertEquals(1, s.tricksWon.values.sum())
        // Ninguem joga durante a exibicao.
        assertTrue(Engine.legalPlays(s, s.order.first()).isEmpty())

        val vencedor = s.trickWinnerId!!
        s = Engine.closeTrick(s)

        // Recolhida: mesa limpa e, como era rodada de 1 carta, a rodada fechou.
        assertTrue(s.currentTrick.isEmpty())
        assertEquals(null, s.trickWinnerId)
        assertEquals(Phase.ROUND_OVER, s.phase)
        assertEquals(1, s.tricksWon[vencedor])
    }

    @Test
    fun `o vencedor da vaza sai na proxima`() {
        var s = game(3)
        while (s.cardsThisRound < 2) s = playRound(s)
        s = bidAll(s)
        repeat(3) { s = Engine.reduce(s, Bot.decidePlay(s, s.currentPlayerId!!)) }

        val vencedor = s.trickWinnerId!!
        s = Engine.closeTrick(s)
        assertEquals(Phase.PLAYING, s.phase)
        assertEquals("quem levou a vaza deveria sair", vencedor, s.currentPlayerId)
    }

    @Test
    fun `mesmo seed produz o mesmo jogo`() {
        val a = game(4, 99L)
        val b = game(4, 99L)
        assertEquals(a.hands, b.hands)
        assertEquals(a.turned, b.turned)
    }

    // --- helpers: usam o proprio Bot para dirigir a partida ---

    private fun bidAll(state: GameState): GameState {
        var s = state
        while (s.phase == Phase.BIDDING) {
            s = Engine.reduce(s, Bot.decideBid(s, s.currentPlayerId!!))
        }
        return s
    }

    private fun playAllTricks(state: GameState): GameState {
        var s = state
        while (s.phase == Phase.PLAYING || s.phase == Phase.TRICK_REVEAL) {
            s = if (s.phase == Phase.TRICK_REVEAL) Engine.closeTrick(s)
            else Engine.reduce(s, Bot.decidePlay(s, s.currentPlayerId!!))
        }
        return s
    }

    /** Joga uma rodada inteira e avanca para a proxima (se o jogo nao acabou). */
    private fun playRound(state: GameState): GameState {
        var s = playAllTricks(bidAll(state))
        if (s.phase == Phase.ROUND_OVER) s = Engine.nextRound(s)
        return s
    }
}
