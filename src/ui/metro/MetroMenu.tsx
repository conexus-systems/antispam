/**
 * Menu de contexto W10M: lista plana em painel chrome com borda de 1 dp, itens de 48 dp,
 * destrutivo em vermelho. Abre preso à borda inferior (acima da app bar) ou ao topo; Voltar fecha.
 */
import React from 'react';
import { Animated, Modal, Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { spacing, useTheme } from '../theme';
import { Glyph, type GlyphName } from './Glyph';
import { useEnterAnimation } from './MetroPage';
import { MetroText } from './MetroText';
import { useReduceMotion } from './motion';

export type MenuItem = { label: string; onPress: () => void; icon?: GlyphName; destructive?: boolean; disabled?: boolean };

function Items({ items, onClose }: { items: MenuItem[]; onClose: () => void }) {
  const t = useTheme();
  const enter = useEnterAnimation();
  return (
    <Animated.View
      accessibilityViewIsModal
      accessibilityLabel="Menu"
      style={[{ backgroundColor: t.chrome, borderWidth: 1, borderColor: t.border, paddingVertical: spacing.s }, enter]}
    >
      {items.map((it) => {
        const color = it.destructive ? t.danger : t.text;
        return (
          <Pressable
            key={it.label}
            disabled={it.disabled}
            accessibilityRole="menuitem"
            accessibilityState={{ disabled: !!it.disabled }}
            onPress={() => { onClose(); it.onPress(); }}
            style={({ pressed }) => ({
              minHeight: 48,
              flexDirection: 'row',
              alignItems: 'center',
              gap: spacing.m,
              paddingHorizontal: t.gutter,
              opacity: it.disabled ? 0.4 : 1,
              backgroundColor: pressed ? t.surface : 'transparent',
            })}
          >
            {it.icon && <Glyph name={it.icon} size={20} color={color} />}
            <MetroText variant="body" color={color}>{it.label}</MetroText>
          </Pressable>
        );
      })}
    </Animated.View>
  );
}

export function MetroMenu({
  visible,
  items,
  onClose,
  position = 'bottom',
  offset = 0,
}: {
  visible: boolean;
  items: MenuItem[];
  onClose: () => void;
  position?: 'top' | 'bottom';
  /** Distância da borda (ex.: altura da app bar). */
  offset?: number;
}) {
  const insets = useSafeAreaInsets();
  const reduce = useReduceMotion();
  return (
    <Modal visible={visible} transparent statusBarTranslucent animationType={reduce ? 'none' : 'fade'} onRequestClose={onClose}>
      <View style={{ flex: 1, justifyContent: position === 'bottom' ? 'flex-end' : 'flex-start' }}>
        <Pressable style={{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }} onPress={onClose} accessibilityLabel="Fechar menu" />
        <View style={position === 'bottom' ? { marginBottom: offset + insets.bottom } : { marginTop: offset + insets.top }}>
          {visible && <Items items={items} onClose={onClose} />}
        </View>
      </View>
    </Modal>
  );
}
