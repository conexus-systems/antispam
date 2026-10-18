/**
 * Botão W10M: retângulo. `secondary` = borda de 2 dp na cor do texto; `primary` = preenchido no destaque;
 * `danger` = vermelho; `text` = link. Pressionado: fundo no destaque (invertido) e escala 0,98;
 * desabilitado: opacidade 0,4; carregando: pontos W10M na cor do texto.
 */
import React from 'react';
import { Pressable, View, type ViewStyle } from 'react-native';
import { motion, spacing, strokeWidth, touchTarget, useTheme } from '../theme';
import { Glyph, type GlyphName } from './Glyph';
import { useReduceMotion } from './motion';
import { MetroProgress } from './MetroProgress';
import { MetroText } from './MetroText';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'text';

export function MetroButton({
  title,
  onPress,
  variant = 'secondary',
  disabled,
  loading,
  small,
  icon,
  style,
  accessibilityLabel,
}: {
  title: string;
  onPress: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
  small?: boolean;
  icon?: GlyphName;
  style?: ViewStyle;
  accessibilityLabel?: string;
}) {
  const t = useTheme();
  const reduce = useReduceMotion();
  const inactive = disabled || loading;
  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      accessibilityRole={variant === 'text' ? 'link' : 'button'}
      accessibilityLabel={accessibilityLabel ?? title}
      accessibilityState={{ disabled: !!inactive, busy: !!loading }}
      style={({ pressed }) => {
        const base: ViewStyle = {
          minHeight: touchTarget,
          paddingHorizontal: variant === 'text' ? 0 : small ? spacing.m : spacing.l,
          paddingVertical: small ? spacing.xs : spacing.s,
          alignItems: variant === 'text' ? 'flex-start' : 'center',
          justifyContent: 'center',
          borderWidth: variant === 'text' ? 0 : strokeWidth,
          opacity: disabled ? 0.4 : 1,
          transform: pressed && !reduce ? [{ scale: motion.pressScale }] : undefined,
        };
        if (variant === 'primary') Object.assign(base, { backgroundColor: pressed ? t.text : t.accentFill, borderColor: pressed ? t.text : t.accentFill });
        else if (variant === 'danger') Object.assign(base, { backgroundColor: pressed ? t.text : t.dangerFill, borderColor: pressed ? t.text : t.dangerFill });
        else if (variant === 'secondary') Object.assign(base, { backgroundColor: pressed ? t.accentFill : 'transparent', borderColor: pressed ? t.accentFill : t.text });
        return [base, style];
      }}
    >
      {({ pressed }) => {
        let fg = t.text;
        if (variant === 'primary' || variant === 'danger') fg = pressed ? t.bg : t.onAccent;
        else if (variant === 'secondary') fg = pressed ? t.onAccent : t.text;
        else fg = pressed ? t.textDim : t.primary;
        if (loading) {
          return (
            <View style={{ alignSelf: 'stretch', paddingHorizontal: spacing.s }}>
              <MetroProgress color={fg} />
            </View>
          );
        }
        return (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.s }}>
            {icon && <Glyph name={icon} size={20} color={fg} />}
            <MetroText variant="body" color={fg} style={{ textAlign: 'center' }}>{title}</MetroText>
          </View>
        );
      }}
    </Pressable>
  );
}

/** Botão só de ícone (44 dp), sem moldura; pressionado ganha a superfície. Sempre com rótulo acessível. */
export function MetroIconButton({ icon, label, onPress, color }: { icon: GlyphName; label: string; onPress: () => void; color?: string }) {
  const t = useTheme();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => ({
        width: touchTarget,
        height: touchTarget,
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: pressed ? t.surface : 'transparent',
      })}
    >
      <Glyph name={icon} size={20} color={color ?? t.text} />
    </Pressable>
  );
}
