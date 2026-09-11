package fodinha.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private fun c(rank: String, suit: Suit) = Card(Rank.fromLabel(rank), suit)

    @Test
    fun `baralho tem 40 cartas sem 8 9 e 10`() {
        assertEquals(40, Deck.all.size)
        assertEquals(40, Deck.all.toSet().size)
        assertTrue(Deck.all.none { it.rank.label in setOf("8", "9", "10") })
    }

    @Test
    fun `hierarquia 3 maior que 2 maior que A maior que K J Q 7 6 5 4`() {
        // Todo rank aparece na lista, entao qualquer manilha bagunçaria a ordem de proposito.
        // A hierarquia base e o proprio ordinal do enum.
        val ordem = listOf("3", "2", "A", "K", "J", "Q", "7", "6", "5", "4")
        val ordinais = ordem.map { Rank.fromLabel(it).ordinal }
        assertEquals(ordinais.sortedDescending(), ordinais)
        assertEquals(Rank.entries.size, ordem.size)

        // E a forca, sem nenhuma manilha em jogo, respeita essa mesma ordem.
        val semManilha = Rank.THREE // virada = 2, manilha = 3; testamos o resto
        val resto = ordem.drop(1).map { Rules.strength(c(it, Suit.SPADES), semManilha) }
        assertEquals(resto.sortedDescending(), resto)
    }

    @Test
    fun `manilha e a carta seguinte a virada`() {
        assertEquals(Rank.FIVE, Rules.manilhaRank(c("4", Suit.CLUBS)))
        assertEquals(Rank.QUEEN, Rules.manilhaRank(c("7", Suit.HEARTS)))
        assertEquals(Rank.THREE, Rules.manilhaRank(c("2", Suit.DIAMONDS)))
    }

    @Test
    fun `virada 3 faz do 4 a manilha`() {
        assertEquals(Rank.FOUR, Rules.manilhaRank(c("3", Suit.SPADES)))
        val manilha = Rank.FOUR
        // 4 de paus vira a carta mais forte do jogo, acima do 3.
        assertTrue(
            Rules.strength(c("4", Suit.CLUBS), manilha) > Rules.strength(c("3", Suit.CLUBS), manilha)
        )
    }

    @Test
    fun `qualquer manilha bate qualquer carta normal`() {
        val manilha = Rank.SEVEN
        val piorManilha = c("7", Suit.DIAMONDS)
        val melhorNormal = c("3", Suit.CLUBS)
        assertTrue(Rules.strength(piorManilha, manilha) > Rules.strength(melhorNormal, manilha))
    }

    @Test
    fun `ordem de naipe vale so entre manilhas`() {
        val manilha = Rank.KING
        val paus = Rules.strength(c("K", Suit.CLUBS), manilha)
        val copas = Rules.strength(c("K", Suit.HEARTS), manilha)
        val espadas = Rules.strength(c("K", Suit.SPADES), manilha)
        val ouros = Rules.strength(c("K", Suit.DIAMONDS), manilha)
        assertTrue(paus > copas && copas > espadas && espadas > ouros)

        // Cartas normais de mesmo rank tem forca IDENTICA, naipe nao desempata.
        assertEquals(
            Rules.strength(c("A", Suit.CLUBS), manilha),
            Rules.strength(c("A", Suit.DIAMONDS), manilha),
        )
    }

    @Test
    fun `empate e vencido pela primeira carta jogada`() {
        val manilha = Rank.FOUR
        val plays = listOf(c("A", Suit.DIAMONDS), c("A", Suit.CLUBS), c("K", Suit.CLUBS))
        assertEquals(0, Rules.trickWinnerIndex(plays, manilha))
    }

    @Test
    fun `vencedor da vaza e a maior carta`() {
        val manilha = Rank.SIX
        val plays = listOf(c("A", Suit.CLUBS), c("3", Suit.DIAMONDS), c("6", Suit.DIAMONDS))
        // 6 de ouros e manilha, ganha mesmo sendo o naipe mais fraco.
        assertEquals(2, Rules.trickWinnerIndex(plays, manilha))
    }

    @Test
    fun `progressao sobe ate 9 desce ate 1 e recomeca`() {
        val esperado = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 8, 7, 6, 5, 4, 3, 2, 1)
        assertEquals(esperado, (0..16).map { Rules.scheduledCards(it) })
        assertEquals(1, Rules.scheduledCards(17))
        assertEquals(2, Rules.scheduledCards(18))
    }

    @Test
    fun `cartas por rodada limitadas pelo baralho`() {
        assertEquals(9, Rules.cardsThisRound(9, 4))  // 4 jogadores: 36 + virada = 37, cabe
        assertEquals(7, Rules.cardsThisRound(9, 5))  // 39/5 = 7
        assertEquals(6, Rules.cardsThisRound(9, 6))  // 39/6 = 6
        assertEquals(3, Rules.cardsThisRound(3, 6))  // agendado menor que o teto
    }
}
