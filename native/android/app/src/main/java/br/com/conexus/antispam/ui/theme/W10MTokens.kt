package br.com.conexus.antispam.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * W10M Design System Tokens — Port 1:1 do Conexus Launcher (ui/Theme.kt, Accent.kt, Type.kt, Motion.kt)
 * Telas e componentes NÃO definem cores, tamanhos de fonte, raios ou durações próprias.
 * Usam `W10MTheme` (tema do sistema, escuro por padrão) via `LocalW10MTheme.current`.
 */

// ============================================================
// CORES ESSENCIAIS W10M (Accent.kt)
// ============================================================

object W10MEssentials {
    const val VERMELHO = "#E51400"      // danger
    const val LARANJA = "#FA6800"       // warn
    const val AMARELO = "#E3C800"       // amarelo (score médio)
    const val VERDE = "#60A917"         // accent padrão / sucesso
    const val CIANO = "#1BA1E2"         // info
    const val AZUL = "#0078D7"          // azul
    const val ROXO = "#AA00FF"          // roxo
    const val ROSA = "#E671B8"          // rosa
    const val CINZA = "#647687"         // mute
    const val MARROM = "#825A2C"        // marrom
}

// Destaque padrão do app: brand primary do AntiSpam BR (fleet.yml, família Conexus)
const val DEFAULT_ACCENT = "#DC2626"

// Extras para faixas de score
const val AMBAR = "#F0A30A"

/**
 * Converte hex para RGB
 */
fun Color.toRgb(): IntArray {
    val value = this.toArgb()
    return intArrayOf(
        (value shr 16) and 0xFF,
        (value shr 8) and 0xFF,
        value and 0xFF
    )
}

fun IntArray.toColor(): Color = Color(this[0], this[1], this[2])

fun String.toColor(): Color = Color(android.graphics.Color.parseColor(this))

/**
 * Luminância relativa WCAG
 */
