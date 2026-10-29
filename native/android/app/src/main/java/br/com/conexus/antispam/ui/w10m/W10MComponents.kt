@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package br.com.conexus.antispam.ui.w10m

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import br.com.conexus.antispam.ui.theme.TypeVariant
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ui.theme.W10MEssentials
import br.com.conexus.antispam.ui.theme.W10MMotion
import br.com.conexus.antispam.ui.theme.W10MSpacing
import br.com.conexus.antispam.ui.theme.W10MTheme
import br.com.conexus.antispam.ui.theme.forWhiteText
import br.com.conexus.antispam.ui.theme.useW10MTheme
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import kotlinx.coroutines.launch

typealias W10MType = br.com.conexus.antispam.ui.theme.W10MType

// ============================================================
// W10M BUTTON — Retângulo plano
// secondary = borda 2dp; primary = accentFill; danger = dangerFill; text = link no accent
// Pressionado inverte para accentFill com texto branco (primary inverte para bg)
// Desabilitado = 0.4 opacity; Carregando = pontos W10M na cor do rótulo
// ============================================================

@Composable
fun W10MButton(
    label: String,
    onClick: (() -> Unit)? = null,
    variant: W10MButtonVariant = W10MButtonVariant.Secondary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    size: W10MButtonSize = W10MButtonSize.Normal,
    block: Boolean = false,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val inactive = !enabled || loading
    
    val (pair, textColor) = when (variant) {
        W10MButtonVariant.Text -> {
            val bg = if (pressed.value) palette.surface else Color.Transparent
            val border = Color.Transparent
            val fg = palette.primary
            bg to border to fg
        }
        W10MButtonVariant.Primary -> {
            if (pressed.value) palette.text to palette.text to palette.bg
            else palette.accentFill to palette.accentFill to palette.onAccent
        }
        W10MButtonVariant.Danger -> {
            if (pressed.value) palette.text to palette.text to palette.bg
            else palette.dangerFill to palette.dangerFill to palette.onAccent
        }
        else -> { // Secondary
            if (pressed.value) palette.accentFill to palette.accentFill to palette.onAccent
            else Color.Transparent to palette.text to palette.text
        }
    }
    val (bgColor, borderColor) = pair
    
    val minHeight = if (size == W10MButtonSize.Small) 36.dp else (W10MSpacing.touchTarget - 4.dp)
    val horizontalPadding = if (size == W10MButtonSize.Small) W10MSpacing.s else W10MSpacing.m
    val gap = if (size == W10MButtonSize.Small) W10MSpacing.xs else W10MSpacing.s
    val textStyle = if (size == W10MButtonSize.Small) theme.type.caption else theme.type.body
    
    Box(
        modifier = modifier
            .then(if (block) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = minHeight)
            .padding(horizontal = horizontalPadding, vertical = W10MSpacing.xs)
            .border(W10MSpacing.strokeWidth, if (variant == W10MButtonVariant.Text) Color.Transparent else borderColor, RectangleShape)
            .background(if (inactive && !loading) bgColor.copy(alpha = bgColor.alpha * 0.4f) else bgColor) // copy(alpha=1f) tornava o Transparent em preto
            // Um único detector de toque: um pointerInput(detectTapGestures) separado consumia o toque
            // e o onClick nunca disparava (botões mortos em toque real).
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = !inactive,
                onClick = onClick ?: {},
                onLongClick = null
            ),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            W10MProgress(color = textColor, size = 48.dp)
        } else {
            Row(
                // fillMaxWidth só em botão "block": senão, como trailing de lista, comia a largura do título
                modifier = if (block) Modifier.fillMaxWidth() else Modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                icon?.invoke()
                if (icon != null) androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(gap))
                Text(
                    text = label,
                    style = textStyle.copy(color = textColor.copy(alpha = if (inactive) 0.4f else 1f))
                )
            }
        }
    }
}

