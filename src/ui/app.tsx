/**
 * Peças de tela do AntiSpam BR sobre o kit `@conexus/metro-ui` (fonte única do W10M nos apps Conexus).
 * Aqui ficam só o que é deste app: o esqueleto de página com o cabeçalho, o score e a linha rótulo/valor.
 */
import type { ReactNode } from 'react';
import { View, type StyleProp, type ViewStyle } from 'react-native';
import { MetroHeader, MetroPage, MetroProgress, MetroScreen, MetroText, spacing as kitSpacing, type MetroHeaderAction } from '@conexus/metro-ui';
import { scoreColor, spacing, useTheme } from './theme';

export const APP_KICKER = 'AntiSpam BR';

/**
 * Página W10M do app: área segura do topo, cabeçalho (kicker do app + título) e conteúdo rolável.
 * Os componentes do kit trazem a própria margem lateral; texto solto vai dentro de `MetroSection`.
 */
export function AppScreen({
  title,
  subtitle,
  actions,
  onBack,
  scroll = true,
  children,
}: {
  title: string;
  subtitle?: string;
  actions?: readonly MetroHeaderAction[];
  onBack?: () => void;
  scroll?: boolean;
  children: ReactNode;
}) {
  return (
    <MetroScreen edges={['top']}>
      <MetroHeader kicker={APP_KICKER} title={title} subtitle={subtitle} actions={actions} onBack={onBack} />
      {scroll ? (
        <MetroPage scroll padded={false}>
          <View style={{ gap: spacing.xl, paddingBottom: spacing.xl * 2 }}>{children}</View>
        </MetroPage>
      ) : (
        <View style={{ flex: 1 }}>{children}</View>
      )}
    </MetroScreen>
  );
}

/** Linhas do kit (lista, toggle, rádio, estados) dentro de uma `MetroSection`: anula a margem dupla. */
export function Flush({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[{ marginHorizontal: -kitSpacing.screen }, style]}>{children}</View>;
}

/** Valor grande e leve na cor da faixa de risco + barra plana determinada. */
export function MetroScore({ score }: { score: number }) {
  const t = useTheme();
  const color = scoreColor(score, t);
  return (
    <View style={{ minWidth: 96 }} accessible accessibilityLabel={`Score ${score} de 100`}>
      <MetroText variant="display" color={color}>{score}</MetroText>
      <MetroProgress value={score} color={color} />
    </View>
  );
}

/** Linha rótulo/valor. */
export function MetroRow({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: spacing.xs, gap: spacing.m }}>
      <MetroText variant="body" tone="muted" style={{ flexShrink: 1 }}>{label}</MetroText>
      <MetroText variant="body" color={color} style={{ flexShrink: 1, textAlign: 'right' }}>{value}</MetroText>
    </View>
  );
}
