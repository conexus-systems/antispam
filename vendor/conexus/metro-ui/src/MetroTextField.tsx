import { forwardRef, useState, type ComponentRef } from "react";
import { StyleSheet, TextInput, View, type StyleProp, type TextInputProps, type ViewStyle } from "react-native";

import { MetroText } from "./MetroText";
import { useMetro } from "./theme";
import { borders, minTouch, spacing, type } from "./tokens";

type Props = TextInputProps & {
  readonly label?: string;
  readonly error?: string;
  readonly hint?: string;
  readonly containerStyle?: StyleProp<ViewStyle>;
};

/**
 * Campo W10M: rótulo acima, retângulo com borda de 2 dp; em foco fica branco com texto preto e borda no
 * destaque; erro em vermelho logo abaixo (região viva), ajuda em secundário.
 */
export const MetroTextField = forwardRef<ComponentRef<typeof TextInput>, Props>(function MetroTextField(
  { label, error, hint, containerStyle, style, onFocus, onBlur, accessibilityLabel, editable, ...input },
  ref,
) {
  const { c, accent } = useMetro();
  const [focused, setFocused] = useState(false);
  const disabled = editable === false;
  const borderColor = error ? c.danger : focused ? accent.fill : disabled ? c.disabled : c.muted;
  return (
    <View style={[styles.container, containerStyle]}>
      {label ? (
        <MetroText variant="body" tone={disabled ? "disabled" : "text"} style={styles.label}>
          {label}
        </MetroText>
      ) : null}
      <TextInput
        ref={ref}
        editable={editable}
        placeholderTextColor={focused ? c.fieldFocusMuted : c.muted}
        selectionColor={accent.fill}
        cursorColor={focused ? c.fieldFocusText : c.text}
        accessibilityLabel={accessibilityLabel ?? label}
        accessibilityHint={error ?? hint}
        accessibilityState={{ disabled }}
        onFocus={(event) => {
          setFocused(true);
          onFocus?.(event);
        }}
        onBlur={(event) => {
          setFocused(false);
          onBlur?.(event);
        }}
        style={[
          styles.input,
          type.body,
          {
            color: focused ? c.fieldFocusText : disabled ? c.disabled : c.text,
            backgroundColor: focused ? c.fieldFocusBg : "transparent",
            borderColor,
          },
          style,
        ]}
        {...input}
      />
      {error ? (
        <MetroText variant="caption" tone="danger" style={styles.below} accessibilityLiveRegion="polite" accessibilityRole="alert">
          {error}
        </MetroText>
      ) : hint ? (
        <MetroText variant="caption" style={styles.below}>
          {hint}
        </MetroText>
      ) : null}
    </View>
  );
});

const styles = StyleSheet.create({
  container: { marginBottom: spacing.md },
  label: { marginBottom: spacing.xs },
  input: { minHeight: minTouch - 4, borderWidth: borders.control, borderRadius: 0, paddingHorizontal: spacing.sm, paddingVertical: 6 },
  below: { marginTop: spacing.xs },
});
