import type { ReactNode } from "react";
import { StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";

import { MetroButton, type MetroButtonVariant } from "./MetroButton";
import { MetroIcon, type MetroIconName } from "./MetroIcon";
import { MetroProgress } from "./MetroProgress";
import { MetroText } from "./MetroText";
import { useMetro } from "./theme";
import { borders, forWhiteText, spacing } from "./tokens";

type Action = { readonly label: string; readonly onPress: () => void };

/** Carregando: pontos W10M no topo da área e o texto ao lado de quem usa leitor de tela. */
export function MetroLoading({ label = "Carregando", style }: { readonly label?: string; readonly style?: StyleProp<ViewStyle> }) {
  const { c } = useMetro();
  return (
    <View style={[styles.loading, { backgroundColor: c.bg }, style]}>
      <MetroProgress accessibilityLabel={label} />
    </View>
  );
}

type MessageProps = {
  readonly message: string;
  readonly icon?: MetroIconName;
  readonly action?: Action;
  readonly style?: StyleProp<ViewStyle>;
};

/** Vazio: o que falta, em texto leve, e uma ação real opcional. */
export function MetroEmpty({ message, icon, action, style }: MessageProps) {
  return (
    <View style={[styles.block, style]}>
      {icon ? <MetroIcon name={icon} size={32} tone="muted" /> : null}
      <MetroText variant="subtitle" tone="muted">
        {message}
      </MetroText>
      {action ? <MetroButton label={action.label} onPress={action.onPress} /> : null}
    </View>
  );
}

/** Erro: mensagem em vermelho anunciada como alerta e "Tentar novamente". */
export function MetroError({ message, action, style }: MessageProps) {
  return (
    <View style={[styles.block, style]} accessibilityRole="alert">
      <View style={styles.errorLine}>
        <MetroIcon name="alert-circle-outline" size={20} tone="danger" />
        <MetroText tone="danger" style={styles.flex}>
          {message}
        </MetroText>
      </View>
      {action ? <MetroButton label={action.label} onPress={action.onPress} /> : null}
    </View>
  );
}

export type MetroStateAction = { readonly label: string; readonly onPress: () => void; readonly variant?: MetroButtonVariant };

type StateProps = {
  /** empty (padrão) | error | loading | success: escolhe o tom e o anúncio. */
  readonly kind?: "empty" | "error" | "loading" | "success";
  readonly title?: string;
  readonly message?: string;
  readonly actions?: readonly MetroStateAction[];
  /** Ocupa a página inteira e centraliza na vertical. */
  readonly fill?: boolean;
  readonly style?: StyleProp<ViewStyle>;
};

/**
 * Estado de página genérico (vazio, erro, carregando, sucesso/portão): título SUBTITLE, mensagem e ações reais,
 * alinhados à esquerda. A primeira ação é primária.
 */
export function MetroState({ kind = "empty", title, message, actions = [], fill = false, style }: StateProps) {
  const { c } = useMetro();
  const tone = kind === "error" ? "danger" : kind === "success" ? "success" : "text";
  return (
    <View
      style={[styles.block, fill && styles.fill, fill && { backgroundColor: c.bg }, style]}
      accessibilityRole={kind === "error" ? "alert" : undefined}
      accessibilityLiveRegion={kind === "error" || kind === "loading" ? "polite" : undefined}
    >
      {kind === "loading" ? <MetroProgress accessibilityLabel={title ?? message ?? "Carregando"} /> : null}
      {title ? (
        <MetroText variant="subtitle" tone={tone} accessibilityRole="header">
          {title}
        </MetroText>
      ) : null}
      {message ? <MetroText tone={kind === "error" && !title ? "danger" : "muted"}>{message}</MetroText> : null}
      {actions.map((action, index) => (
        <MetroButton
          key={action.label}
          label={action.label}
          onPress={action.onPress}
          variant={action.variant ?? (index === 0 ? "primary" : "secondary")}
        />
      ))}
    </View>
  );
}

/** Aviso plano em chrome com barra lateral de 4 dp na cor semântica. */
export function MetroNotice({
  title,
  children,
  tone = "warning",
  style,
}: {
  readonly title: string;
  readonly children?: ReactNode;
  readonly tone?: "warning" | "danger" | "info" | "success";
  readonly style?: StyleProp<ViewStyle>;
}) {
  const { c } = useMetro();
  const color = c[tone];
  return (
    <View accessibilityRole="alert" style={[styles.notice, { backgroundColor: c.chrome, borderLeftColor: color }, style]}>
      <MetroText variant="group" color={color}>
        {title}
      </MetroText>
      {typeof children === "string" ? <MetroText tone="muted">{children}</MetroText> : children}
    </View>
  );
}

/** Rótulo retangular (contorno ou preenchido) para status curtos. */
export function MetroTag({ text, color, filled }: { readonly text: string; readonly color?: string; readonly filled?: boolean }) {
  const { c, accent } = useMetro();
  const ink = color ?? accent.text;
  const fill = forWhiteText(color ?? accent.fill);
  return (
    <View style={[styles.tag, { backgroundColor: filled ? fill : "transparent", borderColor: filled ? fill : ink }]}>
      <MetroText variant="caption" color={filled ? c.onAccent : ink}>
        {text}
      </MetroText>
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1, justifyContent: "center" },
  notice: { borderLeftWidth: 4, padding: spacing.md, gap: spacing.xs },
  tag: { borderWidth: borders.panel, paddingHorizontal: spacing.sm, paddingVertical: 2, alignSelf: "flex-start" },
  loading: { flex: 1, paddingTop: spacing.xl, paddingHorizontal: spacing.screen },
  block: { gap: spacing.md, paddingHorizontal: spacing.screen, paddingVertical: spacing.xl, alignItems: "flex-start" },
  errorLine: { flexDirection: "row", gap: spacing.sm, alignItems: "flex-start" },
  flex: { flex: 1 },
});
