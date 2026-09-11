package fodinha.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fodinha.engine.Suit

private val Red = Color(0xFFC62828)
private val Black = Color(0xFF1B1B1B)

fun Suit.color(): Color = when (this) {
    Suit.HEARTS, Suit.DIAMONDS -> Red
    Suit.CLUBS, Suit.SPADES -> Black
}

/** Carta de frente. `manilha` desenha a borda dourada. */
@Composable
fun PlayingCard(
    card: fodinha.engine.Card,
    modifier: Modifier = Modifier,
    isManilha: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val w = if (small) 42.dp else 62.dp
    val h = if (small) 60.dp else 88.dp
    val lift by animateFloatAsState(if (enabled && onClick != null) 1f else 0.75f, label = "lift")

    Card(
        modifier = modifier
            .width(w)
            .height(h)
            .alpha(lift)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF4)),
        border = if (isManilha) BorderStroke(2.5.dp, Color(0xFFD9A441)) else BorderStroke(1.dp, Color(0x33000000)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isManilha) 8.dp else 3.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = card.rank.label,
                color = card.suit.color(),
                fontWeight = FontWeight.Bold,
                fontSize = if (small) 14.sp else 20.sp,
            )
            Text(
                text = card.suit.symbol,
                color = card.suit.color(),
                fontSize = if (small) 16.sp else 24.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

/**
 * A manilha da rodada em formato de carta.
 *
 * Nao e uma carta so: as quatro daquele valor sao manilha. Por isso a carta
 * mostra o valor grande e os quatro naipes embaixo, na ordem de forca
 * (paus > copas > espadas > ouros), em vez de fingir um naipe unico.
 */
@Composable
fun ManilhaCard(rankLabel: String, modifier: Modifier = Modifier, small: Boolean = true) {
    // Um tico mais larga que a carta comum: leva quatro naipes no rodape.
    val w = if (small) 48.dp else 68.dp
    val h = if (small) 60.dp else 88.dp

    Card(
        modifier = modifier.width(w).height(h),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFBF4)),
        border = BorderStroke(2.5.dp, Color(0xFFD9A441)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 1.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = rankLabel,
                color = Black,
                fontWeight = FontWeight.Black,
                fontSize = if (small) 22.sp else 30.sp,
                maxLines = 1,
            )
            // Os quatro naipes tem que caber: sem folga aqui, ouros era cortado.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                // Ordem de forca entre manilhas, da mais forte para a mais fraca.
                listOf(Suit.CLUBS, Suit.HEARTS, Suit.SPADES, Suit.DIAMONDS).forEach { s ->
                    Text(
                        s.symbol,
                        color = s.color(),
                        fontSize = if (small) 8.sp else 11.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Verso da carta. Usado para mao alheia e para a propria mao na rodada cega.
 * Clicavel quando e a vez do jogador mandar a carta que ele nao ve.
 */
@Composable
fun CardBack(
    modifier: Modifier = Modifier,
    small: Boolean = false,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val w = if (small) 42.dp else 62.dp
    val h = if (small) 60.dp else 88.dp
    Box(
        modifier = modifier
            .width(w)
            .height(h)
            .background(
                brush = Brush.linearGradient(listOf(Color(0xFF243B6B), Color(0xFF122043))),
                shape = RoundedCornerShape(8.dp),
            )
            .then(
                if (highlighted) Modifier.border(2.5.dp, Color(0xFFD9A441), RoundedCornerShape(8.dp))
                else Modifier
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (small) 18.dp else 26.dp)
                .background(Color(0x33D9A441), RoundedCornerShape(4.dp))
        )
    }
}

/** Espaco vazio do tamanho de uma carta, para a mesa nao "pular". */
@Composable
fun CardSlot(modifier: Modifier = Modifier, small: Boolean = false) {
    Box(
        modifier = modifier
            .width(if (small) 42.dp else 62.dp)
            .height(if (small) 60.dp else 88.dp)
            .background(Color(0x14FFFFFF), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("—", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
    }
}
