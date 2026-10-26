# SDD — Windows 10 Mobile (W10M) Design System para UWP

**Versão:** 1.0  
**Data:** 24 de outubro de 2026  
**Projeto:** Conexus Caller ID (Android com visual W10M) / Conexus Launcher  
**Fonte da verdade:** `conexus-launcher/docs/W10M_DESIGN_SYSTEM.md` (Kotlin/Android Views) + `antispam-br/docs/W10M.md` (React Native/Expo)  

---

## 1. Princípios Fundamentais (W10M)

| Princípio | Descrição |
|-----------|-----------|
| **Plano e quadrado** | `borderRadius = 0`, sem sombras/elevation, sem gradientes, sem cartões flutuantes. Só círculos: botões da App Bar, radio, pontos de progresso. |
| **Tipografia faz a hierarquia** | Títulos grandes, leves (`FontWeight.Light`/`300`), alinhados à esquerda; negrito só no kicker (nome do app em maiúsculas) e cabeçalho de grupo. |
| **Cor de destaque com função** | Seleção, links, controles ligados, foco do campo, pivô/aba ativa, cabeçalhos de grupo. Ícones e textos comuns ficam monocromáticos. |
| **Listas, não cartões** | Linhas sobre a própria página, divisórias discretas (1dp). |
| **Movimento curto e desligável** | Respeita "Remover animações" do sistema (Reduce Motion). |

---

## 2. Tokens (Port 1:1 do Conexus Launcher)

### 2.1 Cores Essenciais (Accent.kt)

```kotlin
// Essenciais W10M (não mudam)
object W10MEssentials {
    const val VERMELHO   = "#E51400"  // danger / bloqueio
    const val LARANJA    = "#FA6800"  // warn / suspeito
    const val AMARELO    = "#E3C800"  // score médio
    const val VERDE      = "#60A917"  // accent padrão / sucesso / permitido
    const val CIANO      = "#1BA1E2"  // info
    const val AZUL       = "#0078D7"  // azul
    const val ROXO       = "#AA00FF"  // roxo
    const val ROSA       = "#E671B8"  // rosa
    const val CINZA      = "#647687"  // mute
    const val MARROM     = "#825A2C"  // marrom
}

// Destaque padrão do app (brand primary)
const val DEFAULT_ACCENT = W10MEssentials.VERDE
```

### 2.2 Paletas Completas (Theme.kt) — Dark First

| Token | Dark | Light | Uso |
|-------|------|-------|-----|
| `bg` | `#000000` | `#FFFFFF` | Fundo da página |
| `chrome` | `#1A1A1A` | `#F2F2F2` | App Bar, menus, diálogos, avisos |
| `surface` | `#2B2B2B` | `#E6E6E6` | Botão secundário pressionado, trilhos |
| `text` | `#FFFFFF` | `#000000` | Texto principal |
| `textDim` | `#CCCCCC` | `#505050` | Texto secundário (muted) |
| `border` | `#333333` | `#CCCCCC` | Divisória / borda discreta |
| `disabled` | `#666666` | `#999999` | Desabilitado |
| `accent` | `#60A917` | `#60A917` | Destaque cru (brand) |
| `primary` | `forBackground(accent)` | `forBackground(accent)` | Destaque p/ texto/linha sobre bg (≥4.5:1) |
| `accentFill` | `forWhiteText(accent)` | `forWhiteText(accent)` | Preenchimentos c/ texto branco (botão primário, pino ligado) |
| `onAccent` | `#FFFFFF` | `#FFFFFF` | Texto sobre `accentFill` |
| `danger` | `forBackground(VERMELHO)` | `forBackground(VERMELHO)` | Vermelho ajustado ao fundo |
| `warn` | `forBackground(LARANJA)` | `forBackground(LARANJA)` | Laranja ajustado ao fundo |
| `info` | `forBackground(CIANO)` | `forBackground(CIANO)` | Ciano ajustado ao fundo |
| `mute` | `forBackground(CINZA)` | `forBackground(CINZA)` | Cinza ajustado ao fundo |
| `dangerFill` | `forWhiteText(VERMELHO)` | `forWhiteText(VERMELHO)` | Preenchimento vermelho c/ texto branco |
| `warnFill` | `forWhiteText(LARANJA)` | `forWhiteText(LARANJA)` | Preenchimento laranja c/ texto branco |
| `scrim` | `rgba(0,0,0,0.6)` | `rgba(0,0,0,0.6)` | Fundo diálogo/menu escurecido |
| `fieldFocusBg` | `#FFFFFF` | `#FFFFFF` | Campo em foco (sempre branco) |
| `fieldFocusText` | `#000000` | `#000000` | Texto campo em foco (sempre preto) |
| `fieldFocusPlaceholder` | `#666666` | `#666666` | Placeholder campo em foco |

