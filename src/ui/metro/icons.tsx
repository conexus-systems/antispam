/**
 * Ícone monocromático W10M com Fluent UI System Icons (MIT). Tamanho 24 px, cor por prop
 * (padrão: texto do tema). Decorativo para leitores de tela: o rótulo fica no controle.
 */
import Svg, { Path } from 'react-native-svg';
import { useTheme } from '../theme';
import { FLUENT_PATHS, type FluentName } from './fluentPaths';

type Props = {
  readonly name: FluentName;
  readonly size?: number;
  readonly color?: string;
};

export function FluentIcon({ name, size = 24, color }: Props) {
  const t = useTheme();
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" accessible={false} importantForAccessibility="no" accessibilityElementsHidden>
      <Path d={FLUENT_PATHS[name]} fill={color ?? t.text} />
    </Svg>
  );
}
