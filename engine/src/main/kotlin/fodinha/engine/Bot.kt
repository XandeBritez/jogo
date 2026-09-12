package fodinha.engine

import kotlin.math.roundToInt

/**
 * Heuristica do bot. Roda dentro do host como um jogador qualquer:
 * a Engine nao sabe distinguir bot de humano.
 *
 * Recebe o GameState completo porque roda no host, mas so consulta
 * informacao que aquele jogador teria direito de ver.
 */
object Bot {

    fun decideBid(state: GameState, playerId: Int): GameAction.Bid {
        val legal = Engine.legalBids(state)
        require(legal.isNotEmpty()) { "bot sem previsao legal" }
        val manilha = state.manilha ?: return GameAction.Bid(playerId, legal.first())

        val estimate = if (state.isBlindOneCard) {
            estimateBlindOneCard(state, playerId, manilha)
        } else if (state.isBlindNineCards) {
            // Aposta as cegas: chuta a media estatistica (cartas / jogadores).
            (state.cardsThisRound.toDouble() / state.order.size).roundToInt()
        } else {
            estimateFromHand(state.hands[playerId].orEmpty(), manilha, state.order.size)
        }

        return GameAction.Bid(playerId, nearestLegal(estimate, legal))
    }

    /** Soma a probabilidade de cada carta ganhar uma vaza. */
    private fun estimateFromHand(hand: List<Card>, manilha: Rank, players: Int): Int {
        val expected = hand.sumOf { card -> winChance(card, manilha, players) }
        return expected.roundToInt()
    }

    /**
     * Rodada cega de 1 carta: nao vejo a minha, vejo as dos outros.
     * Se todo mundo que eu vejo esta fraco, vale apostar 1.
     */
    private fun estimateBlindOneCard(state: GameState, playerId: Int, manilha: Rank): Int {
        val visible = state.hands.filterKeys { it != playerId }.values.flatten()
        if (visible.isEmpty()) return 0
        val bestVisible = visible.maxOf { Rules.strength(it, manilha) }
        // Forca media de uma carta aleatoria do baralho ~= 4.5 (sem manilha).
        return if (bestVisible < 6) 1 else 0
    }

    private fun winChance(card: Card, manilha: Rank, players: Int): Double {
        val s = Rules.strength(card, manilha)
        val base = when {
            s >= 100 + Suit.CLUBS.ordinal -> 1.0                       // zap
            s >= 100 -> 0.85                                           // demais manilhas
            card.rank == Rank.THREE -> 0.62
            card.rank == Rank.TWO -> 0.50
            card.rank == Rank.ACE -> 0.40
            card.rank == Rank.KING -> 0.28
            card.rank == Rank.JACK -> 0.20
            card.rank == Rank.QUEEN -> 0.14
            else -> 0.06
        }
        // Quanto mais jogadores, mais dificil segurar uma vaza com carta media.
        val crowdPenalty = if (s >= 100) 1.0 else (2.0 / players).coerceAtMost(1.0)
        return base * crowdPenalty
    }

    fun decidePlay(state: GameState, playerId: Int): GameAction.PlayCard {
        val hand = Engine.legalPlays(state, playerId)
        require(hand.isNotEmpty()) { "bot sem carta para jogar" }
        val manilha = state.manilha!!

        // Na rodada cega de 1 carta so existe uma opcao.
        if (hand.size == 1) return GameAction.PlayCard(playerId, hand.first())

        // Rodada de 9: cega para todo mundo. O bot enxerga a propria mao porque
        // roda dentro do host, mas escolher por forca seria trapaca contra o
        // humano, que esta mandando carta no escuro.
        if (state.isBlindNineCards) return GameAction.PlayCard(playerId, hand.random())

        val bid = state.bids[playerId] ?: 0
        val won = state.tricksWon[playerId] ?: 0
        val stillNeeds = bid - won

        val strongest = hand.maxByOrNull { Rules.strength(it, manilha) }!!
        val weakest = hand.minByOrNull { Rules.strength(it, manilha) }!!

        if (stillNeeds <= 0) {
            // Ja tenho o que precisava: descarto a menor que nao ganhe a vaza, se der.
            val losing = hand.filter { !wouldWin(state, it, manilha) }
            return GameAction.PlayCard(playerId, losing.minByOrNull { Rules.strength(it, manilha) } ?: weakest)
        }

        // Preciso de vaza: ganho o mais barato possivel.
        val winners = hand.filter { wouldWin(state, it, manilha) }
        val cheapestWinner = winners.minByOrNull { Rules.strength(it, manilha) }
        return GameAction.PlayCard(playerId, cheapestWinner ?: if (state.currentTrick.isEmpty()) strongest else weakest)
    }

    /** Esta carta lidera a vaza atual? Ignora quem ainda vai jogar depois. */
    private fun wouldWin(state: GameState, card: Card, manilha: Rank): Boolean {
        val onTable = state.currentTrick.map { it.card }
        if (onTable.isEmpty()) return Rules.strength(card, manilha) >= 100 || card.rank >= Rank.TWO
        val best = onTable.maxOf { Rules.strength(it, manilha) }
        return Rules.strength(card, manilha) > best
    }

    private fun nearestLegal(target: Int, legal: Set<Int>): Int =
        legal.minByOrNull { kotlin.math.abs(it - target) * 2 + if (it > target) 1 else 0 }!!
}