**Algoritmo `forBackground`**: clareia no fundo escuro, escurece no claro até 4.5:1.  
**Algoritmo `forWhiteText`**: escurece até texto branco passar 4.5:1.

### 2.3 Tipografia (Type.kt) — Fonte do Sistema

> **Segoe UI não pode ser embarcada**. Usa fonte do sistema; RN/Android aplicam escala de fonte do sistema (`allowFontScaling` / `sp`).

| Estilo | dp | Peso | Linha | Uso |
|--------|-----|------|-------|-----|
| `display` | 64 | Light (300) | 72 | Score em Verificar |
| `header` | 34 | Light (300) | 42 | Título da página, onboarding |
| `title` | 28 | Light (300) | 34 | Itens do pivô |
| `subtitle` | 20 | Regular (400) | 26 | Título de diálogo, número analisado |
| `group` | 16 | SemiBold (600) | 22 | Cabeçalho de grupo (**no destaque**) |
| `body` | 15 | Regular (400) | 21 | Linhas, botões, campos |
| `caption` | 12 | Regular (400) | 16 | Segunda linha, ajuda |
| `kicker` | 12 | SemiBold (600) | 16 | "CONEXUS CALLER ID" acima do título (uppercase, letter-spacing 0.72) |

### 2.4 Espaçamento, Alvos, Forma, Movimento

| Token | Valor | Uso |
|-------|-------|-----|
| `spacing.xs` | 4dp | Micro gap |
| `spacing.s` | 8dp | Gap pequeno |
| `spacing.m` | 12dp | Gap médio |
| `spacing.l` | 16dp | Gap grande / gutter lateral |
| `spacing.xl` | 24dp | Gap extra |
| `gutter` | 16dp | Margem lateral das páginas |
| `touchTarget` | 44dp | Alvo mínimo de toque |
| `strokeWidth` | 2dp | Bordas de controis (botão secundário, campo, caixa) |
| `radius` | 0dp | **W10M: flat and square** |

**Motion:**
| Token | Valor | Uso |
|-------|-------|-----|
| `quickMs` | 100ms | Interruptor |
| `fastMs` | 167ms | Transições rápidas |
| `pageMs` | 250ms | Entrada de página (sobe 20dp + aparece); diálogo/menu |
| `pageRise` | 20dp | Deslocamento entrada |
| `pressScale` | 0.98 | Escala ao pressionar |

---

## 3. Componentes (Catálogo UWP/W10M)

### 3.1 Botões

| Variante | Visual | Estados |
|----------|--------|---------|
| **Primary** | Preenchido `accentFill`, texto `onAccent` | Pressed: inverte → bg `text`, fg `bg` |
| **Secondary** | Borda 2dp `text`, transparente, texto `text` | Pressed: `accentFill` c/ `onAccent` |
| **Danger** | Preenchido `dangerFill`, texto `onAccent` | Pressed: inverte → bg `text`, fg `bg` |
| **Text** | Link no `primary`, sem borda | Pressed: `surface` |

**Tamanhos:** `normal` (40dp alt), `small` (36dp, caption).  
**Block:** ocupa largura total.  
**Loading:** 5 pontos W10M na cor do rótulo.  
**Disabled:** opacidade 0.4.

