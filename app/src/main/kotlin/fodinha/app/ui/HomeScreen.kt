package fodinha.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fodinha.app.UiState

enum class HomeTab { BOT, WIFI, BLUETOOTH }

@Composable
fun HomeScreen(
    ui: UiState,
    onName: (String) -> Unit,
    onStartBots: (Int) -> Unit,
    onHostWifi: (String) -> Unit,
    onScanWifi: () -> Unit,
    onJoinWifi: (fodinha.app.net.DiscoveredRoom) -> Unit,
    onHostBluetooth: (String) -> Unit,
    onScanBluetooth: () -> Unit,
    onJoinBluetooth: (android.bluetooth.BluetoothDevice) -> Unit,
) {
    var tab by remember { mutableStateOf(HomeTab.BOT) }
    var bots by remember { mutableIntStateOf(3) }
    var room by remember { mutableStateOf("Mesa da sala") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("FODINHA", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text(
            "Baralho de 40 cartas, 10 vidas, previsao de vazas.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = ui.playerName,
            onValueChange = onName,
            label = { Text("Seu nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TabButton("Bots", tab == HomeTab.BOT, Modifier.weight(1f)) { tab = HomeTab.BOT }
            TabButton("WiFi", tab == HomeTab.WIFI, Modifier.weight(1f)) { tab = HomeTab.WIFI }
            TabButton("Bluetooth", tab == HomeTab.BLUETOOTH, Modifier.weight(1f)) { tab = HomeTab.BLUETOOTH }
        }
        Spacer(Modifier.height(16.dp))

        when (tab) {
            HomeTab.BOT -> Section("Jogar contra bots") {
                Text("Adversarios: $bots", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n ->
                        OutlinedButton(
                            onClick = { bots = n },
                            modifier = Modifier.weight(1f),
                        ) { Text("$n", fontWeight = if (bots == n) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onStartBots(bots) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Comecar partida")
                }
            }

            HomeTab.WIFI -> Section("Sala na rede WiFi") {
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Nome da sala") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onHostWifi(room) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir sala")
                }
                Spacer(Modifier.height(16.dp))
                Text("Ou entre numa sala existente:", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = onScanWifi, modifier = Modifier.fillMaxWidth()) {
                    Text("Procurar salas")
                }
                Spacer(Modifier.height(8.dp))
                if (ui.rooms.isEmpty()) {
                    Text(
                        "Nenhuma sala encontrada. O host precisa estar no mesmo WiFi.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(ui.rooms) { r ->
                            RowItem(r.name, "${r.host.hostAddress}:${r.port}") { onJoinWifi(r) }
                        }
                    }
                }
            }

            HomeTab.BLUETOOTH -> Section("Sala por Bluetooth") {
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Nome da sala") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onHostBluetooth(room) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir sala Bluetooth")
                }
                Spacer(Modifier.height(16.dp))
                Text("Ou conecte num aparelho ja pareado:", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = onScanBluetooth, modifier = Modifier.fillMaxWidth()) {
                    Text("Listar pareados")
                }
                Spacer(Modifier.height(8.dp))
                if (ui.btDevices.isEmpty()) {
                    Text(
                        "Pareie os aparelhos nas configuracoes do Android primeiro.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(ui.btDevices) { d ->
                            val name = runCatching { d.name }.getOrNull() ?: "Aparelho"
                            RowItem(name, d.address) { onJoinBluetooth(d) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RowItem(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Button(onClick = onClick) { Text("Entrar") }
        }
    }
}
