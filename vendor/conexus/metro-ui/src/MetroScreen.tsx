import type { ReactNode } from "react";
import { View, type StyleProp, type ViewStyle } from "react-native";
import { SafeAreaView, type Edge } from "react-native-safe-area-context";

import { useMetro } from "./theme";
import { spacing } from "./tokens";

type Props = {
  readonly children: ReactNode;
  readonly edges?: readonly Edge[];
  /** Margem lateral W10M (12 dp). */
  readonly padded?: boolean;
  readonly style?: StyleProp<ViewStyle>;
  readonly testID?: string;
};

/**
 * Tela W10M: fundo do tema (preto/branco) e área segura. A barra de status segue o tema no layout raiz.
 * Combine com MetroHeader/MetroPivot + MetroPage (conteúdo).
 */
export function MetroScreen({ children, edges = ["top", "bottom"], padded = false, style, testID }: Props) {
  const { c } = useMetro();
  return (
    <SafeAreaView testID={testID} style={[{ flex: 1, backgroundColor: c.bg }, style]} edges={[...edges]}>
      <View style={{ flex: 1, paddingHorizontal: padded ? spacing.screen : 0 }}>{children}</View>
    </SafeAreaView>
  );
}
