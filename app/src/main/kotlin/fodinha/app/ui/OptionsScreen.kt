package fodinha.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A tela de opcoes e escura nos dois temas: e a identidade dela. O que muda
// aqui e o quanto fecha no tema escuro e no alto contraste.
private val OptBg: Color @Composable get() = LocalMenuPalette.current.optBg
private val OptDialog: Color @Composable get() = LocalMenuPalette.current.optDialog
private val OptTitle: Color @Composable get() = LocalMenuPalette.current.optTitle
private val OptSub: Color @Composable get() = LocalMenuPalette.current.optSub
private val OptAccent: Color @Composable get() = LocalMenuPalette.current.optAccent

/**
 * Tela de opcoes no estilo do print: lista escura com cabecalhos de secao e o
 * controle de cada linha na direita. Pinta o proprio fundo porque o App
 * envolve tudo num gradiente de feltro verde que nao serve aqui.
 */
@Composable
fun OptionsScreen(
    settings: GameSettings,
    playerName: String,
    onSettings: (GameSettings) -> Unit,
    onName: (String) -> Unit,
    onBack: () -> Unit,
) {
    var dialog by remember { mutableStateOf<OptDialogKind?>(null) }

    BackHandler { if (dialog != null) dialog = null else onBack() }

    Box(Modifier.fillMaxSize().background(OptBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("< Voltar", color = OptAccent, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(8.dp))

            SectionHeader("Visual")

            OptionRow(
                title = "Tema",
                subtitle = "Mesa clara ou escura. Por padrao segue o modo escuro do aparelho",
                onClick = { dialog = OptDialogKind.THEME },
            ) {
                Text(settings.themeMode.label, color = OptAccent, fontSize = 15.sp)
            }

            OptionRow(
                title = "Alto contraste",
                subtitle = "Fecha o fundo, tira a transparencia do texto e devolve a carta ao " +
                    "branco, ignorando a cor escolhida abaixo",
                onClick = { onSettings(settings.copy(highContrast = !settings.highContrast)) },
            ) {
                CheckBox(settings.highContrast)
            }

            OptionRow(
                title = "Baralho",
                subtitle = "Escolha o desenho atras das cartas",
                onClick = { dialog = OptDialogKind.DECK },
            ) {
                DeckBackArt(
                    back = settings.deckBack,
                    modifier = Modifier.width(38.dp).height(54.dp),
                    glyph = 7.sp,
                )
            }

            OptionRow(
                title = "Cor de fundo das cartas e baloes",
                subtitle = "Se o \"modo escuro\" do aparelho deixa estes elementos ilegiveis, ajuste aqui",
                onClick = { dialog = OptDialogKind.COLOR },
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(settings.cardColor, CircleShape)
                        .border(1.dp, Color(0x55FFFFFF), CircleShape),
                )
            }

            OptionRow(
                title = "Tamanho do Texto",
                subtitle = "Aumente o tamanho da fonte dos baloes, botoes e perguntas do jogo",
                onClick = { dialog = OptDialogKind.TEXT },
            ) {
                Text(settings.textScale.label, color = OptAccent, fontSize = 15.sp)
            }

            OptionRow(
                title = "Animacao Rapida",
                subtitle = "Selecione para acelerar a distribuicao, recolhimento e animacao geral das cartas",
                onClick = { onSettings(settings.copy(fastAnimation = !settings.fastAnimation)) },
            ) {
                CheckBox(settings.fastAnimation)
            }

            SectionHeader("Jogo")

            OptionRow(
                title = "Seu nome",
                subtitle = "Como os outros jogadores te veem na mesa",
                onClick = { dialog = OptDialogKind.NAME },
            ) {
                Text(playerName, color = OptAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "As regras (40 cartas, 10 vidas, manilha pela virada) nao mudam: sao do " +
                    "modulo de regras, igual para todo mundo na mesa.",
                color = OptSub,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        OptDialogKind.DECK -> DeckDialog(settings.deckBack, {
            onSettings(settings.copy(deckBack = it))
            dialog = null
        }) { dialog = null }

        OptDialogKind.COLOR -> ColorDialog(settings.cardColor, {
            onSettings(settings.copy(cardColor = it))
            dialog = null
        }) { dialog = null }

        OptDialogKind.THEME -> ThemeDialog(settings.themeMode, {
            onSettings(settings.copy(themeMode = it))
            dialog = null
        }) { dialog = null }

        OptDialogKind.TEXT -> TextSizeDialog(settings.textScale, {
            onSettings(settings.copy(textScale = it))
            dialog = null
        }) { dialog = null }

        OptDialogKind.NAME -> NameEditDialog(playerName, {
            onName(it)
            dialog = null
        }) { dialog = null }

        null -> Unit
    }
}

private enum class OptDialogKind { THEME, DECK, COLOR, TEXT, NAME }

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, color = OptAccent, fontSize = 17.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = OptTitle, fontSize = 19.sp)
            Text(subtitle, color = OptSub, fontSize = 15.sp)
        }
        control()
    }
}