enum class W10MButtonVariant { Primary, Secondary, Danger, Text }
enum class W10MButtonSize { Normal, Small }

// ============================================================
// W10M PROGRESS — 5 pontos correndo (indeterminado) ou barra plana 4dp (determinado)
// ============================================================

@Composable
fun W10MProgress(
    color: Color = useW10MTheme().palette.onAccent,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    progress: Float? = null, // null = indeterminado
    contentDescription: String? = null,
) {
    val theme = useW10MTheme()
    if (progress != null) {
        // Determinado: barra plana 4dp
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(theme.palette.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(color)
            )
        }
    } else {
        // Indeterminado: 5 pontos correndo
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val offsets = (0..4).map { i ->
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(1000, delayMillis = i * 100, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                ),
                label = "progress-$i"
            )
        }
        
        Row(
            modifier = modifier.size(size),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            offsets.forEach { offset ->
                val x = offset.value * (size.value - 8.dp.value)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { translationX = x }
                        .background(color, RectangleShape)
                )
            }
        }
    }
}

// ============================================================
// W10M TEXT — Com variante type e tone
// ============================================================

@Composable
fun W10MText(
    text: String,
    variant: TypeVariant = W10MType::body,
    tone: W10MTextTone = W10MTextTone.Text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign = TextAlign.Start,
    overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    val style = theme.type[ variant]
    val color = when (tone) {
        W10MTextTone.Text -> palette.text
        W10MTextTone.Muted -> palette.textDim
        W10MTextTone.Accent -> palette.primary
        W10MTextTone.Danger -> palette.danger
        W10MTextTone.Warn -> palette.warn
        W10MTextTone.Info -> palette.info
        W10MTextTone.Mute -> palette.mute
    }
    
    Text(
        text = if (variant == W10MType::kicker) text.uppercase() else text,
        color = color,
        fontSize = style.fontSize,
        fontWeight = style.fontWeight,
        lineHeight = style.lineHeight,
        letterSpacing = style.letterSpacing,
        modifier = modifier,
        maxLines = maxLines,
        textAlign = textAlign,
        overflow = overflow,
    )
}

enum class W10MTextTone { Text, Muted, Accent, Danger, Warn, Info, Mute }

// ============================================================
// W10M SCREEN — Scaffold com fundo, safe area, entrada de página
// ============================================================

@Composable
fun W10MScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        content()
    }
}

// ============================================================
// W10M HEADER — Kicker + título grande leve à esquerda, subtítulo, até 2 ações de ícone
// ============================================================

@Composable
fun W10MHeader(
    kicker: String? = null,
    title: String,
    subtitle: String? = null,
    actions: List<W10MHeaderAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    
    Column(modifier = modifier.padding(horizontal = W10MSpacing.gutter).padding(top = W10MSpacing.s)) {
        if (kicker != null || actions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                kicker?.let {
                    W10MText(text = it, variant = W10MType::kicker, tone = W10MTextTone.Accent)
                }
                actions.forEachIndexed { index, action ->
                    if (index == actions.lastIndex) {
                        W10MIconButton(
                            icon = action.icon,
                            onClick = action.onClick,
                            contentDescription = action.contentDescription,
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.xs))
        }
        W10MText(text = title, variant = W10MType::header, tone = W10MTextTone.Text)
        subtitle?.let {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            W10MText(text = it, variant = W10MType::subtitle, tone = W10MTextTone.Muted)
        }
    }
}

data class W10MHeaderAction(
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val contentDescription: String,
)

// ============================================================
// W10M ICON BUTTON — Botão só de ícone 44dp (exceção: círculo)
// ============================================================

@Composable
fun W10MIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    val pressed = remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .size(W10MSpacing.touchTarget)
            .background(Color.Transparent)
            .clip(RoundedCornerShape(50.dp)) // Círculo - exceção W10M
            .border(1.5.dp, palette.text.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
            .combinedClickable(
                onClick = { 
                    pressed.value = true
                    onClick()
                    pressed.value = false
                },
                onLongClick = null
            )
            .graphicsLayer {
                scaleX = if (pressed.value) 0.95f else 1f
                scaleY = if (pressed.value) 0.95f else 1f
            }
    ) {
        icon()
    }
}

