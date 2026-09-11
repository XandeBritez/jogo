package fodinha.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fodinha.engine.Card as GameCard
import fodinha.engine.Phase
import fodinha.engine.PlayerView

@Composable
fun TableScreen(
    view: PlayerView,
    onBid: (Int) -> Unit,
    onPlay: (GameCard) -> Unit,
    onPlayBlind: () -> Unit,
    onNextRound: () -> Unit,
    onLeave: () -> Unit,
) {
    var historyOpen by rememberSaveable { mutableStateOf(false) }

    // Voltar fecha o painel antes de sair da mesa.
    BackHandler(enabled = historyOpen) { historyOpen = false }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            HeaderBar(view, onLeave, onOpenHistory = { historyOpen = true })
            Spacer(Modifier.height(10.dp))
            PlayersStrip(view)
            Spacer(Modifier.height(12.dp))
            TrickArea(view)
            Spacer(Modifier.height(12.dp))

            TurnClock(view)

            when (view.phase) {
                Phase.BIDDING -> BiddingPanel(view, onBid)
                Phase.PLAYING -> PlayingPanel(view)
                Phase.TRICK_REVEAL -> TrickRevealPanel(view)
                Phase.ROUND_OVER -> RoundOverPanel(view, onNextRound)
                Phase.GAME_OVER -> GameOverPanel(view, onLeave)
            }

            Spacer(Modifier.height(12.dp))
            HandArea(view, onPlay, onPlayBlind)
        }

        SideSheet(
            open = historyOpen,
            title = "Historico da partida",
            onClose = { historyOpen = false },
        ) {
            HistoryList(view)
        }
    }
}

