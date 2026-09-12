package fodinha.engine

import kotlinx.serialization.Serializable

/**
 * O que UM jogador pode ver. E isto que o host manda pela rede, nunca o GameState inteiro:
 * mao alheia jamais atravessa o socket.
 */
@Serializable
data class PlayerView(
    val me: Int,
    val players: List<Player>,
    val roundIndex: Int,
    val cardsThisRound: Int,
    val turned: Card?,
    val manilhaLabel: String?,
    /** Minha mao. Vazia nas rodadas cegas: a de 1 carta e a de 9. */
    val myHand: List<Card>,
    /** Maos visiveis dos outros: so preenchido na rodada cega de 1 carta. */
    val revealedHands: Map<Int, List<Card>>,
    /** Quantas cartas cada jogador ainda tem na mao. */
    val handSizes: Map<Int, Int>,
    val bids: Map<Int, Int>,
    val bidSum: Int,
    val order: List<Int>,
    val currentPlayerId: Int?,
    val tricksWon: Map<Int, Int>,
    val currentTrick: List<Play>,
    /** Quem levou a vaza que esta exposta na mesa. Null fora de TRICK_REVEAL. */
    val trickWinnerId: Int?,
    val trickIndex: Int,
    val phase: Phase,
    val legalBids: List<Int>,
    val legalPlays: List<Card>,
    /**
     * Posso mandar a carta as cegas? So na rodada de 1 carta, na minha vez:
     * nao sei qual e, mas sou eu que aperto.
     */
    val canPlayBlind: Boolean,
    /**
     * Posso mandar carta por posicao? So na rodada de 9, na minha vez: escolho
     * um lugar na mao e descubro o que era quando a carta cai na mesa.
     */
    val canPlayBlindAt: Boolean,
    /**
     * Segundos que restam para o jogador da vez, no instante em que o host
     * mandou esta view. O cliente conta para baixo a partir daqui, para nao
     * depender dos relogios dos dois aparelhos baterem.
     */
    val turnSecondsLeft: Int?,
    /** Rodada de 9 cartas: cega do comeco ao fim, aposta e jogada. */
    val blindNineCards: Boolean,
    val lastRoundSummary: List<RoundResult>,
    val eliminationOrder: List<Int>,
    val log: List<String>,
) {
    val isMyTurn: Boolean get() = currentPlayerId == me
    val forbiddenBid: Int? get() = if (phase == Phase.BIDDING) cardsThisRound - bidSum else null
}

fun GameState.viewFor(playerId: Int, turnSecondsLeft: Int? = null): PlayerView {
    val blindOne = isBlindOneCard
    val blindNine = isBlindNineCards

    val myHand = when {
        blindOne -> emptyList()          // nao vejo a minha carta
        blindNine -> emptyList()         // nao vejo nenhuma, nem na hora de jogar
        else -> hands[playerId].orEmpty()
    }

    val revealed = if (blindOne) {
        hands.filterKeys { it != playerId }
    } else {
        emptyMap()
    }

    return PlayerView(
        me = playerId,
        players = players,
        roundIndex = roundIndex,
        cardsThisRound = cardsThisRound,
        turned = turned,
        manilhaLabel = manilha?.label,
        myHand = myHand,
        revealedHands = revealed,
        handSizes = hands.mapValues { it.value.size },
        bids = bids,
        bidSum = bids.values.sum(),
        order = order,
        currentPlayerId = currentPlayerId,
        tricksWon = tricksWon,
        currentTrick = currentTrick,
        trickWinnerId = trickWinnerId,
        trickIndex = trickIndex,
        phase = phase,
        legalBids = if (currentPlayerId == playerId) Engine.legalBids(this).sorted() else emptyList(),
        // A Engine continua validando contra a mao de verdade; o que sai daqui
        // e so o que o aparelho tem direito de ver.
        legalPlays = if (blindOne || blindNine) emptyList() else Engine.legalPlays(this, playerId),
        canPlayBlind = blindOne && phase == Phase.PLAYING && currentPlayerId == playerId,
        canPlayBlindAt = blindNine && phase == Phase.PLAYING && currentPlayerId == playerId,
        turnSecondsLeft = turnSecondsLeft,
        blindNineCards = blindNine,
        lastRoundSummary = lastRoundSummary,
        eliminationOrder = eliminationOrder,
        // O painel lateral mostra o historico, entao vale mandar mais que umas
        // poucas linhas. Nao e ilimitado de proposito: esta view sai inteira a
        // cada acao, e por Bluetooth isso pesa.
        log = log.takeLast(120),
    )
}