/** Caixa de selecao no estilo do print: quadrado vazado, verde quando ligado. */
@Composable
private fun CheckBox(checked: Boolean) {
    Box(
        Modifier
            .size(26.dp)
            .background(if (checked) Color(0xFF1F6B58) else Color.Transparent, RoundedCornerShape(3.dp))
            .border(2.dp, if (checked) OptAccent else Color(0xFF9E9E9E), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = OptAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- dialogos ----------

@Composable
private fun DeckDialog(current: DeckBack, onPick: (DeckBack) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OptDialog,
        title = { Text("Selecione um baralho", color = OptTitle, fontSize = 22.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DeckBack.entries.toList().chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        pair.forEach { back ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onPick(back) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                DeckBackArt(
                                    back = back,
                                    modifier = Modifier
                                        .width(76.dp)
                                        .height(104.dp)
                                        .border(
                                            if (back == current) 3.dp else 0.dp,
                                            if (back == current) OptAccent else Color.Transparent,
                                            RoundedCornerShape(8.dp),
                                        ),
                                    rows = 5,
                                    cols = 3,
                                    glyph = 12.sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(back.label, color = OptTitle, fontSize = 17.sp)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = OptAccent) } },
    )
}

@Composable
private fun ColorDialog(current: Color, onPick: (Color) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OptDialog,
        title = { Text("Selecionar uma cor", color = OptTitle, fontSize = 22.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardColorChoices.chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(c, CircleShape)
                                        .border(1.dp, Color(0x33FFFFFF), CircleShape)
                                        .clickable { selected = c },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (c == selected) {
                                        Text(
                                            "✓",
                                            color = if (c.luminance() >= 0.45f) Color.Black else Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }) { Text("Selecionar", color = OptAccent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = OptAccent) } },
    )
}

@Composable
private fun ThemeDialog(current: ThemeMode, onPick: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OptDialog,
        title = { Text("Tema da mesa", color = OptTitle, fontSize = 22.sp) },
        text = {
            Column {
                ThemeMode.entries.forEach { modo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(modo) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(modo.label, color = OptTitle, fontSize = 17.sp)
                        if (modo == current) Text("✓", color = OptAccent, fontSize = 18.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = OptAccent) } },
    )
}

@Composable
private fun TextSizeDialog(current: TextScale, onPick: (TextScale) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OptDialog,
        title = { Text("Tamanho do texto", color = OptTitle, fontSize = 22.sp) },
        text = {
            Column {
                TextScale.entries.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(s) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            s.label,
                            color = OptTitle,
                            fontSize = (16 * s.factor).sp,
                        )
                        if (s == current) Text("✓", color = OptAccent, fontSize = 18.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = OptAccent) } },
    )
}

@Composable
private fun NameEditDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OptDialog,
        title = { Text("Seu nome", color = OptTitle) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = menuTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.ifBlank { "Voce" }) }) { Text("Salvar", color = OptAccent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = OptAccent) } },
    )
}