// ============================================================
// W10M TOGGLE / SWITCH — Trilho retangular 44×20 com borda, pino quadrado
// ============================================================

@Composable
fun W10MToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    description: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    val pressed = remember { mutableStateOf(false) }
    
    val trackColor = if (checked) palette.accentFill else palette.border
    val thumbColor = if (checked) palette.onAccent else palette.text
    val borderColor = if (checked) palette.accentFill else palette.border
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = W10MSpacing.xs)
            .combinedClickable(
                onClick = { if (enabled) onCheckedChange(!checked) },
                onLongClick = null
            )
            .padding(horizontal = W10MSpacing.gutter)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp, 20.dp)
                .background(trackColor.copy(alpha = if (enabled) 1f else 0.4f))
                .border(W10MSpacing.strokeWidth, borderColor.copy(alpha = if (enabled) 1f else 0.4f), RectangleShape)
                .graphicsLayer {
                    scaleX = if (pressed.value) 0.98f else 1f
                    scaleY = if (pressed.value) 0.98f else 1f
                }
        ) {
            // Thumb quadrado
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = if (checked) 24.dp else 4.dp)
                    .background(thumbColor, RectangleShape)
                    .padding(2.dp) // Borda interna para pino quadrado
            )
        }
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.m))
        
        Column {
            W10MText(text = label, variant = W10MType::body, tone = W10MTextTone.Text)
            description?.let {
                W10MText(text = it, variant = W10MType::caption, tone = W10MTextTone.Muted)
            }
        }
    }
}

// ============================================================
// W10M TEXT FIELD — Rótulo acima, caixa quadrada com borda 2dp
// Em foco: fundo branco com texto preto + borda accent
// ============================================================

@Composable
fun W10MTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    secure: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    val focusRequester = remember { FocusRequester() }
    val focused = remember { mutableStateOf(false) }
    val hasError = errorText != null && errorText.isNotBlank()
    val textFieldValue = remember { mutableStateOf(TextFieldValue(value)) }
    // Mudança externa do valor (ex.: limpar o campo após adicionar) precisa refletir no campo.
    if (textFieldValue.value.text != value) {
        textFieldValue.value = TextFieldValue(value, TextRange(value.length))
    }
    
    val borderColor = when {
        hasError -> palette.danger
        focused.value -> palette.accent
        else -> palette.border
    }
    val bgColor = if (focused.value) palette.fieldFocusBg else palette.surface
    val textColor = if (focused.value) palette.fieldFocusText else palette.text
    val placeholderColor = if (focused.value) palette.fieldFocusPlaceholder else palette.textDim
    val visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = W10MSpacing.gutter)
    ) {
        W10MText(text = label, variant = W10MType::body, tone = W10MTextTone.Text)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.xs))
        
        BasicTextField(
            value = textFieldValue.value,
            onValueChange = { newValue ->
                textFieldValue.value = newValue
                onValueChange(newValue.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RectangleShape)
                .border(W10MSpacing.strokeWidth, borderColor, RectangleShape)
                .heightIn(min = W10MSpacing.touchTarget)
                .padding(horizontal = W10MSpacing.m, vertical = W10MSpacing.s)
                .onFocusChanged { focusState ->
                    focused.value = focusState.isFocused
                },
            textStyle = theme.type.body.copy(color = textColor),
            decorationBox = { innerTextField ->
                Box {
                    if (textFieldValue.value.text.isEmpty()) {
                        placeholder?.let {
                            Text(text = it, color = placeholderColor, style = theme.type.body)
                        }
                    }
                    innerTextField()
                }
            },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            enabled = enabled,
            visualTransformation = visualTransformation,
        )
        
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.xs))
        if (hasError) {
            W10MText(text = errorText!!, variant = W10MType::caption, tone = W10MTextTone.Danger)
        } else if (helperText != null) {
            W10MText(text = helperText, variant = W10MType::caption, tone = W10MTextTone.Muted)
        }
    }
}

