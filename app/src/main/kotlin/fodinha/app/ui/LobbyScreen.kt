package fodinha.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fodinha.app.UiState
import fodinha.app.net.TransportKind

/** Mesa de 2 a 6 assentos. */
private const val MAX_SEATS = 6

/**
 * Sala de espera na mesma linguagem do menu: mesa verde, blocos escuros e o
 * mesmo contador "- n +" para os bots.
 */
@Composable
fun LobbyScreen(
    ui: UiState,
    onAddBot: () -> Unit,
    onRemoveBot: () -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onJoinVoice: () -> Unit = {},
    onLeaveVoice: () -> Unit = {},
    onToggleMic: () -> Unit = {},
    onToggleMutePeer: (Int) -> Unit = {},
) {
    val lobby = ui.lobby
    val seats = lobby?.seats.orEmpty()
    val bots = seats.count { it.isBot }
    val canStart = seats.size >= 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MenuGreen, MenuGreenDeep))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Text(
                lobby?.roomName ?: "Sala",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Text(
                when (ui.kind) {
                    TransportKind.LOCAL -> "partida local contra bots"
                    TransportKind.WIFI -> "sala WiFi na rede local"
                    TransportKind.BLUETOOTH -> "sala Bluetooth"
                    TransportKind.INTERNET -> "sala pela internet"
                },
                color = inkDim(0.75f),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
            )

            // Codigo da sala: e o que o host dita para os amigos entrarem.
            ui.roomCode?.let { code ->
                Spacer(Modifier.height(14.dp))
                MenuTile(modifier = Modifier.fillMaxWidth().height(64.dp), onClick = {}) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("codigo da sala", color = inkDim(0.7f), fontSize = 12.sp)
                        Text(
                            code,
                            color = MenuGold,
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 6.sp,
                        )
                    }
                }
            }

            if (ui.connecting) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MenuGold,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("conectando...", color = Ink)
                }
            }

            VoiceGap(ui, 14.dp)
            VoiceBar(ui, onJoin = onJoinVoice, onLeave = onLeaveVoice, onToggleMic = onToggleMic)

            Spacer(Modifier.height(18.dp))
            Text(
                "jogadores (${seats.size}/$MAX_SEATS)",
                color = inkDim(0.8f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))

            seats.forEach { seat ->
                SeatRow(
                    name = seat.name + if (seat.id == ui.myId) "  (voce)" else "",
                    tag = when {
                        seat.isBot -> "bot"
                        seat.connected -> "conectado"
                        else -> "desconectado"
                    },
                    dot = when {
                        seat.isBot -> MenuGold
                        seat.connected -> Color(0xFF7FE0A0)
                        else -> Color(0xFFFF8A80)
                    },
                    me = seat.id == ui.myId,
                    badge = { VoiceBadge(seat, ui, size = 18.dp) },
                    // Tocar num vizinho que esta na voz silencia so no meu aparelho.
                    onClick = if (seat.inVoice && seat.id != ui.myId && ui.voice.connected)
                        ({ onToggleMutePeer(seat.id) }) else null,
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(10.dp))

            if (ui.isHost) {
                BotBox(
                    bots = bots,
                    canAdd = seats.size < MAX_SEATS,
                    onAdd = onAddBot,
                    onRemove = onRemoveBot,
                )
                Spacer(Modifier.height(14.dp))

                MenuTile(
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                    enabled = canStart,
                    onClick = onStart,
                ) {
                    Text(
                        "Comecar",
                        color = if (canStart) Color.White else inkDim(0.4f),
                        fontSize = 28.sp,
                    )
                }
                if (!canStart) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "precisa de pelo menos 2 jogadores na mesa",
                        color = inkDim(0.75f),
                        fontSize = 13.sp,
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(SlateSoft, RoundedCornerShape(12.dp))
                        .border(2.dp, MenuEdge, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "aguardando o dono da sala comecar...",
                        color = Ink,
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            MenuTile(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = onLeave,
            ) {
                Text("Sair da sala", color = Ink, fontSize = 16.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Assento: bolinha de estado, nome e etiqueta. */
@Composable
private fun SeatRow(
    name: String,
    tag: String,
    dot: Color,
    me: Boolean,
    badge: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Slate, RoundedCornerShape(10.dp))
            .then(
                if (me) Modifier.border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(10.dp))
                else Modifier
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(dot, CircleShape))
            Spacer(Modifier.size(10.dp))
            Text(
                name,
                color = Color.White,
                fontWeight = if (me) FontWeight.Bold else FontWeight.Normal,
                fontSize = 17.sp,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            badge()
            Text(tag, color = inkDim(0.6f), fontSize = 13.sp)
        }
    }
}

/** Mesmo contador do menu, agora mexendo nos bots ja sentados. */
@Composable
private fun BotBox(bots: Int, canAdd: Boolean, onAdd: () -> Unit, onRemove: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateSoft, RoundedCornerShape(12.dp))
            .border(2.dp, MenuEdge, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("bots na mesa:", color = Ink, fontSize = 14.sp, fontStyle = FontStyle.Italic)
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepButton("-", enabled = bots > 0, onClick = onRemove)
            Text("$bots", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
            StepButton("+", enabled = canAdd, onClick = onAdd)
        }
    }
}
