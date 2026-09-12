package fodinha.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fodinha.app.UiState


private val LogoRed = Color(0xFFD32020)
private val LogoBlack = Color(0xFF141414)

/** Uma letra do logo, desenhada como carta de baralho. */
private data class LogoCard(val letter: String, val suit: String, val red: Boolean)

private val LogoWord = listOf(
    LogoCard("F", "♣", false),
    LogoCard("o", "♥", true),
    LogoCard("d", "♠", false),
    LogoCard("i", "♦", true),
    LogoCard("n", "♣", false),
    LogoCard("h", "♥", true),
    LogoCard("a", "♠", false),
)

@Composable
fun HomeScreen(
    ui: UiState,
    onName: (String) -> Unit,
    onStartBots: (Int) -> Unit,
    onHostWifi: (String) -> Unit,
    onScanWifi: () -> Unit,
    onStopScanWifi: () -> Unit,
    onJoinWifi: (fodinha.app.net.DiscoveredRoom) -> Unit,
    onHostBluetooth: (String) -> Unit,
    onScanBluetooth: () -> Unit,
    onJoinBluetooth: (android.bluetooth.BluetoothDevice) -> Unit,
    onOpenOptions: () -> Unit,
) {
    var bots by remember { mutableIntStateOf(3) }
    var room by remember { mutableStateOf("Mesa da sala") }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MenuGreen, MenuGreenDeep)))
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Logo()
            Spacer(Modifier.weight(1.1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                ModeBox(
                    bots = bots,
                    onBots = { bots = it },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                NameBox(
                    name = ui.playerName,
                    modifier = Modifier.weight(1f),
                    onClick = { dialog = HomeDialog.NAME },
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Jogar: bloco grande a esquerda, como no print.
                MenuTile(
                    modifier = Modifier.weight(1.05f).height(172.dp),
                    onClick = { onStartBots(bots) },
                ) {
                    Text("Jogar", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Normal)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MenuTile(
                        modifier = Modifier.fillMaxWidth().height(81.dp),
                        onClick = {
                            onScanWifi()
                            dialog = HomeDialog.WIFI
                        },
                    ) { StackedTileLabel("Internet") { WifiIcon(24.dp) } }
                    MenuTile(
                        modifier = Modifier.fillMaxWidth().height(81.dp),
                        onClick = {
                            onScanBluetooth()
                            dialog = HomeDialog.BLUETOOTH
                        },
                    ) { StackedTileLabel("Bluetooth") { BluetoothIcon(24.dp) } }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MenuTile(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        onClick = { dialog = HomeDialog.HELP },
                    ) { TileLabel("Ajuda") { HelpIcon(19.dp) } }
                    MenuTile(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        onClick = { dialog = HomeDialog.ABOUT },
                    ) { TileLabel("Sobre") { InfoIcon(19.dp) } }
                    MenuTile(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        onClick = onOpenOptions,
                    ) { TileLabel("Opcoes") { GearIcon(19.dp) } }
                }
            }
        }
    }

    when (dialog) {
        HomeDialog.NAME -> NameDialog(ui.playerName, onName) { dialog = null }
        HomeDialog.WIFI -> RoomDialog(
            title = "Sala na rede WiFi",
            room = room,
            onRoom = { room = it },
            hostLabel = "Abrir sala",
            onHost = { onStopScanWifi(); onHostWifi(room); dialog = null },
            rescanLabel = "Procurar salas",
            onRescan = onScanWifi,
            emptyHint = "Nenhuma sala encontrada. O host precisa estar no mesmo WiFi.",
            entries = ui.rooms.map { r -> Entry(r.name, "${r.host.hostAddress}:${r.port}") { onJoinWifi(r); dialog = null } },
            onDismiss = { onStopScanWifi(); dialog = null },
        )
        HomeDialog.BLUETOOTH -> RoomDialog(
            title = "Sala por Bluetooth",
            room = room,
            onRoom = { room = it },
            hostLabel = "Abrir sala Bluetooth",
            onHost = { onHostBluetooth(room); dialog = null },
            rescanLabel = "Listar pareados",
            onRescan = onScanBluetooth,
            emptyHint = "Pareie os aparelhos nas configuracoes do Android primeiro.",
            entries = ui.btDevices.map { d ->
                val name = runCatching { d.name }.getOrNull() ?: "Aparelho"
                Entry(name, d.address) { onJoinBluetooth(d); dialog = null }
            },
            onDismiss = { dialog = null },
        )
        HomeDialog.HELP -> InfoDialog("Como se joga", HELP_TEXT) { dialog = null }
        HomeDialog.ABOUT -> InfoDialog("Sobre", ABOUT_TEXT) { dialog = null }
        null -> Unit
    }
}