// ============================================================
// W10M PIVOT — Títulos em linha; ativo em texto com barra 2dp accent; outros em muted
// Deslize horizontal troca de seção
// ============================================================

@Composable
fun W10MPivot(
    sections: List<W10MPivotSection>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    kicker: String? = null,
    modifier: Modifier = Modifier,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    val scrollState = rememberScrollState()
    val offsets = remember { mutableMapOf<String, Float>() }
    
    Column(modifier = modifier.padding(top = W10MSpacing.s)) {
        if (kicker != null) {
            W10MText(text = kicker, variant = W10MType::kicker, tone = W10MTextTone.Accent, modifier = Modifier.padding(horizontal = W10MSpacing.gutter))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.xs))
        }
        
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            sections.forEach { section ->
                val current = section.key == selectedKey
                val tone = if (current) W10MTextTone.Text else W10MTextTone.Muted
                
                Box(
                    modifier = Modifier
                        .padding(horizontal = W10MSpacing.s)
                        .padding(top = 2.dp)
                        .heightIn(min = W10MSpacing.touchTarget)
                        // sem fillMaxHeight: dentro de Column ele ocupava a tela inteira e empurrava o conteúdo
                        .combinedClickable(
                            onClick = { onSelect(section.key) },
                            onLongClick = null
                        )
                        .onGloballyPositioned { coordinates ->
                            offsets[section.key] = coordinates.localToRoot(Offset.Zero).x
                        }
                        .background(if (current) Color.Transparent else Color.Transparent)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = W10MSpacing.s)
                            .fillMaxWidth()
                    ) {
                        W10MText(
                            text = section.title,
                            variant = W10MType::title,
                            tone = tone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = W10MSpacing.xs)
                        )
                        // Barra de seleção 2dp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (current) palette.primary else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

data class W10MPivotSection(
    val key: String,
    val title: String,
)

// ============================================================
// W10M PIVOT PAGER — Pivô completo com deslize horizontal
// ============================================================

@Composable
fun W10MPivotPager(
    pages: List<W10MPivotPage>,
    selectedKey: String,
    onChange: (String) -> Unit,
    kicker: String? = null,
    modifier: Modifier = Modifier,
) {
    val index = pages.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val currentPage = pages[index]
    
    Column(modifier = modifier.fillMaxSize()) {
        W10MPivot(
            sections = pages.map { W10MPivotSection(it.key, it.title) },
            selectedKey = selectedKey,
            onSelect = onChange,
            kicker = kicker,
            modifier = Modifier.padding(horizontal = W10MSpacing.gutter)
        )
        
        currentPage.content()
    }
}

data class W10MPivotPage(
    val key: String,
    val title: String,
    val content: @Composable () -> Unit,
)

// ============================================================
// W10M APP BAR — Barra inferior flat chrome (64dp)
// Círculos de traço fino com rótulo (destaque só no ativo), "..." expande rótulos e menu
// ============================================================

