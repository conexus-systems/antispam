import type { ReactNode } from "react";
import { Text, type StyleProp, type TextProps, type TextStyle } from "react-native";

import { useMetro } from "./theme";
import { type, type TypeStyle } from "./tokens";

export type MetroTone = "text" | "muted" | "accent" | "danger" | "success" | "warning" | "info" | "disabled" | "onAccent";

export type MetroTextProps = TextProps & {
  /** Estilo da escala W10M (padrão: body). */
  readonly variant?: TypeStyle;
  /** Cor do tema. Padrão: caption = muted, group = accent, o resto = text. */
  readonly tone?: MetroTone;
  /** Cor exata (sobrepõe `tone`), por exemplo a do rótulo de um botão pressionado. */
  readonly color?: string;
  readonly style?: StyleProp<TextStyle>;
  readonly children?: ReactNode;
};

/** Texto na escala W10M e nas cores do tema; telas não definem tamanho nem cor soltos. */
export function MetroText({ variant = "body", tone, color, style, children, ...rest }: MetroTextProps) {
  const { c, accent } = useMetro();
  const defaultTone: MetroTone = variant === "caption" ? "muted" : variant === "group" ? "accent" : "text";
  const tones: Record<MetroTone, string> = {
    text: c.text,
    muted: c.muted,
    accent: accent.text,
    danger: c.danger,
    success: c.success,
    warning: c.warning,
    info: c.info,
    disabled: c.disabled,
    onAccent: c.onAccent,
  };
  return (
    <Text style={[type[variant], { color: color ?? tones[tone ?? defaultTone] }, style]} {...rest}>
      {children}
    </Text>
  );
}
