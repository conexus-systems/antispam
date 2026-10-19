# @conexus/metro-ui

Kit W10M (Windows 10 Mobile / Metro) único dos apps Conexus em Expo. Fonte da verdade:
`conexus-platform/js/metro-ui`; os apps recebem uma cópia com `bin/conexus-vendor` — não edite a cópia.
Guia completo: `docs/js.md` da conexus-platform.

```tsx
import { MetroProvider, MetroScreen, MetroHeader, MetroPage, MetroButton } from "@conexus/metro-ui";

<MetroProvider accent="#1BA1E2">…</MetroProvider>
```

Peer deps: `react`, `react-native`, `react-native-safe-area-context`, `@expo/vector-icons`. Sem nativewind,
sem reanimated (animações com `Animated` + driver nativo, todas desligadas com "reduzir movimento").
