package fodinha.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Icones do menu desenhados a mao.
 *
 * O material-icons-core que vem com o Material3 nao traz wifi nem bluetooth, e
 * puxar o -extended so por dois glifos custa mais que desenha-los.
 */

@Composable
fun WifiIcon(size: Dp, tint: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val baseY = h * 0.82f
        val stroke = Stroke(width = w * 0.09f)
        // Tres arcos concentricos abrindo para cima, mais o ponto.
        listOf(0.78f, 0.54f, 0.30f).forEach { r ->
            val rad = w * r
            drawArc(
                color = tint,
                startAngle = 215f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - rad, baseY - rad),
                size = Size(rad * 2, rad * 2),
                style = stroke,
            )
        }
        drawCircle(color = tint, radius = w * 0.07f, center = Offset(cx, baseY))
    }
}

@Composable
fun BluetoothIcon(size: Dp, tint: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.1f)
        val cx = w * 0.5f
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.32f)
            lineTo(w * 0.78f, h * 0.70f)
            moveTo(w * 0.22f, h * 0.70f)
            lineTo(w * 0.78f, h * 0.32f)
            moveTo(cx, h * 0.06f)
            lineTo(cx, h * 0.94f)
            moveTo(cx, h * 0.06f)
            lineTo(w * 0.78f, h * 0.32f)
            moveTo(cx, h * 0.94f)
            lineTo(w * 0.78f, h * 0.70f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
fun HelpIcon(size: Dp, tint: Color = Color.White) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(
                color = tint,
                radius = this.size.width / 2f - this.size.width * 0.05f,
                style = Stroke(width = this.size.width * 0.09f),
            )
        }
        // O "?" precisa perder o padding de fonte, senao desce e encosta no aro.
        Text(
            "?",
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.58f).sp,
            lineHeight = (size.value * 0.58f).sp,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

/** "i" desenhado: ponto e haste, sem depender da metrica da fonte. */
@Composable
fun InfoIcon(size: Dp, tint: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2f, w / 2f)
        drawCircle(color = tint, radius = w / 2f - w * 0.05f, center = c, style = Stroke(width = w * 0.09f))
        drawCircle(color = tint, radius = w * 0.055f, center = Offset(c.x, w * 0.28f))
        drawLine(
            color = tint,
            start = Offset(c.x, w * 0.44f),
            end = Offset(c.x, w * 0.74f),
            strokeWidth = w * 0.11f,
        )
    }
}

/** Engrenagem simplificada: disco vazado com oito dentes. */
@Composable
fun GearIcon(size: Dp, tint: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2f, w / 2f)
        val ring = w * 0.30f
        drawCircle(color = tint, radius = ring, center = c, style = Stroke(width = w * 0.11f))
        repeat(8) { i ->
            val angle = Math.toRadians(i * 45.0)
            val from = Offset(
                c.x + (ring * 1.18f * Math.cos(angle)).toFloat(),
                c.y + (ring * 1.18f * Math.sin(angle)).toFloat(),
            )
            val to = Offset(
                c.x + (ring * 1.48f * Math.cos(angle)).toFloat(),
                c.y + (ring * 1.48f * Math.sin(angle)).toFloat(),
            )
            drawLine(color = tint, start = from, end = to, strokeWidth = w * 0.13f)
        }
    }
}