@Composable
fun W10MAppBar(
    buttons: List<W10MAppBarButton>,
    menu: List<W10MMenuItem> = emptyList(),
    labels: W10MAppBarLabels = W10MAppBarLabels.Always,
    asTabs: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    val expanded = remember { mutableStateOf(false) }
    val showLabels = labels == W10MAppBarLabels.Always || expanded.value
    val hasMore = menu.isNotEmpty() || labels == W10MAppBarLabels.Expanded
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(palette.chrome)
            .padding(bottom = 0.dp) // Safe area handled by parent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = W10MSpacing.xs)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (hasMore) W10MSpacing.touchTarget else 0.dp)
            ) {
                buttons.take(4).forEach { button ->
                    val ink = if (button.disabled) palette.disabled else if (button.active) palette.primary else palette.text
                    
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .combinedClickable(
                                onClick = { if (!button.disabled) button.onClick() },
                                onLongClick = button.onLongClick
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.Center)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .border(1.5.dp, ink, RoundedCornerShape(16.dp))
                                    .background(if (button.disabled) Color.Transparent else ink.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            ) {
                                button.icon()
                            }
                            if (showLabels) {
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
                                W10MText(
                                    text = button.label,
                                    variant = W10MType::caption,
                                    tone = if (button.disabled) W10MTextTone.Muted else W10MTextTone.Text
                                )
                            }
                        }
                    }
                }
            }
            
            if (hasMore) {
                Box(
                    modifier = Modifier
                        .size(W10MSpacing.touchTarget, 64.dp)
                        .combinedClickable(
                            onClick = { expanded.value = !expanded.value },
                            onLongClick = null
                        )
                ) {
                    W10MIcon(
                        name = W10MIcons.MoreHorizontal,
                        size = 22.dp,
                        color = palette.text
                    )
                }
            }
        }
        
        if (expanded.value && menu.isNotEmpty()) {
            W10MMenu(
                visible = true,
                onClose = { expanded.value = false },
                items = menu,
            )
        }
    }
}

enum class W10MAppBarLabels { Always, Expanded }

data class W10MAppBarButton(
    val key: String,
    val label: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val active: Boolean = false,
    val disabled: Boolean = false,
)

data class W10MMenuItem(
    val title: String,
    val icon: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val disabled: Boolean = false,
)

// ============================================================
// W10M MENU — Painel chrome plano com borda 1dp, itens 48dp
// ============================================================

@Composable
fun W10MMenu(
    visible: Boolean,
    onClose: () -> Unit,
    items: List<W10MMenuItem>,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    
    val theme = useW10MTheme()
    val palette = theme.palette
    val context = LocalContext.current
    
    // Use Popup for menu (W10M style)
    androidx.compose.ui.window.Popup(
        onDismissRequest = onClose,
        properties = PopupProperties(
            focusable = true,
            clippingEnabled = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .background(palette.chrome)
                .border(1.dp, palette.border, RectangleShape)
                .wrapContentSize()
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = W10MSpacing.m)
                        .combinedClickable(
                            onClick = { item.onClick(); onClose() },
                            onLongClick = null,
                            enabled = !item.disabled
                        )
                        .background(if (item.disabled) Color.Transparent else Color.Transparent)
                ) {
                    item.icon?.invoke()
                    if (item.icon != null) androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.m))
                    W10MText(
                        text = item.title,
                        variant = W10MType::body,
                        tone = if (item.destructive) W10MTextTone.Danger else W10MTextTone.Text
                    )
                }
                if (item != items.last()) {
                    HorizontalDivider(
                        color = palette.border,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = W10MSpacing.m)
                    )
                }
            }
        }
    }
}

// ============================================================
// W10M ICONS — Catálogo único vetores traço 1.7 em grade 24
// ============================================================

typealias W10MIconGlyph = @Composable (size: Dp, color: Color) -> Unit

