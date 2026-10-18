import { Modal, Pressable, StyleSheet, View, useWindowDimensions } from "react-native";

import { renderIcon, type MetroIconSource } from "./MetroIcon";
import { MetroText } from "./MetroText";
import { useMetro } from "./theme";
import { borders, minTouch, spacing } from "./tokens";

export type MetroMenuItem = {
  readonly label: string;
  readonly onPress: () => void;
  readonly icon?: MetroIconSource;
  readonly destructive?: boolean;
  readonly disabled?: boolean;
};

type Props = {
  readonly visible: boolean;
  readonly items: readonly MetroMenuItem[];
  readonly onClose: () => void;
  /** Ponto vertical (pageY) de onde o menu abre, por exemplo o do toque longo. */
  readonly anchorY?: number;
  /** "bottom" = encostado acima da app bar (menu "..."), a `bottomOffset` dp do pé da tela. */
  readonly position?: "anchor" | "bottom";
  readonly bottomOffset?: number;
};

const ITEM = minTouch;

/** Menu de contexto W10M: painel chrome plano com borda de 1 dp, itens de 48 dp, destrutivo em vermelho. */
export function MetroMenu({ visible, items, onClose, anchorY, position = "anchor", bottomOffset = 0 }: Props) {
  const { c } = useMetro();
  const { height } = useWindowDimensions();
  const panelHeight = items.length * ITEM + spacing.sm * 2;
  const top = Math.max(spacing.xl, Math.min((anchorY ?? height / 3) - ITEM / 2, height - panelHeight - spacing.xl));
  const place = position === "bottom" ? { bottom: bottomOffset, left: 0, right: 0 } : { top };
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose} statusBarTranslucent>
      <Pressable style={StyleSheet.absoluteFill} onPress={onClose} accessibilityRole="button" accessibilityLabel="Fechar menu" />
      <View
        accessibilityViewIsModal
        accessibilityLabel="Menu"
        style={[styles.panel, place, { backgroundColor: c.chrome, borderColor: c.divider }]}
      >
        {items.map((item) => (
          <Pressable
            key={item.label}
            accessibilityRole="menuitem"
            accessibilityState={{ disabled: !!item.disabled }}
            disabled={item.disabled}
            onPress={() => {
              onClose();
              item.onPress();
            }}
            style={({ pressed }) => [styles.item, pressed && { backgroundColor: c.pressed }]}
          >
            {item.icon ? renderIcon(item.icon, { size: 18, tone: item.destructive ? "danger" : "text" }) : null}
            <MetroText tone={item.disabled ? "disabled" : item.destructive ? "danger" : "text"}>{item.label}</MetroText>
          </Pressable>
        ))}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  panel: { position: "absolute", left: spacing.screen, right: spacing.screen, borderWidth: borders.panel, paddingVertical: spacing.sm },
  item: { minHeight: ITEM, flexDirection: "row", alignItems: "center", gap: spacing.md, paddingHorizontal: spacing.lg },
});
