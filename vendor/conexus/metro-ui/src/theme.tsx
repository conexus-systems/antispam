import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { AccessibilityInfo, useColorScheme } from "react-native";

import { accentSet, DEFAULT_ACCENT, palettes, resolveScheme, type AccentSet, type Palette, type Scheme } from "./tokens";

export type MetroTheme = {
  readonly scheme: Scheme;
  readonly isDark: boolean;
  /** Paleta do tema (fundo, chrome, texto, secundário, divisória, cores semânticas…). */
  readonly c: Palette;
  /** Destaque: `fill` (preenchimento com texto branco) e `text` (texto/linha sobre o fundo). */
  readonly accent: AccentSet;
  /** Cor de destaque crua, como o app passou. */
  readonly accentHex: string;
  /** "Remover animações" do sistema (ou forçado no provider): tudo aparece direto no estado final. */
  readonly reduceMotion: boolean;
};

export function buildTheme(accent: string, scheme: Scheme, reduceMotion = false): MetroTheme {
  return {
    scheme,
    isDark: scheme === "dark",
    c: palettes[scheme],
    accent: accentSet(accent, scheme),
    accentHex: accent,
    reduceMotion,
  };
}

const MetroContext = createContext<MetroTheme>(buildTheme(DEFAULT_ACCENT, "dark"));

/** True enquanto "Remover animações"/"Reduzir movimento" do sistema estiver ligado. */
export function useSystemReduceMotion(): boolean {
  const [reduce, setReduce] = useState(false);
  useEffect(() => {
    let alive = true;
    AccessibilityInfo.isReduceMotionEnabled?.()
      .then((value) => {
        if (alive) setReduce(Boolean(value));
      })
      .catch(() => undefined);
    const subscription = AccessibilityInfo.addEventListener?.("reduceMotionChanged", (value: boolean) =>
      setReduce(Boolean(value)),
    );
    return () => {
      alive = false;
      subscription?.remove?.();
    };
  }, []);
  return reduce;
}

export type MetroThemeProviderProps = {
  /** Cor de destaque do app (uma essencial W10M; veja `accentFromBrand`). */
  readonly accent?: string;
  /** "system" (padrão) segue o aparelho, com escuro quando indefinido. */
  readonly scheme?: Scheme | "system";
  /** Força o "reduzir movimento" (testes, preferência do app). Padrão: o do sistema. */
  readonly reduceMotion?: boolean;
  readonly children: ReactNode;
};

export function MetroThemeProvider({ accent = DEFAULT_ACCENT, scheme = "system", reduceMotion, children }: MetroThemeProviderProps) {
  const system = useColorScheme();
  const systemReduce = useSystemReduceMotion();
  const resolved: Scheme = scheme === "system" ? resolveScheme(system) : scheme;
  const reduce = reduceMotion ?? systemReduce;
  const value = useMemo(() => buildTheme(accent, resolved, reduce), [accent, resolved, reduce]);
  return <MetroContext.Provider value={value}>{children}</MetroContext.Provider>;
}

/** Tema W10M atual. Fora de um provider devolve o tema escuro com o destaque padrão. */
export function useMetro(): MetroTheme {
  return useContext(MetroContext);
}

/** Atalho: o "reduzir movimento" efetivo do tema. */
export function useReduceMotion(): boolean {
  return useContext(MetroContext).reduceMotion;
}
