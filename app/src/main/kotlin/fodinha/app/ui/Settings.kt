package fodinha.app.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Desenho do verso das cartas. O nome e o que aparece no dialogo de escolha. */
enum class DeckBack(val label: String, val base: Color, val ink: Color) {
    VERDE("Verde", Color(0xFF2E9E5B), Color(0xFFEAFBF0)),
    VERMELHO("Vermelho", Color(0xFFD93A3A), Color(0xFFFFECEC)),
    AZUL("Azul", Color(0xFF3E4BC8), Color(0xFFE9EBFF)),
    PRETO("Preto", Color(0xFF1C1C1C), Color(0xFF4A4A4A)),
    FELTRO("Feltro", Color(0xFF0E4A32), Color(0xFFD9A441)),
}

/** Tema da mesa. SISTEMA segue o modo escuro do Android. */
enum class ThemeMode(val label: String) {
    SISTEMA("Seguir o sistema"),
    CLARO("Claro"),
    ESCURO("Escuro"),
}

/** Escala da fonte dos baloes, botoes e perguntas. */
enum class TextScale(val label: String, val factor: Float) {
    NORMAL("Normal", 1.0f),
    GRANDE("Grande", 1.18f),
    ENORME("Enorme", 1.35f),
}

/**
 * Preferencias visuais. Vivem fora do estado de jogo: mudar qualquer uma
 * nao toca na Engine, so em como a carta e desenhada.
 */
data class GameSettings(
    val deckBack: DeckBack = DeckBack.VERDE,
    val cardColor: Color = Color(0xFFFDFBF4),
    val textScale: TextScale = TextScale.NORMAL,
    val fastAnimation: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SISTEMA,
    /**
     * Alto contraste: escurece a mesa, tira a transparencia do texto
     * secundario, engrossa as bordas e manda a carta de volta ao branco,
     * ignorando a cor escolhida.
     */
    val highContrast: Boolean = false,
)

/** Paleta do dialogo "Selecionar uma cor", na ordem do print. */
val CardColorChoices: List<Color> = listOf(
    Color(0xFFFDFBF4), Color(0xFFF44336), Color(0xFFE91E63), Color(0xFFFF2D87), Color(0xFF9C27B0),
    Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
    Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39), Color(0xFFFFEB3B),
    Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFF795548), Color(0xFF607D8B), Color(0xFFBDBDBD),
)

/**
 * Guarda em SharedPreferences. Sem DataStore de proposito: nao vale uma
 * dependencia nova para quatro chaves.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("fodinha.settings", Context.MODE_PRIVATE)

    fun load(): GameSettings = GameSettings(
        deckBack = runCatching { DeckBack.valueOf(prefs.getString(KEY_DECK, null) ?: "") }
            .getOrDefault(DeckBack.VERDE),
        cardColor = Color(prefs.getInt(KEY_COLOR, 0xFFFDFBF4.toInt())),
        textScale = runCatching { TextScale.valueOf(prefs.getString(KEY_SCALE, null) ?: "") }
            .getOrDefault(TextScale.NORMAL),
        fastAnimation = prefs.getBoolean(KEY_FAST, false),
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SISTEMA),
        highContrast = prefs.getBoolean(KEY_CONTRAST, false),
    )

    fun save(s: GameSettings) {
        prefs.edit()
            .putString(KEY_DECK, s.deckBack.name)
            .putInt(KEY_COLOR, s.cardColor.toArgb())
            .putString(KEY_SCALE, s.textScale.name)
            .putBoolean(KEY_FAST, s.fastAnimation)
            .putString(KEY_THEME, s.themeMode.name)
            .putBoolean(KEY_CONTRAST, s.highContrast)
            .apply()
    }

    fun savePlayerName(name: String) = prefs.edit().putString(KEY_NAME, name).apply()

    fun playerName(): String = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: "Voce"

    private companion object {
        const val KEY_DECK = "deck"
        const val KEY_COLOR = "cardColor"
        const val KEY_SCALE = "textScale"
        const val KEY_FAST = "fastAnim"
        const val KEY_THEME = "themeMode"
        const val KEY_CONTRAST = "highContrast"
        const val KEY_NAME = "playerName"
    }
}

/**
 * Repassado por CompositionLocal em vez de parametro: a carta e desenhada em
 * uns dez lugares e nenhum deles quer carregar preferencia visual na assinatura.
 */
val LocalGameSettings = staticCompositionLocalOf { GameSettings() }
