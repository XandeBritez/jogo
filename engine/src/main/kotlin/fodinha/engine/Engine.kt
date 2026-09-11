package fodinha.engine

/**
 * Toda a mecanica da rodada. Puro e deterministico dado o seed.
 * A UI e os transportes nunca reimplementam nada daqui.
 */
object Engine {

    const val STARTING_LIVES = 10

    fun newGame(names: List<Pair<String, Boolean>>, seed: Long = System.nanoTime()): GameState {
        require(names.size in 2..6) { "Fodinha e para 2 a 6 jogadores" }
        val players = names.mapIndexed { i, (name, isBot) ->
            Player(id = i, name = name, isBot = isBot, lives = STARTING_LIVES)
        }
        val base = GameState(players = players, rngSeed = seed, dealerIndex = 0)
        return startRound(base, roundIndex = 0)
    }

    /** Distribui, vira a carta e abre as apostas. */
    fun startRound(state: GameState, roundIndex: Int): GameState {
        val alive = state.players.filter { it.alive }
        if (alive.size <= 1) return finishGame(state)

        val scheduled = Rules.scheduledCards(roundIndex)
        val count = Rules.cardsThisRound(scheduled, alive.size)

        // Ordem: comeca a esquerda do dealer, so jogadores vivos.
        val aliveIds = alive.map { it.id }
        val dealerId = state.players.getOrNull(state.dealerIndex)?.id ?: aliveIds.first()
        val pivot = aliveIds.indexOf(dealerId)
        val order = List(aliveIds.size) { aliveIds[(pivot + 1 + it) % aliveIds.size] }

        val deck = Deck.shuffled(state.rngSeed + roundIndex * 7919L).toMutableList()
        val hands = HashMap<Int, List<Card>>()
        for (id in order) {
            hands[id] = List(count) { deck.removeAt(0) }.sortedByDescending { it.rank.ordinal }
        }
        val turned = deck.removeAt(0)

        return state.copy(
            roundIndex = roundIndex,
            cardsThisRound = count,
            turned = turned,
            hands = hands,
            bids = emptyMap(),
            order = order,
            currentSeat = 0,
            tricksWon = order.associateWith { 0 },
            currentTrick = emptyList(),
            leadSeat = 0,
            trickIndex = 0,
            phase = Phase.BIDDING,
            lastRoundSummary = emptyList(),
            log = state.log + "Rodada ${roundIndex + 1}: $count carta(s), virada $turned, manilha ${Rules.manilhaRank(turned).label}",
        )
    }

    /** Faixa de apostas legais para o jogador da vez. Vazia se nao for fase de previsao. */
    fun legalBids(state: GameState): Set<Int> {
        if (state.phase != Phase.BIDDING) return emptySet()
        val all = (0..state.cardsThisRound).toSet()
        val isLast = state.currentSeat == state.order.size - 1
        if (!isLast) return all
        val sumSoFar = state.bids.values.sum()
        val forbidden = state.cardsThisRound - sumSoFar
        return all - forbidden
    }

    /** Cartas que o jogador da vez pode jogar. Fodinha nao obriga seguir naipe. */
    fun legalPlays(state: GameState, playerId: Int): List<Card> {
        if (state.phase != Phase.PLAYING || state.currentPlayerId != playerId) return emptyList()
        return state.hands[playerId].orEmpty()
    }

    fun reduce(state: GameState, action: GameAction): GameState = when (action) {
        is GameAction.Bid -> applyBid(state, action)
        is GameAction.PlayCard -> applyPlay(state, action)
        is GameAction.PlayBlind -> {
            check(state.isBlindOneCard) { "so a rodada cega de 1 carta aceita jogada as cegas" }
            val only = state.hands[action.playerId]?.singleOrNull()
            checkNotNull(only) { "jogador ${action.playerId} nao tem exatamente uma carta" }
            applyPlay(state, GameAction.PlayCard(action.playerId, only))
        }
    }

    private fun applyBid(state: GameState, action: GameAction.Bid): GameState {
        check(state.phase == Phase.BIDDING) { "nao e fase de previsao" }
        check(state.currentPlayerId == action.playerId) { "nao e a vez de ${action.playerId}" }
        check(action.amount in legalBids(state)) {
            "previsao ilegal ${action.amount}: a soma nao pode dar ${state.cardsThisRound}"
        }

        val bids = state.bids + (action.playerId to action.amount)
        val name = state.player(action.playerId).name
        val next = state.copy(
            bids = bids,
            log = state.log + "$name previu ${action.amount}",
        )
        return if (bids.size == state.order.size) {
            next.copy(phase = Phase.PLAYING, currentSeat = 0, leadSeat = 0)
        } else {
            next.copy(currentSeat = state.currentSeat + 1)
        }
    }

