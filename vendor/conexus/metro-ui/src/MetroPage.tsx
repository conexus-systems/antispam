import type { ReactElement, ReactNode } from "react";
import { ScrollView, StyleSheet, View, type RefreshControlProps, type StyleProp, type ViewStyle } from "react-native";

import { MetroEnter } from "./motion";
import { useMetro } from "./theme";
import { spacing } from "./tokens";

type Props = {
  readonly children: ReactNode;
  /** Envolve o conteúdo num ScrollView. */
  readonly scroll?: boolean;
  readonly refreshControl?: ReactElement<RefreshControlProps>;
  /** Margens laterais W10M + respiro embaixo (padrão: true). */
  readonly padded?: boolean;
  /** Centraliza na vertical (formulários curtos, confirmações, login). */
  readonly centered?: boolean;
  /** Entrada W10M (sobe 20 dp e aparece). Padrão: true. */
  readonly animate?: boolean;
  readonly keyboardShouldPersistTaps?: "always" | "never" | "handled";
  readonly contentStyle?: StyleProp<ViewStyle>;
  readonly style?: StyleProp<ViewStyle>;
  readonly testID?: string;
};

/**
 * Conteúdo de página W10M: fundo plano do tema, margem lateral de 12 dp, entrada animada (desligada com
 * "reduzir movimento"). Não cuida da área segura: use dentro de MetroScreen ou de uma tela com header.
 */
export function MetroPage({
  children,
  scroll = false,
  refreshControl,
  padded = true,
  centered = false,
  animate = true,
  keyboardShouldPersistTaps = "handled",
  contentStyle,
  style,
  testID,
}: Props) {
  const { c } = useMetro();
  const inner = [padded && styles.padded, centered && styles.centered, contentStyle];
  const body = (flex: boolean) =>
    animate ? <MetroEnter style={[flex && styles.flex, centered && styles.full]}>{children}</MetroEnter> : children;

  if (scroll) {
    return (
      <ScrollView
        testID={testID}
        style={[styles.flex, { backgroundColor: c.bg }, style]}
        contentContainerStyle={[styles.grow, inner]}
        refreshControl={refreshControl}
        keyboardShouldPersistTaps={keyboardShouldPersistTaps}
      >
        {body(false)}
      </ScrollView>
    );
  }
  return (
    <View testID={testID} style={[styles.flex, { backgroundColor: c.bg }, style]}>
      <View style={[styles.flex, inner]}>{body(true)}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  grow: { flexGrow: 1 },
  padded: { paddingHorizontal: spacing.screen, paddingTop: spacing.sm, paddingBottom: spacing.xxl },
  centered: { justifyContent: "center" },
  full: { width: "100%" },
});
