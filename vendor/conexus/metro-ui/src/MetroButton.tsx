import type { ReactNode } from "react";
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";

import { renderIcon, type MetroIconSource } from "./MetroIcon";
import { MetroProgress } from "./MetroProgress";
import { MetroText } from "./MetroText";
import { pressTransform } from "./motion";
import { useMetro } from "./theme";
import { borders, minTouch, spacing, type } from "./tokens";

export type MetroButtonVariant = "primary" | "secondary" | "danger" | "text";

type Props = {
  readonly label: string;
  readonly onPress?: () => void;
  /**
   * primary = preenchido no destaque; secondary = borda de 2 dp; danger = vermelho (destrutivo);
   * text = link no destaque, sem borda.
   */
  readonly variant?: MetroButtonVariant;
  readonly disabled?: boolean;
  readonly loading?: boolean;
  /** Glifo Ionicons à esquerda do rótulo (ou um elemento pronto). */
  readonly icon?: MetroIconSource;
  /** "small" = 36 dp de altura e CAPTION, para ações secundárias em linha. */
  readonly size?: "normal" | "small";
  /** Ocupa a largura do pai. */
  readonly block?: boolean;
  readonly accessibilityLabel?: string;
  readonly accessibilityHint?: string;
  readonly accessibilityRole?: "button" | "link";
  readonly testID?: string;
  readonly style?: StyleProp<ViewStyle>;
};

/**
 * Botão W10M: retângulo plano. Pressionado vira destaque com texto branco (o primário inverte para a cor do
 * texto); desabilitado com opacidade 0,4; carregando mostra os pontos W10M na cor do rótulo.
 */
export function MetroButton({
  label,
  onPress,
  variant = "secondary",
  disabled,
  loading,
  icon,
  size = "normal",
  block,
  accessibilityLabel,
  accessibilityHint,
  accessibilityRole = "button",
  testID,
  style,
}: Props) {
  const { c, accent, reduceMotion } = useMetro();
  const inactive = !!disabled || !!loading;
  const small = size === "small";

  const colors = (pressed: boolean) => {
    if (variant === "text") {
      return { bg: pressed ? c.pressed : "transparent", border: "transparent", fg: accent.text };
    }
    if (pressed) {
      if (variant === "primary") return { bg: c.text, border: c.text, fg: c.bg };
      if (variant === "danger") return { bg: c.text, border: c.text, fg: c.bg };
      return { bg: accent.fill, border: accent.fill, fg: c.onAccent };
    }
    if (variant === "primary") return { bg: accent.fill, border: accent.fill, fg: c.onAccent };
    if (variant === "danger") return { bg: c.dangerFill, border: c.dangerFill, fg: c.onAccent };
    return { bg: "transparent", border: c.text, fg: c.text };
  };

  return (
    <Pressable
      accessibilityRole={accessibilityRole}
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: inactive, busy: !!loading }}
      disabled={inactive}
      onPress={onPress}
      testID={testID}
      hitSlop={2}
      style={[block ? styles.block : styles.inline, style]}
    >
      {({ pressed }) => {
        const { bg, border, fg } = colors(pressed);
        return (
          <View
            style={[
              styles.button,
              variant === "text" && styles.textButton,
              small && styles.small,
              { backgroundColor: bg, borderColor: border },
              inactive && !loading && { opacity: 0.4 },
              pressTransform(pressed, reduceMotion),
            ]}
          >
            {loading ? (
              <MetroProgress color={fg} width={48} accessibilityLabel="Carregando" />
            ) : (
              <>
                {renderIcon(icon, { size: small ? 16 : 18, color: fg })}
                <MetroText style={[small ? type.caption : type.body, { color: fg }]} numberOfLines={1}>
                  {label}
                </MetroText>
              </>
            )}
          </View>
        );
      }}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  inline: { alignSelf: "flex-start", minHeight: minTouch - 4, justifyContent: "center" },
  block: { alignSelf: "stretch", minHeight: minTouch - 4, justifyContent: "center" },
  button: {
    minHeight: minTouch - 4,
    borderWidth: borders.control,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.sm,
  },
  textButton: { paddingHorizontal: spacing.sm },
  small: { minHeight: 36, paddingHorizontal: spacing.sm, gap: spacing.xs },
});

/** Botões lado a lado com 8 dp entre eles, quebrando linha quando não cabem (rodapé de formulário). */
export function MetroButtonRow({ children, style }: { readonly children: ReactNode; readonly style?: StyleProp<ViewStyle> }) {
  return <View style={[rowStyles.row, style]}>{children}</View>;
}

const rowStyles = StyleSheet.create({ row: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm, alignItems: "center" } });
