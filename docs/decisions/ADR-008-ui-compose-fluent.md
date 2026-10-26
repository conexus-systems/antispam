# ADR-008: UI Framework — Jetpack Compose + Fluent Tokens (Conexus Design System)

**Status:** Accepted  
**Date:** 2026-10-24  
**Deciders:** A0, A2, H5 (UX/UI)

---

## Contexto

App Android precisa de UI:

1. **Moderna, declarativa** — Compose é o padrão Android atual
2. **Design System próprio** — Identidade visual Conexus (inspirado Fluent/Metro/W10M)
3. **Acessibilidade nativa** — TalkBack, scaling, touch targets, contraste WCAG 2.2 AA
4. **Temas** — Claro/escuro/sistema, cor de destaque configurável, reduzir movimento
5. **Performance** — 60fps, recomposição inteligente, lazy lists
6. **Testabilidade** — Compose Testing, preview, screenshot testing

---

## Decisão

**Jetpack Compose + Material 3 + Tokens Fluent customizados:**

### Stack UI

| Camada | Escolha |
|--------|---------|
| **Toolkit** | Jetpack Compose (BOM 2024.05+) |
| **Design System** | Material 3 + Tokens Fluent (Conexus) |
| **Icons** | Material Icons Extended + Fluent custom (SVG → VectorDrawable) |
| **Navigation** | Navigation Compose (type-safe routes) |
| **State** | StateFlow + Compose `collectAsStateWithLifecycle` |
| **DI** | Hilt + ViewModel Compose |
| **Images** | Coil Compose |
| **Permissions** | Accompanist Permissions |
| **System UI** | Accompanist SystemUiController |

---

## Tokens Fluent (Conexus)

```kotlin
// ui/theme/Colors.kt
object ConexusColors {
    // Brand
    val Primary = Color(0xFF1BA1E2)      // Azul Conexus
    val PrimaryContainer = Color(0xFFD6EFFF)
    val OnPrimary = Color.White
    val OnPrimaryContainer = Color(0xFF001F33)

    // Semantic
    val Success = Color(0xFF2E7D32)
    val SuccessContainer = Color(0xFFE8F5E9)
    val Warning = Color(0xFFF57C00)
    val WarningContainer = Color(0xFFFFF3E0)
    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)

    // Surface variants
    val SurfaceVariant = Color(0xFFE0E3E8)
    val OnSurfaceVariant = Color(0xFF44474C)
    val Outline = Color(0xFF74777C)
    val OutlineVariant = Color(0xFFC4C6CC)
}
```

### Tipografia (Segoe/Selawik style)

```kotlin
// ui/theme/Typography.kt
object ConexusTypography {
    val Header = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp)
    val TitleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    val TitleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp)
    val Body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val LabelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    val Caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
}
```

### Tema Compose

```kotlin
@Composable
fun ConexusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ConexusDarkColors else ConexusLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ConexusTypography,
        content = content
    )
}
```

---

## Componentes Base (Conexus UI Library)

| Componente | Descrição |
|------------|-----------|
| `MetroScreen` | Scaffold com padding, safe area, background |
| `MetroButton` | Primary/Secondary/Text/Disabled, loading state, icon |
| `MetroText` | Variant (Header/Title/Body/Label/Caption), tone |
| `MetroCard` | Elevated/Filled/Outlined, padding, clickable |
| `MetroListItem` | Title, subtitle, meta, trailing, actions, selected |
| `MetroTag` | Text + color, filled/outlined, size |
| `MetroToggle` | Switch com label + description, disabled state |
| `MetroRadio` / `MetroRadioGroup` | Acessível, custom colors |
| `MetroTextField` | Label, placeholder, error, secure, keyboardType |
| `MetroDialog` | Title, message, buttons, custom content |
| `MetroPivotPager` | Pivot tabs (proteção/regras/listas/geral/ aparência) |
| `MetroEmpty` | Illustration + message + action |
| `FluentIcon` | SVG → VectorDrawable, size, tint |

---

## Acessibilidade (WCAG 2.2 AA)

| Critério | Implementação |
|----------|---------------|
| **Contraste 4.5:1** | Tokens validados (`Color.contrastRatio`) |
| **Touch targets ≥48dp** | `Modifier.minimumInteractiveComponentSize()` |
| **TalkBack labels** | `contentDescription`, `semantics { }` |
| **Scaling 200%** | `sp` units, `Modifier.fillMaxWidth()` |
| **Reduzir movimento** | `LocalReduceMotion` + `AnimatedVisibility` condicional |
| **Focus order** | `focusRequester`, `FocusTraversal` |

---

## Dark Mode Strategy

```kotlin
// Suporte a 3 modos: system / light / dark
@Composable
fun rememberThemeMode(): StateFlow<ThemeMode> {
    val dataStore = LocalDataStore.current
    return dataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            "dark" -> ThemeMode.Dark
            "light" -> ThemeMode.Light
            else -> ThemeMode.System
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ThemeMode.System)
}

@Composable
fun AppThemeContent() {
    val themeMode by rememberThemeMode().collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    ConexusTheme(darkTheme = darkTheme) { ... }
}
```

---

## Cor de Destaque Configurável

- **Presets**: 8 cores (Azul, Verde, Roxo, Laranja, Vermelho, Rosa, Ciano, Amarelo)
- **Custom**: `#RRGGBB` input com validação
- **Persistência**: DataStore → aplicado no `ColorScheme` via `compositionLocalOf`

---

## Reduzir Movimento

```kotlin
val reduceMotion = LocalReduceMotion.current
val transition = if (reduceMotion) {
    AnimatedVisibility(enter = fadeIn(), exit = fadeOut())
} else {
    AnimatedVisibility(
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    )
}
```

---

## Testes UI

```kotlin
// Screenshot testing (Roborazzi/Paparazzi)
@Test
fun onboarding_screenshot() {
    composeTestRule.setContent {
        ConexusTheme { OnboardingScreen(onFinish = {}) }
    }
    composeTestRule.captureRoboImage("onboarding_step1")
}

// Semantic tree testing
@Test
fun onboarding_accessibility() {
    composeTestRule.setContent { ... }
    composeTestRule.onNode(hasText("Ativar proteção"))
        .assert(hasClickAction())
        .assert(hasContentDescription("Ativar proteção"))
}
```

---

## Referências

- [Jetpack Compose docs](https://developer.android.com/jetpack/compose)
- [Material 3 specs](https://m3.material.io/)
- [Fluent Design tokens](https://fluent2.microsoft.design/tokens)
- [WCAG 2.2 AA](https://www.w3.org/WAI/WCAG22/quickref/)
- [SDD §5 UI Modules](./SDD.md#5-módulos-funcionais-app-android)
