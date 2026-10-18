/**
 * Listas W10M: linhas planas sobre a página (sem cartões), título BODY/SUBTITLE + segunda linha
 * secundária, ícone opcional à esquerda, valor à direita, divisória discreta opcional.
 * MetroSection: cabeçalho de grupo (GROUP, no destaque) + conteúdo.
 */
import React from 'react';
import { Pressable, View, type ViewStyle } from 'react-native';
import { motion, spacing, touchTarget, useTheme } from '../theme';
import { Glyph, type GlyphName } from './Glyph';
import { useReduceMotion } from './motion';
import { MetroText } from './MetroText';

export function MetroSection({ title, children, style }: { title?: string; children: React.ReactNode; style?: ViewStyle }) {
  return (
    <View style={[{ gap: spacing.s }, style]}>
      {!!title && (
        <MetroText variant="group" tone="accent" accessibilityRole="header">{title}</MetroText>
      )}
      {children}
    </View>
  );
}

export function MetroDivider() {
  const t = useTheme();
  return <View style={{ height: 1, backgroundColor: t.border }} />;
}

export function MetroListItem({
  title,
  subtitle,
  value,
  valueColor,
  icon,
  iconColor,
  large,
  selected,
  onPress,
  onLongPress,
  right,
  divider,
  accessibilityHint,
}: {
  title: string;
  subtitle?: string;
  value?: string;
  valueColor?: string;
  icon?: GlyphName;
  iconColor?: string;
  /** Título em SUBTITLE (20) em vez de BODY. */
  large?: boolean;
  selected?: boolean;
  onPress?: () => void;
  onLongPress?: () => void;
  right?: React.ReactNode;
  divider?: boolean;
  accessibilityHint?: string;
}) {
  const t = useTheme();
  const reduce = useReduceMotion();
  const interactive = !!(onPress || onLongPress);
  const content = (pressed: boolean) => (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.m,
        minHeight: touchTarget,
        paddingVertical: spacing.s,
        borderBottomWidth: divider ? 1 : 0,
        borderBottomColor: t.border,
        borderLeftWidth: selected ? 4 : 0,
        borderLeftColor: t.primary,
        paddingLeft: selected ? spacing.s : 0,
        backgroundColor: pressed ? t.surface : 'transparent',
        transform: pressed && !reduce ? [{ scale: motion.pressScale }] : undefined,
      }}
    >
      {icon && <Glyph name={icon} color={iconColor ?? t.text} />}
      <View style={{ flex: 1 }}>
        <MetroText variant={large ? 'subtitle' : 'body'}>{title}</MetroText>
        {!!subtitle && <MetroText variant="caption">{subtitle}</MetroText>}
      </View>
      {!!value && (
        <MetroText variant="body" color={valueColor} style={{ flexShrink: 1, textAlign: 'right' }}>{value}</MetroText>
      )}
      {right}
    </View>
  );
  if (!interactive) return content(false);
  return (
    <Pressable
      onPress={onPress}
      onLongPress={onLongPress}
      accessibilityRole="button"
      accessibilityState={{ selected: !!selected }}
      accessibilityHint={accessibilityHint}
    >
      {({ pressed }) => content(pressed)}
    </Pressable>
  );
}

/** Linha rótulo/valor (substitui o antigo Row). */
export function MetroRow({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: spacing.xs, gap: spacing.m }}>
      <MetroText variant="body" tone="muted" style={{ flexShrink: 1 }}>{label}</MetroText>
      <MetroText variant="body" color={color} style={{ flexShrink: 1, textAlign: 'right' }}>{value}</MetroText>
    </View>
  );
}
