package fodinha.app.ui

/** Nunca mais que isto lado a lado: abaixo disso a celula fica ilegivel. */
const val MAX_SLOTS_POR_LINHA = 5

/**
 * Quantos jogadores cabem por linha. As linhas sao equilibradas de proposito:
 * 6 jogadores viram 3+3, e nao 5+1, senao a segunda linha ficaria com uma
 * celula solta ocupando um quinto da tela.
 */
fun slotsPorLinha(total: Int): Int = slotsPorLinha(total, MAX_SLOTS_POR_LINHA)

/**
 * Carta jogada ocupa muito mais que a celula de um jogador: 62dp de carta mais
 * o nome embaixo. Cinco lado a lado ja raspam a borda num aparelho estreito e
 * seis nao cabem de jeito nenhum - era por isso que a sexta carta sumia da
 * mesa numa partida cheia.
 */
const val MAX_CARTAS_POR_LINHA = 4

fun cartasPorLinha(total: Int): Int = slotsPorLinha(total, MAX_CARTAS_POR_LINHA)

private fun slotsPorLinha(total: Int, maximo: Int): Int {
    if (total <= 0) return 1
    val linhas = (total + maximo - 1) / maximo
    return (total + linhas - 1) / linhas
}
