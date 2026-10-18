/**
 * Campo de texto W10M: rótulo acima, caixa quadrada com borda de 2 dp; em foco fica branca com texto
 * preto e borda no destaque; ajuda/erro logo abaixo (erro em vermelho, anunciado).
 */
import React, { useState } from 'react';
import { TextInput, View, type TextInputProps, type ViewStyle } from 'react-native';
import { spacing, strokeWidth, touchTarget, useTheme } from '../theme';
import { MetroText } from './MetroText';

export function MetroTextField({
  label,
  help,
  error,
  containerStyle,
  editable = true,
  onFocus,
  onBlur,
  ...input
}: TextInputProps & { label?: string; help?: string; error?: string | null; containerStyle?: ViewStyle }) {
  const t = useTheme();
  const [focused, setFocused] = useState(false);
  const borderColor = error ? t.danger : focused ? t.accentFill : t.textDim;
  return (
    <View style={[{ gap: spacing.xs }, containerStyle]}>
      {!!label && <MetroText variant="caption" tone="text">{label}</MetroText>}
      <TextInput
        {...input}
        editable={editable}
        accessibilityLabel={input.accessibilityLabel ?? label ?? input.placeholder}
        onFocus={(e) => { setFocused(true); onFocus?.(e); }}
        onBlur={(e) => { setFocused(false); onBlur?.(e); }}
        placeholderTextColor={focused ? t.fieldFocusPlaceholder : t.textDim}
        selectionColor={t.accentFill}
        style={[
          t.type.body,
          {
            minHeight: touchTarget,
            borderWidth: strokeWidth,
            borderColor,
            borderRadius: 0,
            paddingHorizontal: spacing.s,
            paddingVertical: spacing.s,
            backgroundColor: focused ? t.fieldFocusBg : 'transparent',
            color: focused ? t.fieldFocusText : t.text,
            opacity: editable ? 1 : 0.4,
          },
        ]}
      />
      {!!error && (
        <MetroText variant="caption" tone="danger" accessibilityLiveRegion="polite">{error}</MetroText>
      )}
      {!error && !!help && <MetroText variant="caption">{help}</MetroText>}
    </View>
  );
}
