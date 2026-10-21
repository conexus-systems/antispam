/**
 * Amostra quadrada de cor de destaque (Ajustes › Aparência). 48 dp de alvo, sem arredondamento.
 * Selecionada: borda de 2 dp na cor do texto, marca de seleção no canto. Função de rádio para TalkBack.
 */
import { Pressable, StyleSheet, View } from 'react-native';
import { useTheme } from '../theme';
import { FluentIcon } from './icons';

type Props = {
  readonly label: string;
  readonly hex: string;
  readonly selected: boolean;
  readonly onSelect: () => void;
  readonly disabled?: boolean;
};

export function AccentSwatch({ label, hex, selected, onSelect, disabled }: Props) {
  const t = useTheme();
  return (
    <Pressable
      onPress={onSelect}
      disabled={disabled}
      accessibilityRole="radio"
      accessibilityLabel={label}
      accessibilityState={{ selected, disabled: !!disabled }}
      style={({ pressed }) => [styles.target, { opacity: pressed ? 0.7 : 1 }]}
    >
      <View style={[styles.fill, { backgroundColor: hex, borderColor: selected ? t.text : 'transparent' }]}>
        {selected ? <FluentIcon name="check" size={20} color={t.onAccent} /> : null}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  target: { width: 56, height: 56, alignItems: 'center', justifyContent: 'center' },
  fill: { width: 48, height: 48, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
});
