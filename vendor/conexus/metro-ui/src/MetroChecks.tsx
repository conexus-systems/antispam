import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";

import { MetroIcon } from "./MetroIcon";
import { MetroText } from "./MetroText";
import { usePressedRow } from "./motion";
import { useMetro } from "./theme";
import { borders, minTouch, spacing, toggleState } from "./tokens";

type ToggleProps = {
  readonly label: string;
  readonly description?: string;
  readonly value: boolean;
  readonly onValueChange: (value: boolean) => void;
  readonly disabled?: boolean;
  readonly testID?: string;
  readonly style?: StyleProp<ViewStyle>;
};

/**
 * Interruptor W10M: nome, descrição e, embaixo, trilho retangular 44×20 com borda de 2 dp e pino quadrado;
 * ligado = trilho no destaque com pino branco à direita; "Ativado/Desativado" ao lado. A linha toda é o alvo.
 */
export function MetroToggle({ label, description, value, onValueChange, disabled, testID, style }: ToggleProps) {
  const { c, accent } = useMetro();
  const pressedRow = usePressedRow();
  const ink = disabled ? c.disabled : c.text;
  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityLabel={[label, description].filter(Boolean).join(", ")}
      accessibilityState={{ checked: value, disabled: !!disabled }}
      accessibilityValue={{ text: toggleState(value) }}
      disabled={disabled}
      onPress={() => onValueChange(!value)}
      testID={testID}
      style={(state) => [styles.row, pressedRow(state), style]}
    >
      <MetroText tone={disabled ? "disabled" : "text"}>{label}</MetroText>
      {description ? <MetroText variant="caption">{description}</MetroText> : null}
      <View style={styles.switchLine}>
        <View
          style={[
            styles.track,
            value ? { backgroundColor: disabled ? c.disabled : accent.fill, borderColor: disabled ? c.disabled : accent.fill } : { borderColor: ink },
          ]}
        >
          <View style={[styles.knob, { backgroundColor: value ? c.onAccent : ink, alignSelf: value ? "flex-end" : "flex-start" }]} />
        </View>
        <MetroText tone={disabled ? "disabled" : "text"}>{toggleState(value)}</MetroText>
      </View>
    </Pressable>
  );
}

type CheckProps = {
  readonly label: string;
  readonly checked: boolean;
  readonly onChange: (checked: boolean) => void;
  readonly disabled?: boolean;
  readonly testID?: string;
  readonly style?: StyleProp<ViewStyle>;
};

/** Caixa de seleção W10M: quadrado com borda de 2 dp; marcada = preenchida no destaque com ✓ branco. */
export function MetroCheckbox({ label, checked, onChange, disabled, testID, style }: CheckProps) {
  const { c, accent } = useMetro();
  const pressedRow = usePressedRow();
  const ink = disabled ? c.disabled : c.text;
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={label}
      accessibilityState={{ checked, disabled: !!disabled }}
      disabled={disabled}
      onPress={() => onChange(!checked)}
      testID={testID}
      style={(state) => [styles.inline, pressedRow(state), style]}
    >
      <View style={[styles.box, checked ? { backgroundColor: accent.fill, borderColor: accent.fill } : { borderColor: ink }]}>
        {checked ? <MetroIcon name="checkmark" size={16} tone="onAccent" /> : null}
      </View>
      <MetroText tone={disabled ? "disabled" : "text"} style={styles.flex}>
        {label}
      </MetroText>
    </Pressable>
  );
}

type RadioProps = {
  readonly label: string;
  /** Linha secundária sob o rótulo. */
  readonly hint?: string;
  readonly selected: boolean;
  readonly onSelect: () => void;
  readonly disabled?: boolean;
  readonly testID?: string;
  readonly style?: StyleProp<ViewStyle>;
};

/** Botão de opção W10M: um dos poucos círculos do sistema (anel de 2 dp; selecionado = anel no destaque + ponto). */
export function MetroRadio({ label, hint, selected, onSelect, disabled, testID, style }: RadioProps) {
  const { c, accent } = useMetro();
  const pressedRow = usePressedRow();
  const ring = disabled ? c.disabled : selected ? accent.text : c.text;
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityLabel={hint ? `${label}, ${hint}` : label}
      accessibilityState={{ checked: selected, disabled: !!disabled }}
      disabled={disabled}
      onPress={onSelect}
      testID={testID}
      style={(state) => [styles.inline, pressedRow(state), style]}
    >
      <View style={[styles.ring, { borderColor: ring }]}>
        {selected ? <View style={[styles.dot, { backgroundColor: disabled ? c.disabled : c.text }]} /> : null}
      </View>
      <View style={styles.flex}>
        <MetroText tone={disabled ? "disabled" : "text"}>{label}</MetroText>
        {hint ? <MetroText variant="caption">{hint}</MetroText> : null}
      </View>
    </Pressable>
  );
}

export type MetroOption<T> = { readonly value: T; readonly label: string; readonly hint?: string; readonly disabled?: boolean };

type GroupProps<T> = {
  readonly options: readonly MetroOption<T>[];
  readonly value: T | null;
  readonly onChange: (value: T) => void;
  /** Nome do grupo para leitores de tela. */
  readonly accessibilityLabel?: string;
  readonly disabled?: boolean;
  readonly style?: StyleProp<ViewStyle>;
};

/** Grupo de botões de opção (radiogroup): um por linha, seleção única. */
export function MetroRadioGroup<T extends string | number>({ options, value, onChange, accessibilityLabel, disabled, style }: GroupProps<T>) {
  return (
    <View accessibilityRole="radiogroup" accessibilityLabel={accessibilityLabel} style={style}>
      {options.map((option) => (
        <MetroRadio
          key={String(option.value)}
          label={option.label}
          hint={option.hint}
          selected={option.value === value}
          disabled={disabled || option.disabled}
          onSelect={() => onChange(option.value)}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { minHeight: minTouch + 16, paddingVertical: spacing.sm, paddingHorizontal: spacing.screen, justifyContent: "center" },
  switchLine: { flexDirection: "row", alignItems: "center", gap: spacing.md, paddingTop: spacing.xs },
  track: { width: 44, height: 20, borderWidth: borders.control, padding: 3, justifyContent: "center" },
  knob: { width: 10, height: 10 },
  inline: { minHeight: minTouch, flexDirection: "row", alignItems: "center", gap: spacing.md, paddingHorizontal: spacing.screen },
  box: { width: 20, height: 20, borderWidth: borders.control, alignItems: "center", justifyContent: "center" },
  // Círculo do botão de opção: única exceção ao "sem cantos", como no original.
  ring: { width: 20, height: 20, borderRadius: 10, borderWidth: borders.control, alignItems: "center", justifyContent: "center" },
  dot: { width: 8, height: 8, borderRadius: 4 },
  flex: { flex: 1 },
});