/** Historico dentro do painel lateral. Mais recente no topo. */
@Composable
private fun HistoryList(view: PlayerView) {
    if (view.log.isEmpty()) {
        Text(
            "Nada aconteceu ainda.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
        return
    }

    Text(
        "Mais recente primeiro",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
    Spacer(Modifier.height(8.dp))
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(view.log.size) { i ->
            val linha = view.log[view.log.size - 1 - i]
            // Cabecalho de rodada fica em destaque: separa visualmente os blocos.
            val abreRodada = linha.startsWith("Rodada ")
            Text(
                linha,
                fontSize = 13.sp,
                fontWeight = if (abreRodada) FontWeight.Bold else FontWeight.Normal,
                color = if (abreRodada) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Conta o tempo do turno para baixo. O host manda quantos segundos restavam
 * quando despachou a view; daqui para frente quem conta e o aparelho, para
 * nao depender dos relogios dos dois lados baterem.
 */
@Composable
private fun TurnClock(view: PlayerView) {
    val total = view.turnSecondsLeft ?: return
    if (view.phase != Phase.BIDDING && view.phase != Phase.PLAYING) return

    // Rearma sempre que o turno muda: outra vez, outra vaza, outra fase.
    val turnKey = listOf(view.roundIndex, view.trickIndex, view.currentPlayerId, view.phase, total)
    var left by remember(turnKey) { mutableIntStateOf(total) }
    LaunchedEffect(turnKey) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
    }

    val limit = if (view.phase == Phase.BIDDING) 60f else 40f
    val urgent = left <= 10
    val label = when {
        view.isMyTurn && view.phase == Phase.BIDDING -> "Sua previsao em ${left}s"
        view.isMyTurn -> "Sua jogada em ${left}s"
        else -> "${left}s"
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (urgent) FontWeight.Black else FontWeight.SemiBold,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            )
            if (view.isMyTurn) {
                Text(
                    if (view.phase == Phase.BIDDING) "no fim, previsao sorteada" else "no fim, carta sorteada",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (left / limit).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Botao enxuto do cabecalho, alto o suficiente para a dupla empilhada ficar
 * da altura das cartas de virada e manilha.
 *
 * Feito na mao em vez de OutlinedButton: o Material3 impoe alvo de toque de
 * 48dp de altura, e dois desses empilhados passariam longe das cartas.
 */
@Composable
private fun CompactButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun HeaderBar(view: PlayerView, onLeave: () -> Unit, onOpenHistory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // O texto da rodada cede espaco: os botoes e a virada tem largura fixa,
        // e sem isto o "Sair" ficava espremido a ponto de quebrar letra a letra.
        Column(Modifier.weight(1f)) {
            Text(
                "Rodada ${view.roundIndex + 1} · ${view.cardsThisRound} carta(s)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Vaza ${minOf(view.trickIndex + 1, view.cardsThisRound)} de ${view.cardsThisRound}",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
        }

        view.turned?.let { t ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("virada", fontSize = 10.sp, maxLines = 1)
                PlayingCard(t, small = true)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("manilha", fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.primary)
                ManilhaCard(view.manilhaLabel ?: "-")
            }
        }

        Column(
            modifier = Modifier.width(92.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CompactButton("Historico", onOpenHistory)
            CompactButton("Sair", onLeave)
        }
    }
}

/**
 * Os jogadores ocupam a largura toda, divididos em partes iguais.
 * Ate 5 por linha; com mais que isso as linhas sao equilibradas (6 vira 3+3),
 * para nenhuma linha ficar com uma celula solta ocupando um quinto da tela.
 */
@Composable
private fun PlayersStrip(view: PlayerView) {
    val ids = view.order
    if (ids.isEmpty()) return

    val porLinha = slotsPorLinha(ids.size)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ids.chunked(porLinha).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                linha.forEach { id ->
                    PlayerSlot(view, id, Modifier.weight(1f).fillMaxHeight())
                }
                // Linha incompleta: o vazio entra como peso, para as celulas
                // preenchidas manterem a mesma largura das outras linhas.
                repeat(porLinha - linha.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Nome, coracoes e previsao de um jogador. Estreito: cabe em 1/5 da tela. */
@Composable
private fun PlayerSlot(view: PlayerView, id: Int, modifier: Modifier) {
    val p = view.players.first { it.id == id }
    val isTurn = view.currentPlayerId == id
    val souEu = id == view.me
    val bid = view.bids[id]
    val won = view.tricksWon[id] ?: 0

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isTurn) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(10.dp),
        // Sem espaco para escrever "(voce)": a borda marca quem e voce.
        border = if (souEu) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                p.name,
                fontWeight = if (isTurn) FontWeight.Black else FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (p.alive) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
            Text("♥ ${p.lives}", fontSize = 12.sp, maxLines = 1)
            Text(
                if (bid == null) "—" else "$won/$bid",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = when {
                    bid == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    won > bid -> Color(0xFFFF8A80)
                    won == bid -> Color(0xFF7FC8A9)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            // Rodada cega de 1 carta: vejo a carta dos outros, nao a minha.
            view.revealedHands[id]?.firstOrNull()?.let { c ->
                Spacer(Modifier.height(4.dp))
                PlayingCard(c, small = true, isManilha = c.rank.label == view.manilhaLabel)
            }
        }
    }
}

/** Cartas no centro da mesa, na ordem em que sairam. */
@Composable
private fun TrickArea(view: PlayerView) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (view.currentTrick.isEmpty()) {
            Text(
                if (view.phase == Phase.BIDDING) "Fazendo previsoes..." else "Mesa vazia",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                view.currentTrick.forEach { play ->
                    val p = view.players.first { it.id == play.playerId }
                    val venceu = view.trickWinnerId == play.playerId
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayingCard(
                            play.card,
                            isManilha = play.card.rank.label == view.manilhaLabel,
                        )
                        Text(
                            if (venceu) "${p.name} ✓" else p.name,
                            fontSize = 11.sp,
                            fontWeight = if (venceu) FontWeight.Black else FontWeight.Normal,
                            color = if (venceu) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BiddingPanel(view: PlayerView, onBid: (Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            val blindOne = view.cardsThisRound == 1
            Text(
                when {
                    blindOne -> "Previsao as cegas: voce NAO ve sua carta, mas ve a dos outros."
                    view.handHiddenUntilBidsDone -> "Previsao as cegas: aposte antes de olhar a mao."
                    else -> "Quantas vazas voce vai ganhar?"
                },
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Soma das previsoes ate agora: ${view.bidSum} de ${view.cardsThisRound}",
                fontSize = 12.sp,
            )
            if (view.isMyTurn && view.forbiddenBid != null && view.forbiddenBid in 0..view.cardsThisRound &&
                !view.legalBids.contains(view.forbiddenBid)
            ) {
                Text(
                    "Voce e o ultimo: nao pode prever ${view.forbiddenBid} (a soma nao pode dar ${view.cardsThisRound}).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))

            if (view.isMyTurn) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (0..view.cardsThisRound).forEach { n ->
                        val legal = view.legalBids.contains(n)
                        Button(
                            onClick = { onBid(n) },
                            enabled = legal,
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text("$n", fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                val who = view.currentPlayerId?.let { id -> view.players.first { it.id == id }.name }
                Text("Vez de $who prever...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Vaza fechada: as cartas ficam na mesa uns segundos antes de serem recolhidas. */
@Composable
private fun TrickRevealPanel(view: PlayerView) {
    val winner = view.trickWinnerId?.let { id -> view.players.firstOrNull { it.id == id } }
    Text(
        if (winner == null) "Vaza fechada"
        else if (winner.id == view.me) "Voce ganhou a vaza" else "${winner.name} ganhou a vaza",
        fontWeight = FontWeight.Black,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        "Recolhendo a mesa...",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
    )
}

@Composable
private fun PlayingPanel(view: PlayerView) {
    val who = view.currentPlayerId?.let { id -> view.players.first { it.id == id }.name }
    Text(
        if (view.isMyTurn) "Sua vez: escolha uma carta" else "Vez de $who",
        fontWeight = FontWeight.SemiBold,
        color = if (view.isMyTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun RoundOverPanel(view: PlayerView, onNext: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Fim da rodada", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            view.lastRoundSummary.forEach { r ->
                val p = view.players.first { it.id == r.playerId }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(p.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "previu ${r.bid}, fez ${r.won}  →  -${r.livesLost}  (${r.livesAfter} vidas)" +
                            if (r.eliminated) "  ELIMINADO" else "",
                        fontSize = 13.sp,
                        color = if (r.livesLost == 0) Color(0xFF7FC8A9)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Proxima rodada") }
        }
    }
}

@Composable
private fun GameOverPanel(view: PlayerView, onLeave: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Fim de jogo", fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text("Classificacao", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            // eliminationOrder: primeiro eliminado primeiro, entao o ranking e o inverso.
            view.eliminationOrder.reversed().forEachIndexed { i, id ->
                val p = view.players.first { it.id == id }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${i + 1}o  ${p.name}",
                        fontWeight = if (i == 0) FontWeight.Black else FontWeight.Normal,
                        color = if (i == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${p.lives} vidas", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao inicio") }
        }
    }
}

/** Minha mao. Na rodada cega de 1 carta mostra so o verso. */
@Composable
private fun HandArea(view: PlayerView, onPlay: (GameCard) -> Unit, onPlayBlind: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Sua mao", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                view.cardsThisRound == 1 && (view.handSizes[view.me] ?: 0) > 0 -> {
                    // Virada para fora: nao vejo a minha, mas sou eu quem joga.
                    CardBack(
                        onClick = if (view.canPlayBlind) onPlayBlind else null,
                        highlighted = view.canPlayBlind,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (view.canPlayBlind) "Sua vez: toque na carta para joga-la."
                        else "Carta virada para fora:\nvoce nao ve a sua.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    )
                }

                view.handHiddenUntilBidsDone -> {
                    repeat(view.cardsThisRound) { CardBack(small = true) }
                }

                view.myHand.isEmpty() -> CardSlot()

                else -> view.myHand.forEach { card ->
                    val playable = view.legalPlays.contains(card) && view.isMyTurn
                    PlayingCard(
                        card = card,
                        isManilha = card.rank.label == view.manilhaLabel,
                        enabled = playable,
                        onClick = if (playable) ({ onPlay(card) }) else null,
                    )
                }
            }
        }
        if (view.canPlayBlind) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPlayBlind) { Text("Jogar minha carta") }
        }
    }
}


