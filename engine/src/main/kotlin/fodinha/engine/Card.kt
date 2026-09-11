package fodinha.engine

import kotlinx.serialization.Serializable

/**
 * Ordem de forca crescente do baralho de 40 cartas (sem 8, 9 e 10).
 * 4 < 5 < 6 < 7 < Q < J < K < A < 2 < 3
 *
 * O ordinal do enum E a forca. Nao existe outra fonte de verdade.
 */
@Serializable
enum class Rank(val label: String) {
    FOUR("4"),
    FIVE("5"),
    SIX("6"),
    SEVEN("7"),
    QUEEN("Q"),
    JACK("J"),
    KING("K"),
    ACE("A"),
    TWO("2"),
    THREE("3");

    /** Carta seguinte no ciclo. 3 volta para 4. */
    fun next(): Rank = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromLabel(label: String): Rank = entries.first { it.label == label }
    }
}

/**
 * Ordinal crescente = forca crescente entre manilhas: ouros < espadas < copas < paus.
 * Para cartas normais o naipe e IRRELEVANTE (por isso existe a regra de empate).
 */
@Serializable
enum class Suit(val label: String, val symbol: String) {
    DIAMONDS("Ouros", "♦"),
    SPADES("Espadas", "♠"),
    HEARTS("Copas", "♥"),
    CLUBS("Paus", "♣");
}

@Serializable
data class Card(val rank: Rank, val suit: Suit) {
    val label: String get() = "${rank.label}${suit.symbol}"
    override fun toString(): String = label
}

object Deck {
    /** As 40 cartas, em ordem canonica. */
    val all: List<Card> = Suit.entries.flatMap { suit -> Rank.entries.map { rank -> Card(rank, suit) } }

    fun shuffled(seed: Long): List<Card> = all.shuffled(java.util.Random(seed))
}
