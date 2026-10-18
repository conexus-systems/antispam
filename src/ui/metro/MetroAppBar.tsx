/**
 * App bar W10M (barra de comandos inferior) usada como tab bar do expo-router:
 * barra plana em chrome, botões circulares de traço fino; o ativo fica no destaque.
 * O botão "..." expande a barra mostrando os rótulos e os comandos secundários (menu),
 * como no Windows 10 Mobile. Acessível como lista de abas.
 */
import React, { useState } from 'react';
import { Pressable, View } from 'react-native';
import type { BottomTabBarProps } from 'expo-router/tabs';
import { spacing, useTheme } from '../theme';
import { Glyph, type GlyphName } from './Glyph';
import { MetroText } from './MetroText';
import type { MenuItem } from './MetroMenu';

const CIRCLE = 40;

function Circle({ icon, color, label }: { icon: GlyphName; color: string; label?: string }) {
  return (
    <View style={{ alignItems: 'center', gap: spacing.xs }}>
      <View
        style={{
          width: CIRCLE,
          height: CIRCLE,
          borderRadius: CIRCLE / 2,
          borderWidth: 1.5,
          borderColor: color,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Glyph name={icon} size={20} color={color} />
      </View>
      {!!label && (
        <MetroText variant="caption" color={color} numberOfLines={1}>{label}</MetroText>
      )}
    </View>
  );
}

export function MetroAppBar({
  state,
  descriptors,
  emitter,
  navigateToTab,
  insets,
  icons,
  menu = [],
}: BottomTabBarProps & { icons: Record<string, GlyphName>; menu?: MenuItem[] }) {
  const t = useTheme();
  const [expanded, setExpanded] = useState(false);

  return (
    <View style={{ backgroundColor: t.chrome, paddingBottom: insets.bottom }}>
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', paddingTop: spacing.s, paddingBottom: expanded ? spacing.s : spacing.xs }}>
        <View style={{ flex: 1, flexDirection: 'row', justifyContent: 'center', gap: spacing.l }} accessibilityRole="tablist">
          {state.routes.map((route, index) => {
            const focused = index === state.index;
            const { options } = descriptors[route.key];
            const label = typeof options.title === 'string' ? options.title : route.name;
            const color = focused ? t.primary : t.text;
            const onPress = () => {
              const event = emitter.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
              setExpanded(false);
              if (!focused && !event.defaultPrevented) navigateToTab(route.key);
            };
            return (
              <Pressable
                key={route.key}
                onPress={onPress}
                onLongPress={() => emitter.emit({ type: 'tabLongPress', target: route.key })}
                accessibilityRole="tab"
                accessibilityLabel={`${label}, aba ${index + 1} de ${state.routes.length}`}
                accessibilityState={{ selected: focused }}
                style={({ pressed }) => ({ minWidth: 56, minHeight: 44, alignItems: 'center', opacity: pressed ? 0.6 : 1 })}
              >
                <Circle icon={icons[route.name] ?? 'more'} color={color} label={expanded ? label : undefined} />
              </Pressable>
            );
          })}
        </View>
        <Pressable
          onPress={() => setExpanded((v) => !v)}
          accessibilityRole="button"
          accessibilityLabel={expanded ? 'Recolher barra' : 'Mais opções'}
          accessibilityState={{ expanded }}
          style={({ pressed }) => ({ width: 48, height: 44, alignItems: 'center', justifyContent: 'flex-start', paddingTop: spacing.xs, backgroundColor: pressed ? t.surface : 'transparent' })}
        >
          <Glyph name="more" color={t.text} />
        </Pressable>
      </View>
      {expanded && menu.length > 0 && (
        <View style={{ paddingBottom: spacing.s }} accessibilityLabel="Menu">
          {menu.map((it) => (
            <Pressable
              key={it.label}
              accessibilityRole="menuitem"
              onPress={() => { setExpanded(false); it.onPress(); }}
              style={({ pressed }) => ({ minHeight: 48, justifyContent: 'center', paddingHorizontal: t.gutter, backgroundColor: pressed ? t.surface : 'transparent' })}
            >
              <MetroText variant="body" color={it.destructive ? t.danger : t.text}>{it.label}</MetroText>
            </Pressable>
          ))}
        </View>
      )}
    </View>
  );
}