object W10MIcons {
    val Shield: W10MIconGlyph = { size, color -> Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val History: W10MIconGlyph = { size, color -> Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Search: W10MIconGlyph = { size, color -> Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Settings: W10MIconGlyph = { size, color -> Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val MoreHorizontal: W10MIconGlyph = { size, color -> Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Heart: W10MIconGlyph = { size, color -> Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Phone: W10MIconGlyph = { size, color -> Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val PhoneOff: W10MIconGlyph = { size, color -> Icon(Icons.Default.PhoneDisabled, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Lock: W10MIconGlyph = { size, color -> Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Close: W10MIconGlyph = { size, color -> Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Check: W10MIconGlyph = { size, color -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Warning: W10MIconGlyph = { size, color -> Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Add: W10MIconGlyph = { size, color -> Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Delete: W10MIconGlyph = { size, color -> Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Flag: W10MIconGlyph = { size, color -> Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Block: W10MIconGlyph = { size, color -> Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val ContentCopy: W10MIconGlyph = { size, color -> Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(size), tint = color) }
    val Notifications: W10MIconGlyph = { size, color -> Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(size), tint = color) }
}

@Composable
fun W10MIcon(
    name: W10MIconGlyph,
    size: Dp = 24.dp,
    color: Color = useW10MTheme().palette.primary,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size)) { name(size, color) }
}

// ============================================================
// W10M SECTION / LIST ITEM / ROW / DIVIDER
// Grupo com cabeçalho group no accent; linha plana (ícone, título, segunda linha, valor/acessório, seleção com barra 4dp)
// ============================================================

@Composable
fun W10MSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    
    Column(modifier = modifier.padding(horizontal = W10MSpacing.gutter)) {
        title?.let {
            W10MText(text = it, variant = W10MType::group, tone = W10MTextTone.Accent, modifier = Modifier.padding(bottom = W10MSpacing.s))
        }
        content()
    }
}

@Composable
fun W10MListItem(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    meta: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    actions: List<W10MListItemAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    val pressed = remember { mutableStateOf(false) }
    
    val bgColor = when {
        pressed.value -> palette.surface
        selected -> palette.accent.copy(alpha = 0.1f)
        else -> Color.Transparent
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .combinedClickable(
                onClick = onClick ?: {},
                onLongClick = onLongClick
            )
            .padding(horizontal = W10MSpacing.gutter)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = W10MSpacing.touchTarget)
                .padding(vertical = W10MSpacing.s)
            ) {
                // Barra de seleção 4dp à esquerda (ativo)
                if (selected) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(palette.primary)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.s))
                } else {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.xl))
                }
                
                leading?.let {
                    it()
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.m))
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    W10MText(text = title, variant = W10MType::body, tone = W10MTextTone.Text)
                    subtitle?.let {
                        W10MText(text = it, variant = W10MType::caption, tone = W10MTextTone.Muted)
                    }
                }
                
                meta?.let {
                    W10MText(text = it, variant = W10MType::caption, tone = W10MTextTone.Muted)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.m))
                }
                
                trailing?.let {
                    it()
                }
                
                // Ações (toque longo abre menu com ações)
                if (actions.isNotEmpty() && onLongClick != null) {
                    W10MIcon(
                        name = W10MIcons.MoreHorizontal,
                        size = 24.dp,
                        color = palette.textDim
                    )
                }
            }
        }
    }

data class W10MListItemAction(
    val label: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
)

@Composable
fun W10MDivider(modifier: Modifier = Modifier) {
    val palette = useW10MTheme().palette
    HorizontalDivider(
        color = palette.border,
        thickness = 1.dp,
        modifier = modifier.padding(horizontal = W10MSpacing.gutter)
    )
}

// ============================================================
// W10M ROW — Par rótulo/valor
// ============================================================

@Composable
fun W10MRow(
    label: String,
    value: String,
    valueTone: W10MTextTone = W10MTextTone.Text,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = useW10MTheme().palette
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = W10MSpacing.touchTarget)
            .padding(horizontal = W10MSpacing.gutter, vertical = W10MSpacing.s)
            .combinedClickable(onClick = onClick ?: {}, onLongClick = null)
            .background(Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            W10MText(text = label, variant = W10MType::body, tone = W10MTextTone.Text)
            W10MText(text = value, variant = W10MType::body, tone = valueTone)
        }
    }
}

// ============================================================
// W10M TAG — Rótulo retangular (contorno ou preenchido)
// ============================================================

