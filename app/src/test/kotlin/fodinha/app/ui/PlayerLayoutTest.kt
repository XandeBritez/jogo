package fodinha.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLayoutTest {

    @Test
    fun `ate cinco jogadores cabem numa linha so`() {
        for (n in 1..5) {
            assertEquals("$n jogadores deveriam caber numa linha", n, slotsPorLinha(n))
        }
    }

    @Test
    fun `seis jogadores viram duas linhas de tres`() {
        assertEquals(3, slotsPorLinha(6))
    }

    @Test
    fun `nunca passa de cinco por linha`() {
        for (n in 1..12) {
            assertTrue("$n estourou o limite", slotsPorLinha(n) <= MAX_SLOTS_POR_LINHA)
        }
    }

    @Test
    fun `as linhas ficam equilibradas`() {
        // Diferenca maxima de um slot entre a linha mais cheia e a mais vazia.
        for (n in 1..12) {
            val porLinha = slotsPorLinha(n)
            val linhas = (0 until n).chunked(porLinha)
            val maior = linhas.maxOf { it.size }
            val menor = linhas.minOf { it.size }
            assertTrue("$n desequilibrou: $maior vs $menor", maior - menor <= 1)
        }
    }

    @Test
    fun `todo mundo aparece`() {
        for (n in 1..12) {
            val linhas = (0 until n).chunked(slotsPorLinha(n))
            assertEquals(n, linhas.sumOf { it.size })
        }
    }
}
