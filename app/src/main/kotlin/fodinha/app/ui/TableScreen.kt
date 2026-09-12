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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fodinha.engine.Card as GameCard
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.isUnspecified
import fodinha.engine.Phase
import fodinha.engine.PlayerView

/** Espera do resumo da rodada. Igual ao `roundOverMillis` do host. */
private const val ROUND_OVER_SECONDS = 5

@Composable
fun TableScreen(
    view: PlayerView,
    onBid: (Int) -> Unit,
    onPlay: (GameCard) -> Unit,
    onPlayBlind: () -> Unit,
    onPlayBlindAt: (Int) -> Unit,
    onLeave: () -> Unit,
) {
    var historyOpen by rememberSaveable { mutableStateOf(false) }

    // Voltar fecha o painel antes de sair da mesa.
    BackHandler(enabled = historyOpen) { historyOpen = false }

    // "Tamanho do Texto" das opcoes: pega tambem o texto que nao declara sp.
    val base = LocalTextStyle.current
    val baseSize = if (base.fontSize.isUnspecified) 16.sp else base.fontSize
    CompositionLocalProvider(LocalTextStyle provides base.copy(fontSize = scaled(baseSize))) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MenuGreen, MenuGreenDeep))),
    ) {
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
                Phase.ROUND_OVER -> RoundOverPanel(view)
                Phase.GAME_OVER -> GameOverPanel(view, onLeave)
            }

            Spacer(Modifier.height(12.dp))
            HandArea(view, onPlay, onPlayBlind, onPlayBlindAt)
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
}