@Composable
fun W10MTag(
    text: String,
    tone: W10MTagTone = W10MTagTone.Outline,
    color: Color? = null,
    modifier: Modifier = Modifier,
) {
    val palette = useW10MTheme().palette
    val c = color ?: palette.primary
    
    val (pair, fg) = when (tone) {
        W10MTagTone.Filled -> c to c to c.forWhiteText()
        W10MTagTone.Outline -> Color.Transparent to c to c
        W10MTagTone.Danger -> palette.dangerFill to palette.dangerFill to palette.onAccent
        W10MTagTone.Warn -> palette.warnFill to palette.warnFill to palette.onAccent
        W10MTagTone.Info -> palette.info.forWhiteText() to palette.info.forWhiteText() to palette.onAccent
    }
    val (bg, border) = pair
    
    Box(
        modifier = modifier
            .padding(horizontal = W10MSpacing.s, vertical = W10MSpacing.xs)
            .background(bg, RectangleShape)
            .border(if (tone == W10MTagTone.Outline) W10MSpacing.strokeWidth else 0.dp, border, RectangleShape)
    ) {
        W10MText(
            text = text,
            variant = W10MType::caption,
            modifier = Modifier.padding(horizontal = W10MSpacing.s, vertical = 2.dp)
        )
    }
}

enum class W10MTagTone { Outline, Filled, Danger, Warn, Info }

// ============================================================
// W10M DIALOG — Painel chrome no topo, largura total, fundo escurecido 60%
// Título subtitle, mensagem, conteúdo, botões lado a lado (vermelho se destrutivo)
// ============================================================

@Composable
fun W10MDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String? = null,
    content: @Composable () -> Unit = {},
    buttons: List<W10MDialogButton> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    
    val theme = useW10MTheme()
    val palette = theme.palette
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.scrim)
                .combinedClickable(onClick = onDismiss, onLongClick = null)
        )
        
        // Dialog panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = W10MSpacing.gutter)
                .background(palette.chrome)
                .padding(W10MSpacing.l)
        ) {
            W10MText(text = title, variant = W10MType::subtitle, tone = W10MTextTone.Text)
            message?.let {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.s))
                W10MText(text = it, variant = W10MType::body, tone = W10MTextTone.Muted)
            }
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.l))
            content()
            
            if (buttons.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.xl))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    buttons.forEachIndexed { index, button ->
                        if (index > 0) androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.s))
                        W10MButton(
                            label = button.text,
                            onClick = { button.onClick(); onDismiss() },
                            variant = if (button.style == W10MDialogButtonStyle.Destructive) W10MButtonVariant.Danger else W10MButtonVariant.Primary,
                        )
                    }
                }
            }
        }
    }
}

enum class W10MDialogButtonStyle { Normal, Destructive, Cancel }

data class W10MDialogButton(
    val text: String,
    val onClick: () -> Unit,
    val style: W10MDialogButtonStyle = W10MDialogButtonStyle.Normal,
)

// ============================================================
// W10M EMPTY / LOADING / NOTICE
// ============================================================

@Composable
fun W10MEmpty(
    message: String,
    actionLabel: String? = null,
    actionIcon: (@Composable () -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = useW10MTheme().palette
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        W10MText(text = message, variant = W10MType::subtitle, tone = W10MTextTone.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = W10MSpacing.xl))
        actionLabel?.let {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.xl))
            W10MButton(
                label = it,
                onClick = onActionClick,
                variant = W10MButtonVariant.Primary,
                icon = actionIcon,
            )
        }
    }
}

@Composable
fun W10MLoading(
    message: String = "Carregando...",
    modifier: Modifier = Modifier,
) {
    val palette = useW10MTheme().palette
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        W10MProgress(color = palette.primary, size = 48.dp)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.m))
        W10MText(text = message, variant = W10MType::body, tone = W10MTextTone.Muted)
    }
}

