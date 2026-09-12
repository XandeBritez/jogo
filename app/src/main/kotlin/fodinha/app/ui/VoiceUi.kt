package fodinha.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fodinha.app.UiState
import fodinha.app.net.LobbySeat
import fodinha.app.net.TransportKind

/** Vermelho do microfone fechado, igual nos dois temas. */
private val MicOff = Color(0xFFFF6B6B)

/** A sala tem voz? So WiFi, e so quando o host abriu o relay. */
fun UiState.hasVoice(): Boolean = kind == TransportKind.WIFI && voicePort != 0

/**
 * Barra de voz: entrar/sair e abrir/fechar o microfone. Some por completo
 * fora da sala WiFi - nao fica desabilitada, some, porque nao ha o que
 * prometer em Bluetooth ou contra bots.
 *
 * A permissao de microfone e pedida aqui, no toque em "entrar": pedir na
 * abertura do app, para um jogo de cartas, assusta.
 */
@Composable
fun VoiceBar(
    ui: UiState,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onToggleMic: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (!ui.hasVoice()) return
    val voice = ui.voice
    val context = LocalContext.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onJoin()
    }
    val join = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onJoin() else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    val h = if (compact) 40.dp else 52.dp
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!voice.connected) {
            MenuTile(
                modifier = Modifier.weight(1f).height(h),
                contentPadding = 4.dp,
                onClick = join,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MicIcon(if (compact) 16.dp else 20.dp, tint = Ink, muted = false)
                    Text("Entrar na voz", color = Ink, fontSize = if (compact) 14.sp else 16.sp, maxLines = 1)
                }
            }
        } else {
            // Microfone: dourado aberto, vermelho fechado. E o botao que mais
            // vai ser apertado, entao fica maior que o de sair.
            MenuTile(
                modifier = Modifier.weight(1.4f).height(h),
                contentPadding = 4.dp,
                onClick = onToggleMic,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MicIcon(
                        if (compact) 16.dp else 20.dp,
                        tint = if (voice.micMuted) MicOff else MenuGold,
                        muted = voice.micMuted,
                    )
                    Text(
                        if (voice.micMuted) "Mic fechado" else "Mic aberto",
                        color = if (voice.micMuted) MicOff else Color.White,
                        fontSize = if (compact) 14.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            MenuTile(
                modifier = Modifier.weight(1f).height(h),
                contentPadding = 4.dp,
                onClick = onLeave,
            ) {
                Text("Sair da voz", color = Ink, fontSize = if (compact) 14.sp else 15.sp, maxLines = 1)
            }
        }
    }
}

/**
 * Estado de voz de um assento, para a lista da sala e a faixa da mesa:
 * nada quando esta fora da voz; microfone quando esta dentro (dourado se esta
 * falando agora, vermelho se ele mesmo fechou, riscado se EU o silenciei).
 */
@Composable
fun VoiceBadge(seat: LobbySeat, ui: UiState, size: Dp = 16.dp) {
    if (!ui.hasVoice() || !seat.inVoice) return
    val me = seat.id == ui.myId
    val mutedByMe = !me && seat.id in ui.voice.mutedPeers
    val speaking = seat.id in ui.voice.speaking
    val tint = when {
        mutedByMe -> inkDim(0.45f)
        seat.micMuted -> MicOff
        speaking -> MenuGold
        else -> Ink
    }
    Box(
        modifier = Modifier
            .size(size + 8.dp)
            .then(
                if (speaking && !seat.micMuted && !mutedByMe)
                    Modifier.border(1.5.dp, MenuGold, RoundedCornerShape(6.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        MicIcon(size, tint = tint, muted = seat.micMuted || mutedByMe)
    }
}

/** Microfone: capsula, haste e arco. `muted` risca em diagonal. */
@Composable
fun MicIcon(size: Dp, tint: Color, muted: Boolean) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        // capsula
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.34f, h * 0.06f),
            size = Size(w * 0.32f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f, w * 0.16f),
        )
        // arco do suporte
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.18f),
            size = Size(w * 0.6f, h * 0.56f),
            style = Stroke(width = stroke),
        )
        // haste e base
        drawLine(tint, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.9f), stroke)
        drawLine(tint, Offset(w * 0.32f, h * 0.9f), Offset(w * 0.68f, h * 0.9f), stroke)
        if (muted) {
            drawLine(tint, Offset(w * 0.15f, h * 0.12f), Offset(w * 0.85f, h * 0.88f), stroke * 1.2f)
        }
    }
}

/** Espacador so para a barra nao colar no que vem depois quando ela existe. */
@Composable
fun VoiceGap(ui: UiState, height: Dp) {
    if (ui.hasVoice()) Spacer(Modifier.height(height))
}
