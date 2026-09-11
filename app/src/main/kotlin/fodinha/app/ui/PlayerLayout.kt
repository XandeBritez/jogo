package fodinha.app.ui

/** Nunca mais que isto lado a lado: abaixo disso a celula fica ilegivel. */
const val MAX_SLOTS_POR_LINHA = 5

/**
 * Quantos jogadores cabem por linha. As linhas sao equilibradas de proposito:
 * 6 jogadores viram 3+3, e nao 5+1, senao a segunda linha ficaria com uma
 * celula solta ocupando um quinto da tela.
 */
fun slotsPorLinha(total: Int): Int {
    if (total <= 0) return 1
    val linhas = (total + MAX_SLOTS_POR_LINHA - 1) / MAX_SLOTS_POR_LINHA
    return (total + linhas - 1) / linhas
}
