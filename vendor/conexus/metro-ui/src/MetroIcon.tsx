import Ionicons from "@expo/vector-icons/Ionicons";
import { isValidElement, type ComponentProps, type ReactNode } from "react";
import { Pressable, StyleSheet, View } from "react-native";

import { useMetro } from "./theme";
import { minTouch } from "./tokens";

/** Nome de glifo Ionicons (prefira as variantes "-outline", de traço fino). */
export type MetroIconName = ComponentProps<typeof Ionicons>["name"];

/** Um nome Ionicons ou um elemento pronto (ex.: ícone phosphor/SVG do app). */
export type MetroIconSource = MetroIconName | ReactNode;

export type MetroIconTone = "text" | "muted" | "danger" | "onAccent" | "accent";

type Props = {
  readonly name: MetroIconName;
  readonly size?: number;
  /** Ícones ficam na cor do texto; destaque só no estado ativo. */
  readonly active?: boolean;
  readonly tone?: MetroIconTone;
  /** Cor exata (por exemplo a do rótulo de um botão pressionado). */
  readonly color?: string;
  readonly testID?: string;
};

/** Ícone monocromático W10M (Ionicons do @expo/vector-icons). Decorativo para leitores de tela. */
export function MetroIcon({ name, size = 20, active, tone = "text", color: exact, testID }: Props) {
  const { c, accent } = useMetro();
  const tones: Record<MetroIconTone, string> = {
    text: c.text,
    muted: c.muted,
    danger: c.danger,
    onAccent: c.onAccent,
    accent: accent.text,
  };
  const color = exact ?? (active ? accent.text : tones[tone]);
  return (
    <Ionicons
      name={name}
      size={size}
      color={color}
      testID={testID}
      accessibilityElementsHidden
      importantForAccessibility="no"
    />
  );
}

/** Renderiza um `MetroIconSource`: string vira MetroIcon; elemento passa direto. */
export function renderIcon(source: MetroIconSource, props: Omit<Props, "name"> = {}): ReactNode {
  if (source == null || source === false) return null;
  if (typeof source === "string") return <MetroIcon name={source as MetroIconName} {...props} />;
  return isValidElement(source) ? source : null;
}

type ButtonProps = {
  readonly icon: MetroIconSource;
  readonly onPress: () => void;
  readonly accessibilityLabel: string;
  readonly active?: boolean;
  readonly disabled?: boolean;
  readonly color?: string;
  readonly testID?: string;
};

/** Comando W10M: círculo fino em volta do ícone, alvo de 48 dp; inverte quando pressionado. */
export function MetroIconButton({ icon, onPress, accessibilityLabel, active, disabled, color, testID }: ButtonProps) {
  const { c, accent } = useMetro();
  const ink = disabled ? c.disabled : (color ?? (active ? accent.text : c.text));
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{ disabled: !!disabled, selected: !!active }}
      hitSlop={4}
      testID={testID}
      style={styles.target}
    >
      {({ pressed }) => (
        <View style={[styles.circle, { borderColor: ink }, pressed && { backgroundColor: ink }]}>
          {renderIcon(icon, { size: 18, color: pressed ? c.bg : ink })}
        </View>
      )}
    </Pressable>
  );
}

/**
 * Nomes do antigo catálogo `Glyph` (antispam-br) → Ionicons de traço fino, para migrar sem trocar cada chamada.
 */
export const GLYPHS = {
  shield: "shield-outline",
  history: "time-outline",
  search: "search-outline",
  settings: "settings-outline",
  more: "ellipsis-horizontal",
  heart: "heart-outline",
  phone: "call-outline",
  phoneOff: "call-outline",
  lock: "lock-closed-outline",
  close: "close",
  check: "checkmark",
  warning: "warning-outline",
  add: "add",
  delete: "trash-outline",
  flag: "flag-outline",
  block: "ban-outline",
  copy: "copy-outline",
  bell: "notifications-outline",
  back: "arrow-back-outline",
  person: "person-outline",
  signout: "log-out-outline",
} as const satisfies Record<string, MetroIconName>;

export type GlyphName = keyof typeof GLYPHS;

/** Compatível com o `Glyph` do antispam-br (nome + cor obrigatória). */
export function Glyph({ name, size = 24, color }: { readonly name: GlyphName; readonly size?: number; readonly color: string }) {
  return <MetroIcon name={GLYPHS[name]} size={size} color={color} />;
}

const styles = StyleSheet.create({
  target: { width: minTouch, height: minTouch, alignItems: "center", justifyContent: "center" },
  // Círculo do comando da app bar: exceção ao "sem cantos", como no original.
  circle: { width: 34, height: 34, borderRadius: 17, borderWidth: 1.5, alignItems: "center", justifyContent: "center" },
});