private enum class HomeDialog { NAME, WIFI, BLUETOOTH, HELP, ABOUT }

/** "Fodinha" escrito em cartas, do jeito que o miniTruco escreve o nome dele. */
@Composable
private fun Logo() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoWord.forEachIndexed { i, c ->
            val tint = if (c.red) LogoRed else LogoBlack
            Box(
                modifier = Modifier
                    .weight(1f)
                    .rotate(if (i % 2 == 0) -2f else 2f)
                    .aspectRatio(0.7f)
                    .background(Color(0xFFFFFDF7), RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0x22000000), RoundedCornerShape(6.dp)),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        c.letter,
                        color = tint,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        maxLines = 1,
                    )
                    Text(
                        c.suit,
                        color = tint,
                        fontSize = 20.sp,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

/**
 * Caixa "escolha o modo" do print. Aqui o que muda de modo para modo e
 * quantos bots entram na mesa: a Engine nao tem variante de regra para
 * oferecer, entao o seletor escolhe o que existe de verdade.
 */
@Composable
private fun ModeBox(bots: Int, onBots: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(SlateSoft, RoundedCornerShape(12.dp))
            .border(2.dp, MenuEdge, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "escolha o modo:",
            color = Ink,
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepButton("-", enabled = bots > MIN_BOTS) { onBots(bots - 1) }
            Text(
                "$bots",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
            )
            StepButton("+", enabled = bots < MAX_BOTS) { onBots(bots + 1) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (bots == 1) "1 adversario" else "$bots adversarios",
            color = Color.White,
            fontSize = 18.sp,
        )
    }
}

/** Mesa de 2 a 6: eu mais 1..5 bots. */
private const val MIN_BOTS = 1
private const val MAX_BOTS = 5

/**
 * Nome do jogador com cara de campo: moldura e lapis, senao ninguem descobre
 * que da para tocar e trocar.
 */
@Composable
private fun NameBox(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("jogando como", color = Ink.copy(alpha = 0.75f), fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Slate, RoundedCornerShape(10.dp))
                .border(BorderStroke(1.5.dp, Color(0x66FFFFFF)), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Text("✎", color = MenuGold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text("toque para trocar", color = Ink.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

@Composable
private fun TileLabel(label: String, icon: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(label, color = Color.White, fontSize = 15.sp, maxLines = 1, softWrap = false)
    }
}

/** Icone em cima do texto: lado a lado, "Bluetooth" nao cabe na largura do tile. */
@Composable
private fun StackedTileLabel(label: String, icon: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(label, color = Color.White, fontSize = 16.sp, maxLines = 1, softWrap = false)
    }
}

// ---------- dialogos ----------

private data class Entry(val title: String, val subtitle: String, val onJoin: () -> Unit)

@Composable
private fun NameDialog(current: String, onName: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seu nome") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onName(text.ifBlank { "Voce" }); onDismiss() }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun RoomDialog(
    title: String,
    room: String,
    onRoom: (String) -> Unit,
    hostLabel: String,
    onHost: () -> Unit,
    rescanLabel: String,
    onRescan: () -> Unit,
    emptyHint: String,
    entries: List<Entry>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = room,
                    onValueChange = onRoom,
                    label = { Text("Nome da sala") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onHost, modifier = Modifier.fillMaxWidth()) { Text(hostLabel) }
                Spacer(Modifier.height(14.dp))
                Text("Ou entre numa sala existente:", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) { Text(rescanLabel) }
                Spacer(Modifier.height(8.dp))
                if (entries.isEmpty()) {
                    Text(emptyHint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 190.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(entries) { e ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        e.subtitle,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        maxLines = 1,
                                    )
                                }
                                Button(onClick = e.onJoin) { Text("Entrar") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi") } },
    )
}

private const val HELP_TEXT =
    "Baralho de 40 cartas e 10 vidas por jogador.\n\n" +
        "A manilha e a carta seguinte a virada (3 vira 4). Entre manilhas, paus > copas > " +
        "espadas > ouros.\n\n" +
        "Cada um preve quantas vazas vai fazer. A soma das previsoes nunca pode dar o numero " +
        "de cartas da rodada, e quem trava isso e o ultimo a prever.\n\n" +
        "Errou a previsao, perde |previsao - vazas| vidas. Zerou, saiu.\n\n" +
        "Rodada de 1 carta e cega: voce ve a carta de todo mundo, menos a sua."

private const val ABOUT_TEXT =
    "Fodinha\nversao 1.0\n\nKotlin + Jetpack Compose. Regras no modulo :engine, " +
        "host-autoritativo no WiFi e no Bluetooth.\n\nfeito com carinho para jogar na mesa."
