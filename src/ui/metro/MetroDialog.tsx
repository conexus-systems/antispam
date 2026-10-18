/**
 * Diálogo W10M: no telefone ocupa a largura no topo da tela (como no Lumia), sobre fundo escurecido 60%;
 * painel chrome plano com título SUBTITLE, mensagem, conteúdo e botões lado a lado.
 * O primário fica vermelho quando `destructive`. Voltar fecha (onRequestClose).
 */
import React from 'react';
import { Animated, Modal, Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { spacing, useTheme } from '../theme';
import { useEnterAnimation } from './MetroPage';
import { MetroButton } from './MetroButton';
import { MetroText } from './MetroText';
import { useReduceMotion } from './motion';

export type DialogButton = { title: string; onPress: () => void; primary?: boolean; destructive?: boolean };

function Panel({ title, message, children, buttons }: { title: string; message?: string; children?: React.ReactNode; buttons: DialogButton[] }) {
  const t = useTheme();
  const insets = useSafeAreaInsets();
  const enter = useEnterAnimation();
  return (
    <Animated.View
      accessibilityViewIsModal
      style={[{ backgroundColor: t.chrome, paddingTop: insets.top + spacing.l, paddingHorizontal: t.gutter, paddingBottom: spacing.l, maxHeight: '85%' }, enter]}
    >
      <MetroText variant="subtitle" accessibilityRole="header">{title}</MetroText>
      <ScrollView style={{ marginTop: spacing.s }} contentContainerStyle={{ gap: spacing.m }}>
        {!!message && <MetroText variant="body">{message}</MetroText>}
        {children}
      </ScrollView>
      {buttons.length > 0 && (
        <View style={{ flexDirection: 'row', gap: spacing.s, marginTop: spacing.l }}>
          {buttons.map((b) => (
            <View key={b.title} style={{ flex: 1 }}>
              <MetroButton
                title={b.title}
                onPress={b.onPress}
                variant={b.destructive ? 'danger' : b.primary ? 'primary' : 'secondary'}
              />
            </View>
          ))}
        </View>
      )}
    </Animated.View>
  );
}

export function MetroDialog({
  visible,
  title,
  message,
  children,
  buttons = [],
  onRequestClose,
}: {
  visible: boolean;
  title: string;
  message?: string;
  children?: React.ReactNode;
  buttons?: DialogButton[];
  onRequestClose: () => void;
}) {
  const t = useTheme();
  const reduce = useReduceMotion();
  return (
    <Modal visible={visible} transparent statusBarTranslucent animationType={reduce ? 'none' : 'fade'} onRequestClose={onRequestClose}>
      <View style={{ flex: 1, backgroundColor: t.scrim }}>
        {visible && <Panel title={title} message={message} buttons={buttons}>{children}</Panel>}
        <Pressable style={{ flex: 1 }} onPress={onRequestClose} accessibilityLabel="Fechar" accessibilityRole="button" />
      </View>
    </Modal>
  );
}
