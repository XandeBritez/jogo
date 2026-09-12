package fodinha.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta e pecas do menu, compartilhadas entre a abertura e a sala de espera:
 * as duas telas sao a mesma mesa verde com os mesmos blocos escuros.
 */

/**
 * Cores da mesa. Existe uma paleta por combinacao de tema e contraste, mas
 * escrita uma vez so: a versao de alto contraste e derivada da base, senao
 * seriam quatro tabelas para manter em sincronia.
 */
data class MenuPalette(
    val green: Color,
    val greenDeep: Color,
    val slate: Color,
    val slateBright: Color,
    val slateSoft: Color,
    val edge: Color,
    val ink: Color,
    val gold: Color,
    /** Fundo da tela de opcoes, que e escura nos dois temas. */
    val optBg: Color,
    val optDialog: Color,
    val optTitle: Color,
    val optSub: Color,
    val optAccent: Color,
    val highContrast: Boolean,
)

private val PaletaClara = MenuPalette(
    green = Color(0xFF2E9E4F),
    greenDeep = Color(0xFF1F8A41),
    slate = Color(0xFF1D3B29),
    slateBright = Color(0xFF2A5C3C),
    slateSoft = Color(0x33124C2B),
    edge = Color(0xFF7FC8A9),
    ink = Color(0xFFEFF6F0),
    gold = Color(0xFFD9A441),
    optBg = Color(0xFF2B2B2B),
    optDialog = Color(0xFF4B4B4B),
    optTitle = Color(0xFFE8E8E8),
    optSub = Color(0xFFA6A6A6),
    optAccent = Color(0xFF80CBC4),
    highContrast = false,
)

/** Mesa de noite: o feltro fecha, mas continua feltro verde. */
private val PaletaEscura = PaletaClara.copy(
    green = Color(0xFF13492F),
    greenDeep = Color(0xFF0A2E1D),
    slate = Color(0xFF0C1F15),
    slateBright = Color(0xFF17402B),
    slateSoft = Color(0x3307301F),
    edge = Color(0xFF3F7F5E),
    ink = Color(0xFFE6F0E8),
    optBg = Color(0xFF161616),
    optDialog = Color(0xFF2C2C2C),
)

/**
 * Alto contraste: fundo mais fechado, tinta branca, borda quase branca e
 * dourado mais aceso. Nao e um tema novo - e um reforco por cima do que
 * estiver valendo.
 */
private fun MenuPalette.altoContraste(): MenuPalette = copy(
    green = if (this === PaletaClara) Color(0xFF1C7C3A) else Color(0xFF0D3A24),
    greenDeep = if (this === PaletaClara) Color(0xFF106030) else Color(0xFF061C11),
    slate = Color(0xFF06120B),
    slateBright = Color(0xFF123324),
    slateSoft = Color(0x66000000),
    edge = Color(0xFFEAF7EE),
    ink = Color(0xFFFFFFFF),
    gold = Color(0xFFFFD24A),
    optBg = Color(0xFF000000),
    optDialog = Color(0xFF1A1A1A),
    optTitle = Color(0xFFFFFFFF),
    optSub = Color(0xFFE0E0E0),
    optAccent = Color(0xFF7FF0DF),
    highContrast = true,
)

fun menuPalette(dark: Boolean, highContrast: Boolean): MenuPalette {
    val base = if (dark) PaletaEscura else PaletaClara
    return if (highContrast) base.altoContraste() else base
}

val LocalMenuPalette = staticCompositionLocalOf { PaletaClara }

// Os nomes antigos continuam valendo, agora lendo a paleta em vigor. Sao uns
// cento e cinquenta usos espalhados pelas telas; trocar todos por
// `LocalMenuPalette.current.x` so encheria o codigo de ruido.
val MenuGreen: Color @Composable get() = LocalMenuPalette.current.green
val MenuGreenDeep: Color @Composable get() = LocalMenuPalette.current.greenDeep
val Slate: Color @Composable get() = LocalMenuPalette.current.slate
val SlateBright: Color @Composable get() = LocalMenuPalette.current.slateBright
val SlateSoft: Color @Composable get() = LocalMenuPalette.current.slateSoft
val MenuEdge: Color @Composable get() = LocalMenuPalette.current.edge
val Ink: Color @Composable get() = LocalMenuPalette.current.ink
val MenuGold: Color @Composable get() = LocalMenuPalette.current.gold

/**
 * Texto secundario. Em alto contraste a transparencia quase some: e ela que
 * derruba a legibilidade de quem precisa do reforco.
 */
@Composable
fun inkDim(alpha: Float): Color {
    val p = LocalMenuPalette.current
    return p.ink.copy(alpha = if (p.highContrast) alpha.coerceAtLeast(0.92f) else alpha)
}

/** Bloco escuro clicavel: Jogar, Internet, Ajuda, Comecar, Sair. */
@Composable
fun MenuTile(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Bloco baixo (o do cabecalho da mesa tem 32dp) nao aguenta 10dp de folga.
    contentPadding: Dp = 10.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(if (enabled) Slate else Slate.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Botao "-" / "+" do contador de bots. */
@Composable
fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(if (enabled) Slate else Slate.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .border(
                BorderStroke(1.dp, if (enabled) Color(0x55FFFFFF) else Color(0x22FFFFFF)),
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color.White else inkDim(0.35f),
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
        )
    }
}

/**
 * Campo de texto dentro de um bloco escuro.
 *
 * Sem isto o campo herda o colorScheme e, no tema claro, escreve com a tinta
 * clara do tema em cima do fundo escuro do dialogo - ou pior, texto claro em
 * container claro.
 */
@Composable
fun menuTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = MenuGold,
    focusedBorderColor = MenuGold,
    unfocusedBorderColor = MenuEdge,
    focusedLabelColor = MenuGold,
    unfocusedLabelColor = inkDim(0.75f),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
)
