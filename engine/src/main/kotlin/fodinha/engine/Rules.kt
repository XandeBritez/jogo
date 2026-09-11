package fodinha.engine

/**
 * Regras puras de comparacao de cartas. Sem estado, sem Android.
 */
object Rules {

    /** Manilha da rodada = rank seguinte ao da carta virada (3 -> 4). */
    fun manilhaRank(turned: Card): Rank = turned.rank.next()

    fun isManilha(card: Card, manilha: Rank): Boolean = card.rank == manilha

    /**
     * Forca absoluta de uma carta na rodada. Maior = mais forte.
     *
     * Cartas normais: 0..9 (apenas o rank; naipe NAO entra).
     * Manilhas: 100 + ordinal do naipe -> qualquer manilha bate qualquer carta normal,
     * e entre manilhas vale paus > copas > espadas > ouros.
     */
    fun strength(card: Card, manilha: Rank): Int =
        if (card.rank == manilha) 100 + card.suit.ordinal else card.rank.ordinal

    /**
     * Vencedor de uma vaza. `plays` na ordem em que foram jogadas.
     * Empate resolve pela PRIMEIRA carta jogada (por isso `>` e nao `>=`).
     * Retorna o indice dentro de `plays`.
     */
    fun trickWinnerIndex(plays: List<Card>, manilha: Rank): Int {
        require(plays.isNotEmpty()) { "vaza vazia" }
        var best = 0
        var bestStrength = strength(plays[0], manilha)
        for (i in 1 until plays.size) {
            val s = strength(plays[i], manilha)
            if (s > bestStrength) {
                best = i
                bestStrength = s
            }
        }
        return best
    }

    /** Cartas por rodada, limitado pelo baralho: 1 carta vai para a virada. */
    fun cardsThisRound(scheduled: Int, activePlayers: Int): Int {
        require(activePlayers > 0)
        return minOf(scheduled, (Deck.all.size - 1) / activePlayers)
    }

    /**
     * Progressao infinita 1..9 subindo, 8..1 descendo, repete.
     * roundIndex e 0-based.
     */
    fun scheduledCards(roundIndex: Int): Int {
        val pos = roundIndex % 17
        return if (pos < 9) pos + 1 else 17 - pos
    }
}
