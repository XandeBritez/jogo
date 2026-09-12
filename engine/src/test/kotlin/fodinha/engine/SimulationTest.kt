package fodinha.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simula partidas inteiras com varias sementes e quantidades de jogadores.
 * Substitui o teste manual no aparelho: qualquer invariante quebrada aparece aqui.
 */
class SimulationTest {

    @Test
    fun `mil partidas terminam com invariantes intactas`() {
        var games = 0
        for (players in 2..6) {
            for (seed in 1L..200L) {
                simulateOneGame(players, seed)
                games++
            }
        }
        assertEquals(1000, games)
    }

    private fun simulateOneGame(playerCount: Int, seed: Long) {
        var s = Engine.newGame((1..playerCount).map { "P$it" to true }, seed)
        var guard = 0
        var previousTotalLives = s.players.sumOf { it.lives }

        while (s.phase != Phase.GAME_OVER) {
            if (guard++ > 2000) throw AssertionError("jogo nao termina: $playerCount jogadores, seed $seed")

            val vivos = s.players.count { it.alive }
            val cartas = s.cardsThisRound

            // Baralho nao estoura: maos + virada cabem em 40.
            assertTrue(
                "estourou o baralho: $vivos x $cartas + 1",
                vivos * cartas + 1 <= Deck.all.size,
            )
            assertTrue("rodada com 0 cartas", cartas >= 1)
            assertEquals("ordem nao bate com os vivos", vivos, s.order.size)

            // Nenhuma carta duplicada em jogo.
            val emJogo = s.hands.values.flatten() + listOfNotNull(s.turned)
            assertEquals("carta duplicada", emJogo.size, emJogo.toSet().size)

            // Previsoes.
            while (s.phase == Phase.BIDDING) {
                val pid = s.currentPlayerId!!
                val legais = Engine.legalBids(s)
                assertTrue("sem previsao legal", legais.isNotEmpty())
                assertTrue("previsao fora da faixa", legais.all { it in 0..cartas })
                s = Engine.reduce(s, Bot.decideBid(s, pid))
            }
            assertTrue(
                "soma das previsoes igualou as cartas",
                s.bids.values.sum() != cartas,
            )
            assertEquals("faltou alguem prever", vivos, s.bids.size)

            // Jogadas. A vaza fechada fica exposta ate ser recolhida.
            while (s.phase == Phase.PLAYING || s.phase == Phase.TRICK_REVEAL) {
                if (s.phase == Phase.TRICK_REVEAL) {
                    assertEquals("vaza exposta incompleta", vivos, s.currentTrick.size)
                    assertTrue("vaza exposta sem vencedor", s.trickWinnerId != null)
                    s = Engine.closeTrick(s)
                    continue
                }
                val pid = s.currentPlayerId!!
                assertTrue("jogador da vez sem carta", s.hands[pid]!!.isNotEmpty())
                s = Engine.reduce(s, Bot.decidePlay(s, pid))
            }

            // Vazas distribuidas batem com as cartas da rodada.
            assertEquals("vazas nao batem", cartas, s.tricksWon.values.sum())

            // Vidas so caem, e a perda de cada um e |previsao - vazas|.
            for (r in s.lastRoundSummary) {
                assertEquals(kotlin.math.abs(r.bid - r.won), r.livesLost)
                assertTrue("vida negativa", r.livesAfter >= 0)
            }
            val totalNow = s.players.sumOf { it.lives }
            assertTrue("vidas aumentaram", totalNow <= previousTotalLives)
            previousTotalLives = totalNow

            if (s.phase == Phase.ROUND_OVER) s = Engine.nextRound(s)
        }

        // Fim: no maximo um sobrevivente e ranking com todo mundo, sem repetir.
        assertTrue(s.players.count { it.alive } <= 1)
        assertEquals(playerCount, s.eliminationOrder.size)
        assertEquals(playerCount, s.eliminationOrder.toSet().size)
    }

    @Test
    fun `view nunca vaza a mao dos outros fora da rodada cega`() {
        var s = Engine.newGame((1..4).map { "P$it" to true }, 123L)
        var guard = 0
        while (s.phase != Phase.GAME_OVER && guard++ < 60) {
            for (id in s.order) {
                val v = s.viewFor(id)
                if (s.cardsThisRound == 1) {
                    // Rodada cega: vejo os outros, nunca a minha.
                    assertTrue(v.myHand.isEmpty())
                    assertTrue(v.revealedHands.keys.none { it == id })
                } else {
                    assertTrue("vazou mao alheia", v.revealedHands.isEmpty())
                    val esperado = if (v.blindNineCards) emptyList() else s.hands[id].orEmpty()
                    assertEquals(esperado, v.myHand)
                }
            }
            s = playRound(s)
        }
    }

    private fun playRound(state: GameState): GameState {
        var s = state
        while (s.phase == Phase.BIDDING) s = Engine.reduce(s, Bot.decideBid(s, s.currentPlayerId!!))
        while (s.phase == Phase.PLAYING || s.phase == Phase.TRICK_REVEAL) {
            s = if (s.phase == Phase.TRICK_REVEAL) Engine.closeTrick(s)
            else Engine.reduce(s, Bot.decidePlay(s, s.currentPlayerId!!))
        }
        if (s.phase == Phase.ROUND_OVER) s = Engine.nextRound(s)
        return s
    }
}
