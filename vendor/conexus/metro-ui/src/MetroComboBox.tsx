import { useState } from "react";
import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";

import { MetroRadioGroup, type MetroOption } from "./MetroChecks";
import { MetroDialog } from "./MetroDialog";
import { MetroIcon } from "./MetroIcon";
import { MetroText } from "./MetroText";
import { useMetro } from "./theme";
import { borders, minTouch, spacing } from "./tokens";

type Props<T> = {
  readonly label: string;
  readonly options: readonly MetroOption<T>[];
  readonly value: T | null;
  readonly onChange: (value: T) => void;
  /** Texto quando nada está escolhido. */
  readonly placeholder?: string;
  readonly disabled?: boolean;
  readonly testID?: string;
  readonly style?: StyleProp<ViewStyle>;
};

/**
 * Caixa de combinação W10M: rótulo acima, retângulo de 2 dp com o valor atual e uma divisa. Tocar abre um
 * MetroDialog com botões de opção; escolher aplica na hora e fecha.
 */
export function MetroComboBox<T extends string | number>({ label, options, value, onChange, placeholder = "", disabled, testID, style }: Props<T>) {
  const { c } = useMetro();
  const [open, setOpen] = useState(false);
  const current = options.find((option) => option.value === value);
  const shown = current?.label ?? placeholder;
  return (
    <View style={[styles.wrap, style]}>
      <MetroText tone={disabled ? "disabled" : "text"} numberOfLines={1} style={styles.label}>
        {label}
      </MetroText>
      <Pressable
        onPress={() => setOpen(true)}
        disabled={disabled}
        testID={testID}
        accessibilityRole="combobox"
        accessibilityLabel={`${label}: ${shown}`}
        accessibilityHint="Abre as opções"
        accessibilityState={{ disabled: !!disabled, expanded: open }}
      >
        {({ pressed }) => (
          <View
            style={[
              styles.box,
              { borderColor: disabled ? c.disabled : c.muted },
              pressed && { backgroundColor: c.surface },
            ]}
          >
            <MetroText numberOfLines={1} tone={disabled ? "disabled" : current ? "text" : "muted"} style={styles.value}>
              {shown}
            </MetroText>
            <MetroIcon name="chevron-down" size={16} tone={disabled ? "muted" : "text"} />
          </View>
        )}
      </Pressable>
      <MetroDialog visible={open} title={label} onDismiss={() => setOpen(false)} buttons={[{ text: "Cancelar", style: "cancel" }]}>
        <MetroRadioGroup
          options={options}
          value={value}
          accessibilityLabel={label}
          onChange={(next) => {
            setOpen(false);
            onChange(next);
          }}
        />
      </MetroDialog>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { marginBottom: spacing.md },
  label: { marginBottom: spacing.xs },
  box: {
    minHeight: minTouch - 4,
    borderWidth: borders.control,
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: spacing.sm,
    gap: spacing.xs,
  },
  value: { flex: 1 },
});
