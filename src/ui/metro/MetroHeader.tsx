/**
 * Cabeçalho W10M: nome do app em maiúsculas (kicker) + título grande e leve à esquerda,
 * subtítulo opcional e até 2 ações de ícone à direita. O título é anunciado como cabeçalho.
 */
import React from 'react';
import { Pressable, View } from 'react-native';
import { spacing, touchTarget, useTheme } from '../theme';
import { Glyph, type GlyphName } from './Glyph';
import { MetroText } from './MetroText';

export const APP_KICKER = 'AntiSpam BR';

export type HeaderAction = { icon: GlyphName; label: string; onPress: () => void };

export function MetroHeader({
  title,
  kicker = APP_KICKER,
  subtitle,
  actions = [],
}: {
  title?: string;
  kicker?: string;
  subtitle?: string;
  actions?: HeaderAction[];
}) {
  const t = useTheme();
  return (
    <View style={{ paddingHorizontal: t.gutter, paddingTop: spacing.m, paddingBottom: spacing.m }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', minHeight: touchTarget }}>
        <MetroText variant="kicker" style={{ flex: 1 }}>{kicker}</MetroText>
        {actions.slice(0, 2).map((a) => (
          <Pressable
            key={a.label}
            onPress={a.onPress}
            accessibilityRole="button"
            accessibilityLabel={a.label}
            hitSlop={4}
            style={({ pressed }) => ({
              width: touchTarget,
              height: touchTarget,
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: pressed ? t.surface : 'transparent',
            })}
          >
            <Glyph name={a.icon} color={t.text} />
          </Pressable>
        ))}
      </View>
      {!!title && (
        <MetroText variant="header" accessibilityRole="header" numberOfLines={2}>
          {title}
        </MetroText>
      )}
      {!!subtitle && <MetroText variant="caption" style={{ marginTop: spacing.xs }}>{subtitle}</MetroText>}
    </View>
  );
}
