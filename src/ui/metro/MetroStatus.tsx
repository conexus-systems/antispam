/**
 * Indicadores e estados W10M, todos planos e quadrados:
 * - MetroTag: rótulo retangular (contorno ou preenchido) para status curtos.
 * - MetroScore: valor grande e leve na cor da faixa + barra plana determinada (substitui o anel).
 * - MetroNotice: aviso plano em chrome com barra lateral de 4 dp na cor semântica.
 * - MetroEmpty: estado vazio/erro (o que falta + ação opcional). MetroLoading: pontos W10M.
 */
import React from 'react';
import { View } from 'react-native';
import { forWhiteText, scoreColor, spacing, useTheme } from '../theme';
import { MetroButton } from './MetroButton';
import { MetroProgress } from './MetroProgress';
import { MetroText } from './MetroText';

export function MetroTag({ text, color, filled }: { text: string; color: string; filled?: boolean }) {
  const t = useTheme();
  const fill = forWhiteText(color);
  return (
    <View
      style={{
        backgroundColor: filled ? fill : 'transparent',
        borderColor: filled ? fill : color,
        borderWidth: 1,
        paddingHorizontal: spacing.s,
        paddingVertical: 2,
        alignSelf: 'flex-start',
      }}
    >
      <MetroText variant="caption" color={filled ? t.onAccent : color}>{text}</MetroText>
    </View>
  );
}

export function MetroScore({ score }: { score: number }) {
  const t = useTheme();
  const color = scoreColor(score, t);
  return (
    <View style={{ minWidth: 96 }} accessible accessibilityLabel={`Score ${score} de 100`}>
      <MetroText variant="display" color={color}>{score}</MetroText>
      <MetroProgress progress={score} color={color} />
    </View>
  );
}

export function MetroNotice({ title, children, tone = 'warn' }: { title: string; children: React.ReactNode; tone?: 'warn' | 'danger' | 'info' }) {
  const t = useTheme();
  const color = tone === 'danger' ? t.danger : tone === 'info' ? t.info : t.warn;
  return (
    <View
      accessibilityRole="alert"
      style={{ backgroundColor: t.chrome, borderLeftWidth: 4, borderLeftColor: color, padding: spacing.m, gap: spacing.xs }}
    >
      <MetroText variant="group" color={color}>{title}</MetroText>
      {typeof children === 'string' ? <MetroText variant="body" tone="muted">{children}</MetroText> : children}
    </View>
  );
}

export function MetroEmpty({ message, actionTitle, onAction }: { message: string; actionTitle?: string; onAction?: () => void }) {
  return (
    <View style={{ paddingVertical: spacing.xl, gap: spacing.m }}>
      <MetroText variant="subtitle" tone="muted">{message}</MetroText>
      {!!actionTitle && !!onAction && <MetroButton title={actionTitle} onPress={onAction} style={{ alignSelf: 'flex-start' }} />}
    </View>
  );
}

export function MetroLoading({ label = 'Carregando' }: { label?: string }) {
  return (
    <View style={{ paddingVertical: spacing.l, gap: spacing.s }}>
      <MetroProgress />
      <MetroText variant="caption">{label}</MetroText>
    </View>
  );
}