### 3.2 Text Field

- Rótulo **acima**, caixa quadrada com borda 2dp
- **Em foco**: fundo **branco** (`fieldFocusBg`), texto **preto** (`fieldFocusText`), borda `accent`
- Fora de foco: fundo `surface`, borda `border`
- Erro: borda `danger`, ajuda vermelha
- Secure: `PasswordVisualTransformation`

### 3.3 Toggle / Switch

- Trilho retangular **44×20dp** com borda 2dp
- Pino **quadrado** 16dp
- Ligado: trilho `accentFill`, pino `onAccent`
- Desligado: trilho `border`, pino `text`
- Rótulo "Ativado/Desativado" ao lado; linha toda é alvo

### 3.4 Checkbox

- Quadrado 24dp, borda 2dp
- Marcado: preenchido `accentFill` com ✓ `onAccent`
- Desmarcado: borda `border`, transparente

### 3.5 Radio

- Círculo 24dp, anel 2dp no `primary` + ponto 10dp `primary`
- Nome + descrição opcional

### 3.4 Pivot (Tabs Superior)

- Títulos `title` (28dp Light) em linha horizontal scrollável
- Ativo: texto `text` + **barra 2dp `primary`** na base
- Inativos: texto `textDim` (muted)
- Deslize horizontal troca seção; entrada lateral (`MetroSlideIn`)
- Wrapper: `MetroPivotPager` (só página atual montada)

### 3.5 App Bar (Inferior, 64dp)

- Faixa `chrome` plana embaixo
- Até 4 comandos: **círculos 32dp** traço 1.5dp, rótulo `caption` 11dp
- Ativo: ícone/rótulo no `primary`
- "..." (`MoreHorizontal`) expande rótulos e abre **Menu** (secundários)
- `labels="always"` (padrão W10M) ou `"expanded"`

### 3.6 Menu (Contexto)

- Painel `chrome` plano, borda 1dp `border`
- Itens 48dp altura, padding 16dp
- Destrutivo: texto `danger`

### 3.7 ListItem / Section / Row / Divider

- **Section**: cabeçalho `group` (16dp SemiBold) **no destaque**
- **ListItem**: linha plana 48dp+, ícone opcional, título `body`, subtitle `caption`, meta `caption`, trailing, **barra seleção 4dp `primary`** à esquerda quando selecionado
- **Row**: par rótulo/valor, `body` / `body`
- **Divider**: 1dp `border`, margem lateral `gutter`

### 3.8 Tag

- Retângulo: `Outline` (borda `primary`) ou `Filled` (`primary`/`dangerFill`/`warnFill`)
- Caption 12dp

### 3.9 Dialog

- Painel `chrome` **topo da tela, largura total**, scrim 60% (`scrim`)
- Título `subtitle`, mensagem `body` muted
- Botões lado a lado (destrutivo = `Danger`, outro = `Primary`)
- Voltar fecha

### 3.10 Progress

- **Indeterminado**: 5 pontos 8dp correndo (1s ciclo, delay 100ms)
- **Determinado**: barra plana 4dp `primary` sobre `surface`

### 3.11 Score

- Valor em `display` (64dp Light) na cor da faixa (`scoreColor`)
- Barra determinada 4dp mesma cor

### 3.12 Icons

- Catálogo único vetores traço **1.7dp** em grade **24dp** (Material Icons Extended / Ionicons outline equivalentes)
- Monocromáticos; destaque só pinta estado ativo
- Nomes: `shield`, `history`, `search`, `settings`, `more`, `heart`, `phone`, `phoneOff`, `lock`, `close`, `check`, `warning`, `add`, `delete`, `flag`, `block`, `copy`, `bell`

---

## 4. Telas do App (Mapping UWP → Android/Compose)

