package fodinha.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Felt = Color(0xFF0B5D3B)
private val FeltDark = Color(0xFF06301F)
private val Gold = Color(0xFFD9A441)
private val Cream = Color(0xFFF5EFE0)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF231A05),
    secondary = Color(0xFF7FC8A9),
    background = FeltDark,
    onBackground = Cream,
    surface = Felt,
    onSurface = Cream,
    surfaceVariant = Color(0xFF0E4A32),
    onSurfaceVariant = Color(0xFFDCE8DF),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6412),
    onPrimary = Color.White,
    secondary = Color(0xFF2E7D5B),
    background = Color(0xFF0E7A50),
    onBackground = Cream,
    surface = Felt,
    onSurface = Cream,
    surfaceVariant = Color(0xFF116347),
    onSurfaceVariant = Cream,
)

/**
 * Mesa de feltro nos dois temas: o jogo e o mesmo de dia ou de noite, so muda
 * o quanto o feltro fecha. Quem decide claro ou escuro e a opcao do jogador,
 * nao o Android direto - por isso `dark` chega de fora.
 */
@Composable
fun FodinhaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