@Composable
fun W10MNotice(
    message: String,
    tone: W10MNoticeTone = W10MNoticeTone.Info,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = useW10MTheme().palette
    
    val (barColor, bgColor) = when (tone) {
        W10MNoticeTone.Info -> palette.info to palette.info.copy(alpha = 0.1f)
        W10MNoticeTone.Warning -> palette.warn to palette.warn.copy(alpha = 0.1f)
        W10MNoticeTone.Danger -> palette.danger to palette.danger.copy(alpha = 0.1f)
        W10MNoticeTone.Success -> palette.primary to palette.primary.copy(alpha = 0.1f)
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = W10MSpacing.gutter, vertical = W10MSpacing.m)
            .background(bgColor, RectangleShape)
    ) {
        Row(
            modifier = Modifier.padding(W10MSpacing.m)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(barColor)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.m))
            Column(modifier = Modifier.weight(1f)) {
                W10MText(text = message, variant = W10MType::body, tone = W10MTextTone.Text)
                actionLabel?.let {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.s))
                    W10MButton(
                        label = it,
                        onClick = onActionClick,
                        variant = W10MButtonVariant.Text,
                        size = W10MButtonSize.Small,
                    )
                }
            }
        }
    }
}

enum class W10MNoticeTone { Info, Warning, Danger, Success }

// ============================================================
// W10M CHECKBOX / RADIO
// ============================================================

@Composable
fun W10MCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = W10MSpacing.touchTarget)
            .padding(horizontal = W10MSpacing.gutter, vertical = W10MSpacing.xs)
            .combinedClickable(onClick = { if (enabled) onCheckedChange(!checked) }, onLongClick = null)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(W10MSpacing.strokeWidth, if (checked) palette.accent else palette.border, RectangleShape)
                .background(if (checked) palette.accentFill else Color.Transparent, RectangleShape)
        ) {
            if (checked) {
                W10MIcon(name = W10MIcons.Check, size = 16.dp, color = palette.onAccent, modifier = Modifier.padding(4.dp))
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.m))
        W10MText(text = label, variant = W10MType::body, tone = if (enabled) W10MTextTone.Text else W10MTextTone.Muted)
    }
}

@Composable
fun W10MRadio(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    description: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = W10MSpacing.touchTarget)
            .padding(horizontal = W10MSpacing.gutter, vertical = W10MSpacing.xs)
            .combinedClickable(onClick = { if (enabled) onClick() }, onLongClick = null)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(2.dp, if (selected) palette.primary else palette.border, RoundedCornerShape(12.dp))
                .background(if (selected) palette.primary.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(12.dp))
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(palette.primary, RoundedCornerShape(5.dp))
                        .padding(7.dp)
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(W10MSpacing.m))
        Column {
            W10MText(text = label, variant = W10MType::body, tone = if (enabled) W10MTextTone.Text else W10MTextTone.Muted)
            description?.let {
                W10MText(text = it, variant = W10MType::caption, tone = W10MTextTone.Muted)
            }
        }
    }
}

// ============================================================
// W10M SCORE — Score em display na cor da faixa + barra determinada 4dp
// ============================================================

@Composable
fun W10MScore(
    score: Int,
    modifier: Modifier = Modifier,
) {
    val theme = useW10MTheme()
    val palette = theme.palette
    val scoreColor = palette.scoreColor(score)
    
    Column(
        modifier = modifier
            .padding(horizontal = W10MSpacing.gutter)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            W10MText(text = score.toString(), variant = W10MType::display, tone = W10MTextTone.Text, modifier = Modifier.wrapContentSize())
            W10MText(text = "de 100", variant = W10MType::caption, tone = W10MTextTone.Muted, modifier = Modifier.padding(bottom = 8.dp))
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(W10MSpacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(palette.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(score / 100f)
                    .background(scoreColor)
            )
        }
    }
}