| Tela UWP | Compose Route | Componentes W10M |
|----------|---------------|------------------|
| **Onboarding** | `onboarding` | `W10MScreen` + kicker/header + step indicator quadrado + `W10MButton` Primary/Text |
| **Home / Início** | `home` | `W10MHeader` (kicker + título), `W10MTextField` simulador, `W10MButton` Primary, QuickActions como `W10MListItem` |
| **Histórico** | `history` | `LazyColumn` de `W10MListItem` (barra seleção 4dp, trailing action tag, long-press → `W10MMenu`) |
| **Verificar** | `verify` | `W10MTextField` + `W10MButton` Primary → `W10MScore` + `W10MSection` "Por que este score?" + ações rápidas |
| **Ajustes** | `settings` | `W10MPivotPager` 5 páginas: Proteção, Regras, Listas, Geral, Aparência |
| **Proteção** | `settings#protection` | `W10MRow` papel, `W10MRadio` modos, custom thresholds `W10MButton` small |
| **Regras** | `settings#rules` | `W10MListItem` com `W10MTag` ativa/desativada, ações toggle/remove, add `W10MTextField` prefixo |
| **Listas** | `settings#lists` | `W10MSection` Permitidos/Bloqueados, `W10MListItem` + delete, add `W10MTextField` |
| **Geral** | `settings#general` | `W10MRow` Conta, IA Toggle, Backup botões, Base local rows, Sobre rows, Reset `W10MButton` Danger |
| **Aparência** | `settings#appearance` | `W10MRadio` tema (system/dark/light), swatches cor 48dp quadrados, custom hex field, `W10MToggle` reduce motion |

---

## 5. Acessibilidade (WCAG 2.2 AA)

| Critério | Implementação W10M |
|----------|-------------------|
| **Contraste 4.5:1** | Tokens validados (`forBackground`, `forWhiteText`); testado em `__tests__/theme.test.ts` |
| **Touch targets ≥44dp** | `touchTarget = 44dp`; `Modifier.minimumInteractiveComponentSize()` |
| **TalkBack / Leitores** | `contentDescription` obrigatório em ícones; `semantics { }` para estado (selected, checked, expanded); `accessibilityRole` (button, tab, switch, checkbox, radio, menuitem, alert, progressbar) |
| **Escala 200%** | Tudo em `sp`/`dp`; `Modifier.fillMaxWidth()` evita truncamento |
| **Reduzir movimento** | `LocalReduceMotion` lê `AccessibilityInfo.isReduceMotionEnabled`; animações viram `fadeIn/Out` instantâneo |
| **Ordem de foco** | `FocusRequester`, `FocusTraversal` natural (topo→baixo, esquerda→direita) |

---

## 6. Tema Dinâmico (Runtime)

```kotlin
// Preferência do usuário (DataStore) + sistema
val themeMode = dataStore.data.map { prefs ->
    when (prefs[THEME_MODE_KEY]) {
        "dark"  -> ThemeMode.Dark
        "light" -> ThemeMode.Light
        else    -> ThemeMode.System  // escuro quando indefinido
    }
}

// Cor de destaque configurável (DataStore)
val accentHex = dataStore.data.map { it[ACCENT_KEY] ?: DEFAULT_ACCENT }

// Provider
CompositionLocalProvider(
    LocalW10MTheme provides W10MTheme(
        palette = getW10MPalette(dark = isDark, accentHex = accentHex)
    )
) { content() }
```

---

## 7. Reduzir Movimento (Reduce Motion)

```kotlin
val reduceMotion = LocalReduceMotion.current

// Entrada de página
val transition = if (reduceMotion) {
    AnimatedVisibility(enter = fadeIn(), exit = fadeOut())
} else {
    AnimatedVisibility(
        enter = slideInVertically(initialOffsetY = { pageRise }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { pageRise }) + fadeOut()
    )
}

// Press scale
Modifier.graphicsLayer { 
    scaleX = if (pressed) pressScale else 1f
    scaleY = if (pressed) pressScale else 1f
}
```

---

## 8. Validação Automatizada