    private fun applyPlay(state: GameState, action: GameAction.PlayCard): GameState {
        check(state.phase == Phase.PLAYING) { "nao e fase de jogada" }
        check(state.currentPlayerId == action.playerId) { "nao e a vez de ${action.playerId}" }
        val hand = state.hands[action.playerId].orEmpty()
        check(action.card in hand) { "carta ${action.card} nao esta na mao" }

        val hands = state.hands + (action.playerId to hand.minusFirst(action.card))
        val trick = state.currentTrick + Play(action.playerId, action.card)
        val name = state.player(action.playerId).name
        var next = state.copy(
            hands = hands,
            currentTrick = trick,
            log = state.log + "$name jogou ${action.card}",
        )

        if (trick.size < state.order.size) {
            return next.copy(currentSeat = (state.currentSeat + 1) % state.order.size)
        }

        // Vaza completa: decide quem levou, mas NAO recolhe a mesa. As cartas
        // ficam expostas em TRICK_REVEAL ate `closeTrick`, para todo mundo ver.
        // `trick` ja esta na ordem real de jogada, entao o desempate pela
        // primeira carta sai de graca.
        val manilha = Rules.manilhaRank(state.turned!!)
        val winnerIdx = Rules.trickWinnerIndex(trick.map { it.card }, manilha)
        val winnerId = trick[winnerIdx].playerId
        val tricks = next.tricksWon + (winnerId to (next.tricksWon[winnerId] ?: 0) + 1)

        return next.copy(
            tricksWon = tricks,
            trickWinnerId = winnerId,
            phase = Phase.TRICK_REVEAL,
            log = next.log + "${state.player(winnerId).name} ganhou a vaza",
        )
    }

    /**
     * Recolhe a mesa depois da pausa de exibicao. O vencedor sai na proxima
     * vaza; se ninguem tem mais carta, a rodada e contabilizada.
     */
    fun closeTrick(state: GameState): GameState {
        check(state.phase == Phase.TRICK_REVEAL) { "nao ha vaza exposta para recolher" }
        val winnerId = checkNotNull(state.trickWinnerId) { "vaza sem vencedor" }
        val winnerSeat = state.order.indexOf(winnerId)

        val next = state.copy(
            currentTrick = emptyList(),
            trickWinnerId = null,
            trickIndex = state.trickIndex + 1,
            leadSeat = winnerSeat,
            currentSeat = winnerSeat,
            phase = Phase.PLAYING,
        )

        val allEmpty = next.hands.values.all { it.isEmpty() }
        return if (allEmpty) scoreRound(next) else next
    }

    /** Fecha a rodada: |previsao - vazas| = vidas perdidas. */
    private fun scoreRound(state: GameState): GameState {
        val results = state.order.map { id ->
            val bid = state.bids[id] ?: 0
            val won = state.tricksWon[id] ?: 0
            val lost = kotlin.math.abs(bid - won)
            val before = state.player(id).lives
            val after = (before - lost).coerceAtLeast(0)
            RoundResult(
                playerId = id,
                bid = bid,
                won = won,
                livesLost = lost,
                livesAfter = after,
                eliminated = after == 0,
            )
        }

        val players = state.players.map { p ->
            results.firstOrNull { it.playerId == p.id }?.let { p.copy(lives = it.livesAfter) } ?: p
        }

        // Eliminados nesta rodada: quem tinha menos vidas ANTES da rodada cai primeiro no ranking.
        val newlyDead = results.filter { it.eliminated }
            .sortedBy { state.player(it.playerId).lives }
            .map { it.playerId }

        val logLines = results.map { r ->
            "${state.player(r.playerId).name}: previu ${r.bid}, fez ${r.won}, -${r.livesLost} vida(s), resta ${r.livesAfter}"
        }

        val next = state.copy(
            players = players,
            phase = Phase.ROUND_OVER,
            lastRoundSummary = results,
            eliminationOrder = state.eliminationOrder + newlyDead,
            log = state.log + logLines,
        )

        val survivors = players.count { it.alive }
        return if (survivors <= 1) finishGame(next) else next
    }

    private fun finishGame(state: GameState): GameState {
        val survivors = state.players.filter { it.alive }
        val ranking = state.eliminationOrder + survivors.map { it.id }
        val winnerText = when {
            survivors.size == 1 -> "${survivors.first().name} venceu!"
            survivors.isEmpty() -> {
                // Todos zeraram na mesma rodada: vence quem tinha mais vidas antes
                // (o ultimo da ordem de eliminacao, que ja foi ordenada por vidas anteriores).
                val sharedWinner = state.eliminationOrder.lastOrNull()?.let { state.player(it).name }
                if (sharedWinner != null) "$sharedWinner venceu no desempate por vidas" else "Empate"
            }
            else -> "Fim de jogo"
        }
        return state.copy(
            phase = Phase.GAME_OVER,
            eliminationOrder = ranking,
            log = state.log + winnerText,
        )
    }

    /** Avanca de ROUND_OVER para a proxima rodada, rotacionando o dealer. */
    fun nextRound(state: GameState): GameState {
        if (state.phase == Phase.GAME_OVER) return state
        check(state.phase == Phase.ROUND_OVER) { "rodada ainda nao acabou" }
        val nextDealer = nextAliveDealer(state)
        return startRound(state.copy(dealerIndex = nextDealer), state.roundIndex + 1)
    }

    private fun nextAliveDealer(state: GameState): Int {
        val n = state.players.size
        for (step in 1..n) {
            val idx = (state.dealerIndex + step) % n
            if (state.players[idx].alive) return idx
        }
        return state.dealerIndex
    }

    private fun List<Card>.minusFirst(card: Card): List<Card> {
        val i = indexOf(card)
        return if (i < 0) this else toMutableList().also { it.removeAt(i) }
    }
}