fun Color.wcagLuminance(): Float {
    val rgb = toRgb()
    fun channel(v: Int): Float {
        val c = v / 255f
        return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
    val (r, g, b) = rgb
    return 0.2126f * channel(r) + 0.7152f * channel(g) + 0.0722f * channel(b)
}

/**
 * Razão de contraste WCAG entre duas cores opacas
 */
fun Color.contrast(other: Color): Float {
    val l1 = this.wcagLuminance()
    val l2 = other.wcagLuminance()
    return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
}

fun Color.mix(other: Color, amount: Float): Color {
    val a = this.toRgb()
    val b = other.toRgb()
    return (0..2).map { i ->
        (a[i] * (1 - amount) + b[i] * amount).toInt()
    }.toIntArray().toColor()
}

fun Color.scale(factor: Float): Color {
    return toRgb().map { (it * factor).toInt() }.toIntArray().toColor()
}

/**
 * Ajusta `color` até passar 4.5:1 como texto/linha sobre `background`:
 * clareia no fundo escuro, escurece no claro (Accent.forBackground)
 */
fun Color.forBackground(background: Color): Color {
    var current = this
    val dark = background.wcagLuminance() < 0.5f
    repeat(40) {
        if (current.contrast(background) >= 4.5f) return current
        current = if (dark) current.mix(Color.White, 0.1f) else current.scale(0.92f)
    }
    return current
}

/**
 * Escurece até texto branco passar 4.5:1 (Accent.forWhiteText): preenchimentos
 */
fun Color.forWhiteText(): Color {
    var current = this
    repeat(40) {
        if (current.contrast(Color.White) >= 4.5f) return current
        current = current.scale(0.95f)
    }
    return current
}

// ============================================================
// PALETA COMPLETA (Theme.kt)
// ============================================================

data class W10MPalette(
    val dark: Boolean,
    val bg: Color,                    // Fundo da página
    val chrome: Color,                // Superfície elevada: barras, menus, diálogos
    val surface: Color,               // Superfície: botão secundário pressionado, trilhos
    val text: Color,                  // Texto principal
    val textDim: Color,               // Texto secundário (muted)
    val border: Color,                // Divisória / borda discreta
    val disabled: Color,              // Desabilitado
    val accent: Color,                // Destaque cru
    val primary: Color,               // Destaque ajustado para texto/linha sobre bg (>=4.5:1)
    val accentFill: Color,            // Destaque escurecido para texto branco por cima (botão primário, pino ligado)
    val onAccent: Color,              // Texto sobre accentFill
    val danger: Color,                // Semânticas ajustadas ao fundo (texto/linha)
    val warn: Color,
    val info: Color,
    val mute: Color,
    val dangerFill: Color,            // Preenchimentos semânticos para texto branco por cima
    val warnFill: Color,
    val scrim: Color,                 // Fundo do diálogo/menu escurecido (60%)
    val fieldFocusBg: Color,          // Campo de texto em foco (sempre branco com texto preto)
    val fieldFocusText: Color,
    val fieldFocusPlaceholder: Color,
) {
    // Cores de ação (scoreColor/actionColor semânticas)
    fun scoreColor(score: Int): Color = when {
        score >= 81 -> danger
        score >= 61 -> warn
        score >= 41 -> AMBAR.toColor().forBackground(bg)
        score >= 21 -> W10MEssentials.AMARELO.toColor().forBackground(bg)
        else -> primary
    }

    fun actionColor(action: String): Color = when (action) {
        "BLOCK" -> danger
        "SILENCE" -> mute
        "WARN" -> warn
        else -> primary
    }
}

private val DARK_BG = Color(0xFF000000)
private val DARK_CHROME = Color(0xFF1A1A1A)
private val DARK_SURFACE = Color(0xFF2B2B2B)
private val DARK_TEXT = Color(0xFFFFFFFF)
private val DARK_TEXT_DIM = Color(0xFFCCCCCC)
private val DARK_BORDER = Color(0xFF333333)
private val DARK_DISABLED = Color(0xFF666666)

private val LIGHT_BG = Color(0xFFFFFFFF)
private val LIGHT_CHROME = Color(0xFFF2F2F2)
private val LIGHT_SURFACE = Color(0xFFE6E6E6)
private val LIGHT_TEXT = Color(0xFF000000)
private val LIGHT_TEXT_DIM = Color(0xFF505050)
private val LIGHT_BORDER = Color(0xFFCCCCCC)
private val LIGHT_DISABLED = Color(0xFF999999)

fun buildW10MPalette(dark: Boolean, accentHex: String = DEFAULT_ACCENT): W10MPalette {
    val accent = Color(android.graphics.Color.parseColor(accentHex))
    val bg = if (dark) DARK_BG else LIGHT_BG
    val on = { c: Color -> c.forBackground(bg) }
    
    return W10MPalette(
        dark = dark,
        bg = bg,
        chrome = if (dark) DARK_CHROME else LIGHT_CHROME,
        surface = if (dark) DARK_SURFACE else LIGHT_SURFACE,
        text = if (dark) DARK_TEXT else LIGHT_TEXT,
        textDim = if (dark) DARK_TEXT_DIM else LIGHT_TEXT_DIM,
        border = if (dark) DARK_BORDER else LIGHT_BORDER,
        disabled = if (dark) DARK_DISABLED else LIGHT_DISABLED,
        accent = accent,
        primary = on(accent),
        accentFill = accent.forWhiteText(),
        onAccent = Color.White,
        danger = on(W10MEssentials.VERMELHO.toColor()),
        warn = on(W10MEssentials.LARANJA.toColor()),
        info = on(W10MEssentials.CIANO.toColor()),
        mute = on(W10MEssentials.CINZA.toColor()),
        dangerFill = W10MEssentials.VERMELHO.toColor().forWhiteText(),
        warnFill = W10MEssentials.LARANJA.toColor().forWhiteText(),
        scrim = Color(0x99000000), // 60% black
        fieldFocusBg = Color.White,
        fieldFocusText = Color.Black,
        fieldFocusPlaceholder = Color(0xFF666666),
    )
}

// Cache de paletas por (dark, accent)
private val paletteCache = mutableMapOf<String, W10MPalette>()

fun getW10MPalette(dark: Boolean, accentHex: String = DEFAULT_ACCENT): W10MPalette {
    val key = "${if (dark) "dark" else "light"}|$accentHex"
    return paletteCache.getOrPut(key) { buildW10MPalette(dark, accentHex) }
}

// ============================================================
// TIPOGRAFIA (Type.kt) — Fonte do sistema; Segoe UI não embarcada
// Tamanhos em dp escaláveis; o Android aplica a escala de fonte do sistema
// ============================================================

data class W10MType(
    val display: TextStyle = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Light, lineHeight = 72.sp),
    val header: TextStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Light, lineHeight = 42.sp),
    val title: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Light, lineHeight = 34.sp),
    val subtitle: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    val group: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    val kicker: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, letterSpacing = 0.72.sp),
) {
    companion object {
        val Default = W10MType()
    }

    operator fun get(variant: TypeVariant): TextStyle = variant(this)
}

typealias TypeVariant = W10MType.() -> TextStyle

// ============================================================
// ESPAÇAMENTO, ALVOS, FORMA, MOVIMENTO
// ============================================================

object W10MSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val gutter = 16.dp  // Margem lateral das páginas
    val touchTarget = 44.dp
    val strokeWidth = 2.dp
    val radius = 0.dp   // W10M: flat and square
}

object W10MMotion {
    const val quickMs = 100      // interruptor
    const val fastMs = 167
    const val pageMs = 250       // entrada de página: sobe 20dp + aparece; diálogo/menu
    val pageRise = 20.dp
    const val pressScale = 0.98f
}

// ============================================================
// TEMA COMPLETO
// ============================================================

data class W10MTheme(
    val palette: W10MPalette,
    val type: W10MType = W10MType.Default,
    val spacing: W10MSpacing = W10MSpacing,
    val motion: W10MMotion = W10MMotion,
) {
    val radius = W10MSpacing.radius
    fun space(n: Int) = (n * 8).dp
}

val LocalW10MTheme = staticCompositionLocalOf {
    W10MTheme(palette = getW10MPalette(true))
}

@Composable
fun provideW10MTheme(dark: Boolean, accentHex: String = DEFAULT_ACCENT, content: @Composable () -> Unit) {
    val theme = remember(dark, accentHex) {
        W10MTheme(palette = getW10MPalette(dark, accentHex))
    }
    CompositionLocalProvider(LocalW10MTheme provides theme, content = content)
}

@Composable
fun useW10MTheme(): W10MTheme = LocalW10MTheme.current