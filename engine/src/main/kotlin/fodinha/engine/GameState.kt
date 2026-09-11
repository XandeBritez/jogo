package fodinha.engine

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: Int,
    val name: String,
    val isBot: Boolean = false,
    val lives: Int = 10,
) {
    val alive: Boolean get() = lives > 0
}

@Serializable
enum class Phase {
    /** Cartas distribuidas, aguardando previsoes. */
    BIDDING,

    /** Previsoes feitas, jogadores jogando cartas. */
    PLAYING,

    /**
     * Vaza completa, cartas ainda na mesa. Existe para todo mundo conseguir
     * ver o que foi jogado antes de a mesa ser recolhida. Ninguem joga nesta
     * fase; ela sai por `Engine.closeTrick`.
     */
    TRICK_REVEAL,

    /** Rodada acabou, vidas contabilizadas. Aguarda avanco para a proxima. */
    ROUND_OVER,

    /** Restou 1 (ou zero) jogador. Fim. */
    GAME_OVER,
}

/** Uma carta jogada na vaza corrente, na ordem em que entrou. */
@Serializable
data class Play(val playerId: Int, val card: Card)

@Serializable
data class GameState(
    val players: List<Player>,
    val roundIndex: Int = 0,
    val dealerIndex: Int = 0,
    val cardsThisRound: Int = 1,
    val turned: Card? = null,
    val hands: Map<Int, List<Card>> = emptyMap(),
    val bids: Map<Int, Int> = emptyMap(),
    /** Ordem de aposta/jogada da rodada: ids dos jogadores vivos comecando a esquerda do dealer. */
    val order: List<Int> = emptyList(),
    val currentSeat: Int = 0,
    val tricksWon: Map<Int, Int> = emptyMap(),
    val currentTrick: List<Play> = emptyList(),
    /** Quem levou a vaza que esta na mesa. So preenchido em TRICK_REVEAL. */
    val trickWinnerId: Int? = null,
    /** Quem lidera a vaza atual (indice em `order`). */
    val leadSeat: Int = 0,
    val trickIndex: Int = 0,
    val phase: Phase = Phase.BIDDING,
    val lastRoundSummary: List<RoundResult> = emptyList(),
    /** Ordem de eliminacao: primeiro eliminado primeiro. Vencedor entra por ultimo. */
    val eliminationOrder: List<Int> = emptyList(),
    val rngSeed: Long = 0L,
    val log: List<String> = emptyList(),
) {
    val manilha: Rank? get() = turned?.let { Rules.manilhaRank(it) }

    val activePlayers: List<Player> get() = players.filter { it.alive }

    val currentPlayerId: Int? get() = order.getOrNull(currentSeat)

    fun player(id: Int): Player = players.first { it.id == id }

    /** Rodada as cegas com a carta virada para fora: 1 carta, cada um ve a dos outros. */
    val isBlindOneCard: Boolean get() = cardsThisRound == 1

    /** Rodada de 9 cartas: aposta antes de olhar a mao. */
    val isBlindBidNineCards: Boolean get() = cardsThisRound == 9

    val bidsComplete: Boolean get() = order.all { bids.containsKey(it) }
}

@Serializable
data class RoundResult(
    val playerId: Int,
    val bid: Int,
    val won: Int,
    val livesLost: Int,
    val livesAfter: Int,
    val eliminated: Boolean,
)

@Serializable
sealed interface GameAction {
    val playerId: Int

    @Serializable
    data class Bid(override val playerId: Int, val amount: Int) : GameAction

    @Serializable
    data class PlayCard(override val playerId: Int, val card: Card) : GameAction

    /**
     * Joga a unica carta da mao sem nomea-la. Existe por causa da rodada cega
     * de 1 carta: o jogador manda a carta sem saber qual e, e o cliente nunca
     * precisa receber esse dado para poder jogar.
     */
    @Serializable
    data class PlayBlind(override val playerId: Int) : GameAction
}