/** Historico dentro do painel lateral. Mais recente no topo. */
@Composable
private fun HistoryList(view: PlayerView) {
    if (view.log.isEmpty()) {
        Text(
            "Nada aconteceu ainda.",
            fontSize = 13.sp,
            color = inkDim(0.75f),
        )
        return
    }

    Text(
        "Mais recente primeiro",
        fontSize = 11.sp,
        color = inkDim(0.7f),
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
                color = if (abreRodada) MenuGold else Ink,
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
                color = if (urgent) Color(0xFFFF8A80) else Color.White,
            )
            if (view.isMyTurn) {
                Text(
                    if (view.phase == Phase.BIDDING) "no fim, previsao sorteada" else "no fim, carta sorteada",
                    fontSize = 11.sp,
                    color = inkDim(0.75f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (left / limit).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (urgent) Color(0xFFFF8A80) else MenuGold,
            trackColor = Slate,
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
    MenuTile(
        modifier = Modifier.fillMaxWidth().height(34.dp),
        contentPadding = 2.dp,
        onClick = onClick,
    ) {
        Text(label, color = Ink, fontSize = 12.sp, maxLines = 1, softWrap = false)
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
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Vaza ${minOf(view.trickIndex + 1, view.cardsThisRound)} de ${view.cardsThisRound}",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = inkDim(0.8f),
            )
        }

        view.turned?.let { t ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("virada", fontSize = 10.sp, maxLines = 1, color = inkDim(0.8f))
                PlayingCard(t, small = true)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("manilha", fontSize = 10.sp, maxLines = 1, color = MenuGold)
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

    Box(
        modifier = modifier
            .background(Slate, RoundedCornerShape(10.dp))
            // Dourado marca de quem e a vez; branco marca voce.
            .then(
                when {
                    isTurn -> Modifier.border(2.dp, MenuGold, RoundedCornerShape(10.dp))
                    souEu -> Modifier.border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(10.dp))
                    else -> Modifier
                }
            ),
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
                color = if (p.alive) Color.White else inkDim(0.4f),
            )
            Text("♥ ${p.lives}", fontSize = 12.sp, maxLines = 1, color = Ink)
            Text(
                if (bid == null) "—" else "$won/$bid",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = when {
                    bid == null -> inkDim(0.5f)
                    won > bid -> Color(0xFFFF8A80)
                    won == bid -> Color(0xFF7FE0A0)
                    else -> Ink
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

/**
 * Cartas no centro da mesa, na ordem em que sairam.
 *
 * Quebra em linhas equilibradas pelo numero de jogadores, e nao pelas cartas
 * ja jogadas: assim a mesa reserva o espaco desde a primeira carta em vez de
 * mudar de formato no meio da vaza.
 */
@Composable
private fun TrickArea(view: PlayerView) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 130.dp)
            .background(SlateSoft, RoundedCornerShape(14.dp))
            .border(2.dp, MenuEdge, RoundedCornerShape(14.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (view.currentTrick.isEmpty()) {
            Text(
                if (view.phase == Phase.BIDDING) "Fazendo previsoes..." else "Mesa vazia",
                color = inkDim(0.7f),
            )
        } else {
            val porLinha = cartasPorLinha(maxOf(view.order.size, view.currentTrick.size))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                view.currentTrick.chunked(porLinha).forEach { linha ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        linha.forEach { play ->
                            val p = view.players.first { it.id == play.playerId }
                            val venceu = view.trickWinnerId == play.playerId
                            Column(
                                // Nome comprido nao pode empurrar a carta do
                                // vizinho para fora da mesa.
                                modifier = Modifier.width(82.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PlayingCard(
                                    play.card,
                                    isManilha = play.card.rank.label == view.manilhaLabel,
                                )
                                // O visto fica fora do nome: junto, era ele que
                                // sumia no "..." de quem tem nome comprido.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        p.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (venceu) FontWeight.Black else FontWeight.Normal,
                                        color = if (venceu) MenuGold else Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (venceu) {
                                        Text(
                                            " ✓",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MenuGold,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiddingPanel(view: PlayerView, onBid: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().background(Slate, RoundedCornerShape(12.dp))) {
        Column(Modifier.padding(14.dp)) {
            val blindOne = view.cardsThisRound == 1
            Text(
                when {
                    blindOne -> "Previsao as cegas: voce NAO ve sua carta, mas ve a dos outros."
                    view.blindNineCards -> "Rodada as cegas: voce nao ve a sua mao em momento nenhum."
                    else -> "Quantas vazas voce vai ganhar?"
                },
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Soma das previsoes ate agora: ${view.bidSum} de ${view.cardsThisRound}",
                fontSize = scaled(12.sp),
                color = inkDim(0.8f),
            )
            if (view.isMyTurn && view.forbiddenBid != null && view.forbiddenBid in 0..view.cardsThisRound &&
                !view.legalBids.contains(view.forbiddenBid)
            ) {
                Text(
                    "Voce e o ultimo: nao pode prever ${view.forbiddenBid} (a soma nao pode dar ${view.cardsThisRound}).",
                    fontSize = scaled(12.sp),
                    color = Color(0xFFFF8A80),
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
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    if (legal) SlateBright else Slate.copy(alpha = 0.45f),
                                    CircleShape,
                                )
                                .border(
                                    BorderStroke(
                                        if (legal) 2.dp else 1.dp,
                                        if (legal) MenuGold else Color(0x22FFFFFF),
                                    ),
                                    CircleShape,
                                )
                                .then(
                                    if (legal) Modifier.clickable { onBid(n) } else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$n",
                                fontWeight = FontWeight.Black,
                                fontSize = scaled(20.sp),
                                color = if (legal) Color.White else inkDim(0.35f),
                            )
                        }
                    }
                }
            } else {
                val who = view.currentPlayerId?.let { id -> view.players.first { it.id == id }.name }
                Text("Vez de $who prever...", color = inkDim(0.85f))
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
        fontSize = scaled(16.sp),
        color = MenuGold,
    )
    Text(
        "Recolhendo a mesa...",
        fontSize = scaled(12.sp),
        color = inkDim(0.8f),
    )
}

@Composable
private fun PlayingPanel(view: PlayerView) {
    val who = view.currentPlayerId?.let { id -> view.players.first { it.id == id }.name }
    Text(
        if (view.isMyTurn) "Sua vez: escolha uma carta" else "Vez de $who",
        fontWeight = FontWeight.SemiBold,
        color = if (view.isMyTurn) MenuGold else Ink,
    )
}

@Composable
private fun RoundOverPanel(view: PlayerView) {
    Box(Modifier.fillMaxWidth().background(Slate, RoundedCornerShape(12.dp))) {
        Column(Modifier.padding(14.dp)) {
            Text("Fim da rodada", fontWeight = FontWeight.Bold, fontSize = scaled(18.sp), color = Color.White)
            Spacer(Modifier.height(8.dp))
            view.lastRoundSummary.forEach { r ->
                val p = view.players.first { it.id == r.playerId }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(
                        "previu ${r.bid}, fez ${r.won}  →  -${r.livesLost}  (${r.livesAfter} vidas)" +
                            if (r.eliminated) "  ELIMINADO" else "",
                        fontSize = scaled(13.sp),
                        color = if (r.livesLost == 0) Color(0xFF7FE0A0) else Ink,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            NextRoundCountdown(view.roundIndex)
        }
    }
}

/**
 * Conta os segundos ate a proxima rodada. Quem de fato avanca e o host, no
 * mesmo lugar onde ja vivia a pausa da vaza; isto aqui so mostra a espera.
 *
 * Conta sozinho porque em ROUND_OVER nao chega acao nenhuma: a view do fim da
 * rodada cai no aparelho uma vez e fica parada ate a proxima comecar.
 */
@Composable
private fun NextRoundCountdown(roundIndex: Int) {
    var left by remember(roundIndex) { mutableIntStateOf(ROUND_OVER_SECONDS) }
    LaunchedEffect(roundIndex) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            if (left > 0) "Proxima rodada em ${left}s" else "Comecando...",
            color = MenuGold,
            fontWeight = FontWeight.SemiBold,
            fontSize = scaled(15.sp),
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (left / ROUND_OVER_SECONDS.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MenuGold,
            trackColor = SlateBright,
        )
    }
}

@Composable
private fun GameOverPanel(view: PlayerView, onLeave: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Slate, RoundedCornerShape(12.dp))) {
        Column(Modifier.padding(16.dp)) {
            Text("Fim de jogo", fontSize = scaled(22.sp), fontWeight = FontWeight.Black, color = MenuGold)
            Spacer(Modifier.height(10.dp))
            Text("Classificacao", fontWeight = FontWeight.SemiBold, color = Color.White)
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
                        color = if (i == 0) MenuGold else Ink,
                    )
                    Text("${p.lives} vidas", fontSize = scaled(13.sp), color = Ink)
                }
            }
            Spacer(Modifier.height(14.dp))
            MenuTile(
                modifier = Modifier.fillMaxWidth().height(58.dp),
                onClick = onLeave,
            ) { Text("Voltar ao inicio", color = Color.White, fontSize = scaled(18.sp)) }
        }
    }
}

/**
 * Minha mao. Cinco cartas por linha, o resto desce: com nove na mesma linha
 * so dava para ver as primeiras.
 *
 * Nas rodadas cegas aparece o verso: na de 1 carta porque a minha esta virada
 * para fora, na de 9 porque a rodada inteira e no escuro - eu escolho a
 * posicao e so descubro o que mandei quando a carta cai na mesa.
 */
@Composable
private fun HandArea(
    view: PlayerView,
    onPlay: (GameCard) -> Unit,
    onPlayBlind: () -> Unit,
    onPlayBlindAt: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Sua mao", fontWeight = FontWeight.SemiBold, fontSize = scaled(13.sp), color = Ink)
        Spacer(Modifier.height(6.dp))

        when {
            view.cardsThisRound == 1 && (view.handSizes[view.me] ?: 0) > 0 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Virada para fora: nao vejo a minha, mas sou eu quem joga.
                    CardBack(
                        onClick = if (view.canPlayBlind) onPlayBlind else null,
                        highlighted = view.canPlayBlind,
                    )
                    Text(
                        if (view.canPlayBlind) "Sua vez: toque na carta para joga-la."
                        else "Carta virada para fora:\nvoce nao ve a sua.",
                        fontSize = scaled(12.sp),
                        color = inkDim(0.9f),
                    )
                }
            }

            view.blindNineCards -> {
                val restantes = view.handSizes[view.me] ?: 0
                Text(
                    if (view.canPlayBlindAt) "Sua vez: escolha uma posicao. So a mesa dira qual carta era."
                    else "Rodada as cegas: a mao inteira fica no escuro.",
                    fontSize = scaled(12.sp),
                    color = inkDim(0.9f),
                )
                Spacer(Modifier.height(8.dp))
                CardGrid(restantes) { i ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CardBack(
                            onClick = if (view.canPlayBlindAt) ({ onPlayBlindAt(i) }) else null,
                            highlighted = view.canPlayBlindAt,
                        )
                        Text(
                            "${i + 1}",
                            fontSize = scaled(11.sp),
                            color = inkDim(0.7f),
                        )
                    }
                }
            }

            view.myHand.isEmpty() -> CardSlot()

            else -> CardGrid(view.myHand.size) { i ->
                val card = view.myHand[i]
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
}

/** Cinco por linha; a ultima linha alinha a esquerda, sem esticar carta. */
@Composable
private fun CardGrid(count: Int, item: @Composable (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until count).chunked(5).forEach { linha ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                linha.forEach { i -> item(i) }
            }
        }
    }
}
