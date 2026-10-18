import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";

import { MetroIcon, renderIcon, type MetroIconSource } from "./MetroIcon";
import { MetroText } from "./MetroText";
import { MetroEnter, usePressedRow } from "./motion";
import { minTouch, spacing } from "./tokens";

export type MetroHeaderAction = {
  readonly icon: MetroIconSource;
  /** Rótulo para leitores de tela. */
  readonly label: string;
  readonly onPress: () => void;
  readonly testID?: string;
};

type Props = {
  readonly title: string;
  /** Nome do app, em maiúsculas acima do título. */
  readonly kicker?: string;
  readonly subtitle?: string;
  /** Até 2 ações de ícone à direita do título. */
  readonly actions?: readonly MetroHeaderAction[];
  /** Mostra "Voltar" (seta fina) na linha do kicker; o gesto e a tecla Voltar continuam valendo. */
  readonly onBack?: () => void;
  readonly animate?: boolean;
  readonly style?: StyleProp<ViewStyle>;
};

/** Cabeçalho de página W10M: kicker em maiúsculas + título grande e leve à esquerda; entra subindo. */
export function MetroHeader({ title, kicker, subtitle, actions = [], onBack, animate = true, style }: Props) {
  const pressedRow = usePressedRow();
  const content = (
    <View style={[styles.root, style]}>
      {(kicker || onBack) && (
        <View style={styles.kickerRow}>
          {onBack ? (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Voltar"
              onPress={onBack}
              hitSlop={8}
              style={(state) => [styles.back, pressedRow(state)]}
            >
              <MetroIcon name="arrow-back-outline" size={20} />
            </Pressable>
          ) : null}
          {kicker ? <MetroText variant="kicker">{kicker}</MetroText> : null}
        </View>
      )}
      <View style={styles.titleRow}>
        <MetroText variant="header" accessibilityRole="header" numberOfLines={2} style={styles.title}>
          {title}
        </MetroText>
        {actions.slice(0, 2).map((action) => (
          <Pressable
            key={action.label}
            testID={action.testID}
            accessibilityRole="button"
            accessibilityLabel={action.label}
            onPress={action.onPress}
            style={(state) => [styles.action, pressedRow(state)]}
          >
            {renderIcon(action.icon, { size: 22 })}
          </Pressable>
        ))}
      </View>
      {subtitle ? <MetroText variant="caption">{subtitle}</MetroText> : null}
    </View>
  );
  return animate ? <MetroEnter>{content}</MetroEnter> : content;
}

const styles = StyleSheet.create({
  root: { paddingHorizontal: spacing.screen, paddingTop: spacing.sm, paddingBottom: spacing.sm },
  kickerRow: { flexDirection: "row", alignItems: "center", minHeight: 32, gap: spacing.sm },
  back: { width: 32, height: 32, alignItems: "center", justifyContent: "center", marginLeft: -spacing.xs },
  titleRow: { flexDirection: "row", alignItems: "center" },
  title: { flex: 1 },
  action: { width: minTouch, height: minTouch, alignItems: "center", justifyContent: "center" },
});