```bash
# TypeScript/React Native (antispam-br)
npx tsc --noEmit
npx jest --ci  # inclui theme.test.ts (contraste AA, tokens, motion)

# Kotlin/Android (conexus-caller-id)
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest  # requer emulador/device
# Screenshot testing: Paparazzi/Roborazzi
```

**Testes de tema obrigatórios:**
- Contraste AA: todas cores de texto/linha sobre `bg` e `chrome`
- Contraste AA: `onAccent` sobre `accentFill`, `dangerFill`, `warnFill`
- Tokens essenciais nos dois fundos
- `Reduce Motion` desliga `pageRise` e `pressScale`

---

## 9. Diferenças UWP Nativo vs Port Android/Compose

| Aspecto | UWP Nativo (WinUI 3) | Android/Compose Port |
|---------|---------------------|---------------------|
| **Fonte** | Segoe UI Variable (embarcada) | Fonte do sistema (Roboto/Inter) |
| **Acrylic/Reveal** | `AcrylicBrush`, `RevealBorderBrush` | Simulado: `chrome` + `scrim` + ripple manual |
| **NavigationView** | Pane + Hamburger | `W10MPivotPager` (top tabs) + `W10MAppBar` (bottom) |
| **CommandBar** | `AppBarButton` com `LabelPosition` | `W10MAppBar` círculos + "..." menu |
| **Flyout/MenuFlyout** | Nativo | `W10MMenu` (Popup) |
| **ContentDialog** | Nativo | `W10MDialog` (Box + scrim) |
| **TextBox** | `TextBox` com `Header` | `W10MTextField` (BasicTextField + label) |
| **ToggleSwitch** | Nativo | `W10MToggle` (Box + thumb quadrado) |
| **ProgressRing/Bar** | Nativo | `W10MProgress` (5 dots / barra 4dp) |
| **RatingControl** | Nativo | — (não usado) |
| **CalendarView** | Nativo | — (não usado) |

---

## 10. Checklist de Conformidade W10M (por PR)

- [ ] **Zero `borderRadius > 0`** exceto botões App Bar (32dp círculo), Radio (12dp), Progress dots
- [ ] **Zero `elevation` / `shadow`** — usar `chrome` + `scrim` para elevação visual
- [ ] **Zero gradientes** — cores sólidas dos tokens
- [ ] **Kicker** presente acima do título em páginas principais (uppercase, 12dp SemiBold, accent)
- [ ] **Group headers** em `group` (16dp SemiBold) **no `primary` (accent)**
- [ ] **Pivot ativo** tem barra 2dp `primary`; inativos `muted`
- [ ] **App Bar** 64dp `chrome`, círculos 32dp traço 1.5dp, "..." expande
- [ ] **ListItem** selecionado = barra 4dp `primary` esquerda
- [ ] **Campo foco** = branco/preto + borda `accent`
- [ ] **Botão Primary** = `accentFill`/`onAccent`; pressed inverte
- [ ] **Reduce Motion** desliga `pageRise`, `pressScale`, animações viram fade
- [ ] **Contraste AA** validado em CI (`theme.test.ts` / `ThemeTest.kt`)
- [ ] **Touch target 44dp** mínimo em todos interativos
- [ ] **Strings PT-BR** em `strings.xml` / recursos — sem hardcoded

---

## 11. Referências

- [Windows 10 Mobile Design Guidelines (Archive)](https://web.archive.org/web/2017/https://developer.microsoft.com/en-us/windows/apps/design)
- [Fluent Design System (sucessor)](https://fluent2.microsoft.design/)
- [Conexus Launcher W10M_DESIGN_SYSTEM.md](../conexus-launcher/docs/W10M_DESIGN_SYSTEM.md)
- [antispam-br W10M.md](../antispam-br/docs/W10M.md)
- [WCAG 2.2 AA](https://www.w3.org/WAI/WCAG22/quickref/)

---

*Este SDD é a especificação canônica do visual W10M para todos os apps Conexus (Android, futura UWP, Web). Qualquer desvio requer ADR.*