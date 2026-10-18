/**
 * Controles de escolha W10M:
 * - MetroToggle: nome, descrição e o interruptor (trilho retangular 44×20 com borda de 2 dp e pino quadrado;
 *   preenchido no destaque quando ligado) com "Ativado/Desativado" ao lado. A linha inteira é o alvo.
 * - MetroCheckbox: quadrado com borda de 2 dp; marcado = preenchido no destaque com ✓.
 * - MetroRadio: círculo com anel; marcado = anel no destaque + ponto.
 */
import React, { useEffect, useRef } from 'react';
import { Animated, Pressable, View } from 'react-native';
import { motion, spacing, strokeWidth, touchTarget, useTheme } from '../theme';
import { Glyph } from './Glyph';
import { useReduceMotion } from './motion';
import { MetroText } from './MetroText';

export function MetroSwitch({ value, disabled }: { value: boolean; disabled?: boolean }) {
  const t = useTheme();
  const reduce = useReduceMotion();
  const pos = useRef(new Animated.Value(value ? 1 : 0)).current;
  useEffect(() => {
    if (reduce) { pos.setValue(value ? 1 : 0); return; }
    Animated.timing(pos, { toValue: value ? 1 : 0, duration: motion.quickMs + 40, useNativeDriver: true }).start();
  }, [value, reduce, pos]);
  const ink = disabled ? t.disabled : t.text;
  const W = 44, H = 20, K = 10, PAD = 3;
  return (
    <View
      style={{
        width: W,
        height: H,
        borderWidth: strokeWidth,
        borderColor: value ? (disabled ? t.disabled : t.accentFill) : ink,
        backgroundColor: value ? (disabled ? t.disabled : t.accentFill) : 'transparent',
        justifyContent: 'center',
      }}
    >
      <Animated.View
        style={{
          position: 'absolute',
          left: PAD,
          width: K,
          height: K,
          backgroundColor: value ? t.onAccent : ink,
          transform: [{ translateX: pos.interpolate({ inputRange: [0, 1], outputRange: [0, W - 2 * strokeWidth - 2 * PAD - K] }) }],
        }}
      />
    </View>
  );
}

export function MetroToggle({
  label,
  description,
  value,
  onValueChange,
  disabled,
}: {
  label?: string;
  description?: string;
  value: boolean;
  onValueChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  const state = value ? 'Ativado' : 'Desativado';
  return (
    <Pressable
      onPress={() => onValueChange(!value)}
      disabled={disabled}
      accessibilityRole="switch"
      accessibilityLabel={label ?? description}
      accessibilityState={{ checked: value, disabled: !!disabled }}
      style={{ minHeight: touchTarget, paddingVertical: spacing.s, gap: spacing.xs, opacity: disabled ? 0.4 : 1 }}
    >
      {!!label && <MetroText variant="body">{label}</MetroText>}
      {!!description && <MetroText variant="caption">{description}</MetroText>}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.m, marginTop: spacing.xs }}>
        <MetroSwitch value={value} disabled={disabled} />
        <MetroText variant="body">{state}</MetroText>
      </View>
    </Pressable>
  );
}

export function MetroCheckbox({
  label,
  checked,
  onChange,
  disabled,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  const t = useTheme();
  return (
    <Pressable
      onPress={() => onChange(!checked)}
      disabled={disabled}
      accessibilityRole="checkbox"
      accessibilityLabel={label}
      accessibilityState={{ checked, disabled: !!disabled }}
      style={{ minHeight: touchTarget, flexDirection: 'row', alignItems: 'center', gap: spacing.m, opacity: disabled ? 0.4 : 1 }}
    >
      <View
        style={{
          width: 20,
          height: 20,
          borderWidth: strokeWidth,
          borderColor: checked ? t.accentFill : t.text,
          backgroundColor: checked ? t.accentFill : 'transparent',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {checked && <Glyph name="check" size={16} color={t.onAccent} strokeWidth={2.2} />}
      </View>
      <MetroText variant="body" style={{ flex: 1 }}>{label}</MetroText>
    </Pressable>
  );
}

export function MetroRadio({
  label,
  description,
  selected,
  onSelect,
  disabled,
}: {
  label: string;
  description?: string;
  selected: boolean;
  onSelect: () => void;
  disabled?: boolean;
}) {
  const t = useTheme();
  return (
    <Pressable
      onPress={onSelect}
      disabled={disabled}
      accessibilityRole="radio"
      accessibilityLabel={description ? `${label}. ${description}` : label}
      accessibilityState={{ checked: selected, disabled: !!disabled }}
      style={({ pressed }) => ({
        minHeight: touchTarget,
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.m,
        paddingVertical: spacing.s,
        opacity: disabled ? 0.4 : 1,
        backgroundColor: pressed ? t.surface : 'transparent',
      })}
    >
      <View
        style={{
          width: 20,
          height: 20,
          borderRadius: 10,
          borderWidth: strokeWidth,
          borderColor: selected ? t.primary : t.text,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {selected && <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: t.text }} />}
      </View>
      <View style={{ flex: 1 }}>
        <MetroText variant="body">{label}</MetroText>
        {!!description && <MetroText variant="caption">{description}</MetroText>}
      </View>
    </Pressable>
  );
}
