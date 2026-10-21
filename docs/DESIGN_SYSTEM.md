# Design system W10M — AntiSpam BR

Status: **plano + migração parcial** (branch `feat/metro-w10m-ui`).
Referências: `conexus-launcher/docs/W10M_DESIGN_SYSTEM.md`, skill `metro-w10m-ui`, kit `vendor/conexus/metro-ui` (cópia; não editar).

## 1. Inventário (antes do código)

### Rotas (expo-router, `app/`)

| Rota | Tela | Componentes W10M hoje | Pivô? |
|---|---|---|---|
| `index` | redireciona para onboarding ou abas | — | não |
| `onboarding` | apresentação em passos | `Glyph` (Ionicons do kit) | não |
| `(tabs)/index` | Início (proteção, resumo) | `AppScreen`, `MetroRow`, `MetroScore` | não |
| `(tabs)/history` | Histórico + explicação | `AppScreen`, `MetroListItem`, `AdBanner` | não |
| `(tabs)/verify` | Verificar número | `AppScreen`, `MetroTextField`, `MetroButton` | não |
| `(tabs)/settings` | Ajustes | `MetroPivotPager` (proteção · regras · listas · geral), `MetroRadio`, `MetroToggle`, `MetroTextField`, `MetroDialog` | **sim** (4 páginas) |
| `(tabs)/_layout` | Barra inferior (abas) | `MetroAppBar` `asTabs`, ícones `GLYPHS` | — |
| `account` | Conta Conexus (opcional) | `AppScreen`, `MetroSection`, `MetroListItem`, `MetroDialog` | não |
| `donate` | Doação PIX | `AppScreen`, `MetroTag`, `QRCode` | não |
| `login` | Login Conexus | `ConexusLoginScreen` (pacote `@conexus/account-expo`) | não |
| `+native-intent` | Deep links | — | — |

Lógica preservada: bloqueio/triagem (`src/app/screening`, `src/core`), listas, regras, backup, conta, doação. Nada disso muda nesta entrega.

### Componentes e estilos próprios

- `src/ui/theme.ts`: paletas claro/escuro, `ESSENTIALS` (10 cores W10M), `forBackground`/`forWhiteText` (contraste AA), escala tipográfica, espaçamento, movimento, `useTheme()`. **Único lugar com hex próprio do app.**
- `src/ui/app.tsx`: `AppScreen`, `Flush`, `MetroScore`, `MetroRow`.
- `src/ui/AdBanner.tsx`: usa `theme` e `spacing`.
- `vendor/conexus/metro-ui`: kit W10M (tokens, provider, pivô, app bar, lista, toggle, radio, diálogo, menu, estados, tiles). Usa **Ionicons** internamente (`MetroIcon`, `GLYPHS`).
- Grep de cor/fonte/raio literal em `app/` e `src/ui/*.tsx`: nenhuma ocorrência fora de `theme.ts` (telas usam `theme.*` e `spacing.*`).

### Achados (gaps)

1. **Accent**: `ACCENT` era o verde `#60A917`. O padrão do produto é `brand.primary` = `#DC2626` (fleet.yml).
2. **Preferências de aparência**: não existem. Tema segue só o sistema; não há escolha de cor de destaque nem de "reduzir animações" no app.
3. **Ícones**: o kit usa Ionicons. As telas do app usam `GLYPHS.*` (13 nomes). Pedido: Fluent UI System Icons (MIT), uma biblioteca só.
4. **Fonte**: o kit usa a fonte do sistema. Selawik (OFL) não está no repo; baixar exige aprovação explícita.
5. Notificação (`app.json` plugin `expo-notifications`, `color: #60A917`) e `adaptiveIcon.backgroundColor` (`#0B1220`): fora desta entrega (config nativa).

## 2. Tokens (fonte única)

Arquivo: `src/ui/metro/tokens.ts` (puro, sem React: testável em Node).

- `BRAND_PRIMARY = '#DC2626'` (fleet.yml `brand.primary`) → padrão do destaque.
- `ESSENTIALS` (10 cores W10M) e `ACCENT_CHOICES` (as 10 + marca).
- `THEME_PREFS`: `system | dark | light`.
- `normalizeHex(input)`: aceita `#RRGGBB`, `RRGGBB`, `#RGB`; rejeita o resto.
- `resolveAccent(saved)`: hex válido ou `BRAND_PRIMARY`.
- `DEFAULT_APPEARANCE` e `sanitizeAppearance(saved)`: valida preferências lidas do armazenamento.

`src/ui/theme.ts` reexporta `ESSENTIALS` e passa a usar `BRAND_PRIMARY` como `ACCENT`.

## 3. Preferências (persistidas no store existente, AsyncStorage)

`AppState.appearance = { theme, accent, reduceMotion }`. Efeitos:
- `useTheme()` segue `appearance.theme` (sistema quando `system`).
- `MetroProvider` recebe `scheme`, `accent` e `reduceMotion` do store.
- Tela Ajustes › **Aparência** (nova página do pivô): tema, cor de destaque (10 essenciais + marca + hex personalizado validado), reduzir animações.

## 4. Componentes novos

- `src/ui/metro/icons.tsx`: `FluentIcon` (react-native-svg, 24 px, `currentColor` por prop) e catálogo `FLUENT` com os 13 ícones usados pelo app. Paths extraídos de `@fluentui/svg-icons@1.1.344` (MIT), variante Regular.
- `src/ui/metro/AccentSwatch.tsx`: amostra quadrada 48 dp, `accessibilityRole="radio"`, estado selecionado com borda.

## 5. Migração tela a tela

| Tela | Plano | Status |
|---|---|---|
| Barra de abas | ícones Fluent, tokens do accent | feito |
| Ajustes | nova página Aparência; ícones Fluent nos botões | feito |
| Início, Histórico, Verificar, Conta, Doação, Onboarding | ícones Fluent onde usam `GLYPHS`; sem mudança de layout | parcial |
| Login (`ConexusLoginScreen`) | pacote vendorizado | pendente (upstream) |

Pivô só em Ajustes (seções irmãs). Demais telas são páginas simples com cabeçalho grande.

## 6. Pendências e riscos

- **Ionicons dentro do kit vendorizado** (`MetroIcon`, `GLYPHS` usados pelo próprio kit): a regra "uma biblioteca" só fecha quando o kit em `conexus-platform/js/metro-ui` trocar para Fluent. Não editar `vendor/`.
- **Selawik não empacotado**: aguarda aprovação de download. Fonte atual = sistema.
- **Vermelho como destaque** colide com o vermelho de bloqueio (`danger`). Mitigação: bloqueio segue com ícone e rótulo.
- **Verificação visual** (claro/escuro, 200% de fonte, TalkBack, tablet) não feita nesta sessão: só testes de tokens e build de bundle.
- Testes de render (React Native Testing Library) não estão no projeto; componentes novos ficam cobertos por testes de lógica pura e tipagem.

## 7. Critérios de aceite

- [ ] Zero hex/fonte/raio literal fora de `src/ui/theme.ts` e `src/ui/metro/tokens.ts`.
- [ ] Claro, escuro e sistema funcionam e persistem.
- [ ] Accent troca e persiste em todas as telas; padrão `#DC2626`.
- [ ] Reduzir animações zera o movimento do kit.
- [ ] Contraste AA do destaque como texto e do texto sobre preenchimento (testado).
- [ ] Testes, typecheck e `expo export --platform android` verdes.
