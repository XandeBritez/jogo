package fodinha.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fodinha.app.UiState
import fodinha.app.net.TransportKind

@Composable
fun LobbyScreen(
    ui: UiState,
    onAddBot: () -> Unit,
    onRemoveBot: () -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
) {
    val lobby = ui.lobby
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            lobby?.roomName ?: "Sala",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            when (ui.kind) {
                TransportKind.LOCAL -> "Partida local contra bots"
                TransportKind.WIFI -> "Sala WiFi na rede local"
                TransportKind.BLUETOOTH -> "Sala Bluetooth"
            },
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(20.dp))

        if (ui.connecting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text("  Conectando...")
            }
            Spacer(Modifier.height(16.dp))
        }

        Text("Jogadores (${lobby?.seats?.size ?: 0}/6)", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        lobby?.seats?.forEach { seat ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildString {
                            append(seat.name)
                            if (seat.id == ui.myId) append(" (voce)")
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            seat.isBot -> "bot"
                            seat.connected -> "conectado"
                            else -> "desconectado"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (ui.isHost) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAddBot, modifier = Modifier.weight(1f)) { Text("+ Bot") }
                OutlinedButton(onClick = onRemoveBot, modifier = Modifier.weight(1f)) { Text("- Bot") }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = (lobby?.seats?.size ?: 0) >= 2,
            ) { Text("Comecar jogo") }
            if ((lobby?.seats?.size ?: 0) < 2) {
                Text(
                    "Precisa de pelo menos 2 jogadores.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        } else {
            Text("Aguardando o dono da sala comecar...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onLeave) { Text("Sair da sala") }
    }
}
