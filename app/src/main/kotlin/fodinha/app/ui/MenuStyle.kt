package fodinha.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta e pecas do menu, compartilhadas entre a abertura e a sala de espera:
 * as duas telas sao a mesma mesa verde com os mesmos blocos escuros.
 */

/** Verde da mesa do menu. Mais claro que o feltro da partida, como no print. */
val MenuGreen = Color(0xFF2E9E4F)
val MenuGreenDeep = Color(0xFF1F8A41)
val Slate = Color(0xFF1D3B29)
val SlateSoft = Color(0x33124C2B)
val MenuEdge = Color(0xFF7FC8A9)
val Ink = Color(0xFFEFF6F0)
val MenuGold = Color(0xFFD9A441)

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
            color = if (enabled) Color.White else Ink.copy(alpha = 0.35f),
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
        )
    }
}
