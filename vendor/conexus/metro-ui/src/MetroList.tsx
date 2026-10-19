import { useState, type ReactNode } from "react";
import {
  Pressable,
  StyleSheet,
  View,
  type AccessibilityActionEvent,
  type GestureResponderEvent,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from "react-native";

import { MetroButton } from "./MetroButton";
import { renderIcon, type MetroIconSource } from "./MetroIcon";
import { MetroMenu } from "./MetroMenu";
import { MetroText } from "./MetroText";
import { usePressedRow } from "./motion";
import { useMetro } from "./theme";
import { minTouch, spacing } from "./tokens";

export type MetroListAction = {
  readonly label: string;
  readonly onPress: () => void;
  readonly accessibilityLabel?: string;
  /** Destaca a ação (botão primário em vez de link). */
  readonly primary?: boolean;
};

type ItemProps = {
  readonly title: string;
  /** Linha secundária (até 2 linhas). */
  readonly subtitle?: string;
  /** Status curto à direita (em destaque quando `metaTone="accent"`). */
  readonly meta?: string;
  readonly metaTone?: "muted" | "accent" | "danger";
  /** Data/hora curta à direita, sob o status. */
  readonly time?: string;
  /** Glifo Ionicons de traço fino à esquerda (ou um elemento pronto). */
  readonly icon?: MetroIconSource;
  readonly iconTone?: "text" | "danger";
  /** Título grande (SUBTITLE 20) em vez de BODY. */
  readonly large?: boolean;
  readonly destructive?: boolean;
  /** Linha selecionada: barra de 4 dp no destaque à esquerda (seleção múltipla, item atual). */
  readonly selected?: boolean;
  readonly onPress?: () => void;
  /**
   * Ações da linha: aparecem como links embaixo, no menu de contexto (toque longo) e como ações do leitor de
   * tela — nada depende só do gesto.
   */
  readonly actions?: readonly MetroListAction[];
  readonly trailing?: ReactNode;
  readonly children?: ReactNode;
  readonly accessibilityLabel?: string;
  readonly accessibilityHint?: string;
  readonly accessibilityRole?: "button" | "link" | "text";
  readonly disabled?: boolean;
  readonly testID?: string;
  readonly style?: StyleProp<ViewStyle>;
};

/** Linha de lista W10M: plana sobre a página, sem cartão; ícone, título, linha secundária e status à direita. */
export function MetroListItem({
  title,
  subtitle,
  meta,
  metaTone = "muted",
  time,
  icon,
  iconTone = "text",
  large,
  destructive,
  selected,
  onPress,
  actions,
  trailing,
  children,
  accessibilityLabel,
  accessibilityHint,
  accessibilityRole,
  disabled,
  testID,
  style,
}: ItemProps) {
  const pressedRow = usePressedRow();
  const { accent } = useMetro();
  const [menuY, setMenuY] = useState<number | null>(null);
  const hasActions = !!actions?.length;
  const label = accessibilityLabel ?? [title, subtitle, meta, time].filter(Boolean).join(", ");
  const onAction = (event: AccessibilityActionEvent) =>
    actions?.find((action) => action.label === event.nativeEvent.actionName)?.onPress();
  const interactive = !!onPress || hasActions;

  return (
    <View style={[styles.item, style]} testID={testID}>
      <Pressable
        accessibilityRole={accessibilityRole ?? (onPress ? "button" : "text")}
        accessibilityLabel={label}
        accessibilityHint={accessibilityHint ?? (hasActions ? "Toque longo para mais ações" : undefined)}
        accessibilityState={{ disabled: !!disabled, selected: !!selected }}
        accessibilityActions={actions?.map((action) => ({ name: action.label, label: action.accessibilityLabel ?? action.label }))}
        onAccessibilityAction={hasActions ? onAction : undefined}
        onPress={onPress}
        onLongPress={hasActions ? (event: GestureResponderEvent) => setMenuY(event.nativeEvent.pageY) : undefined}
        disabled={disabled || !interactive}
        style={(state) => [styles.row, pressedRow(state), disabled && { opacity: 0.4 }]}
      >
        {selected ? <View style={[styles.selectedBar, { backgroundColor: accent.fill }]} /> : null}
        {icon != null ? <View style={styles.icon}>{renderIcon(icon, { size: 24, tone: iconTone })}</View> : null}
        <View style={styles.text}>
          <MetroText variant={large ? "subtitle" : "body"} tone={destructive ? "danger" : "text"} numberOfLines={2}>
            {title}
          </MetroText>
          {subtitle ? (
            <MetroText variant="body" tone="muted" numberOfLines={2}>
              {subtitle}
            </MetroText>
          ) : null}
        </View>
        {meta || time ? (
          <View style={styles.side}>
            {meta ? (
              <MetroText variant="caption" tone={metaTone} style={styles.right}>
                {meta}
              </MetroText>
            ) : null}
            {time ? (
              <MetroText variant="caption" style={styles.right}>
                {time}
              </MetroText>
            ) : null}
          </View>
        ) : null}
        {trailing}
      </Pressable>
      {children ? <View style={[styles.extra, icon == null && styles.flush]}>{children}</View> : null}
      {hasActions ? (
        <View style={[styles.actions, icon == null && styles.flushActions]}>
          {actions!.map((action) => (
            <MetroButton
              key={action.label}
              label={action.label}
              variant={action.primary ? "primary" : "text"}
              accessibilityLabel={action.accessibilityLabel}
              onPress={action.onPress}
            />
          ))}
        </View>
      ) : null}
      {hasActions ? (
        <MetroMenu
          visible={menuY != null}
          anchorY={menuY ?? undefined}
          onClose={() => setMenuY(null)}
          items={actions!.map((action) => ({ label: action.label, onPress: action.onPress }))}
        />
      ) : null}
    </View>
  );
}

/** Cabeçalho de grupo de lista W10M: GROUP 16 seminegrito no destaque. */
export function MetroGroupHeader({ title, style }: { readonly title: string; readonly style?: StyleProp<TextStyle> }) {
  return (
    <MetroText variant="group" accessibilityRole="header" style={[styles.group, style]}>
      {title}
    </MetroText>
  );
}

/** Seção de página: cabeçalho de grupo + conteúdo com a margem lateral W10M. */
export function MetroSection({
  title,
  children,
  style,
}: {
  readonly title?: string;
  readonly children: ReactNode;
  readonly style?: StyleProp<ViewStyle>;
}) {
  return (
    <View style={[styles.section, style]}>
      {title ? <MetroGroupHeader title={title} /> : null}
      <View style={styles.sectionBody}>{children}</View>
    </View>
  );
}

/** Divisória discreta e opcional entre linhas. */
export function MetroDivider({ style }: { readonly style?: StyleProp<ViewStyle> }) {
  const { c } = useMetro();
  return <View style={[{ height: StyleSheet.hairlineWidth, backgroundColor: c.divider }, style]} />;
}

const styles = StyleSheet.create({
  item: {},
  row: {
    minHeight: minTouch + 8,
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.screen,
    gap: spacing.md,
  },
  icon: { width: 32, alignItems: "center" },
  selectedBar: { position: "absolute", left: 0, top: spacing.xs, bottom: spacing.xs, width: 4 },
  text: { flex: 1 },
  side: { alignItems: "flex-end", maxWidth: "40%" },
  right: { textAlign: "right" },
  extra: { paddingHorizontal: spacing.screen, paddingLeft: spacing.screen + 32 + spacing.md, paddingBottom: spacing.sm },
  actions: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: spacing.sm,
    paddingLeft: spacing.screen + 32 + spacing.md - spacing.sm,
    paddingRight: spacing.screen,
    paddingBottom: spacing.sm,
  },
  flush: { paddingLeft: spacing.screen },
  flushActions: { paddingLeft: spacing.screen - spacing.sm },
  group: { paddingTop: spacing.xl, paddingBottom: spacing.xs },
  section: { paddingHorizontal: spacing.screen },
  sectionBody: { gap: spacing.md },
});
