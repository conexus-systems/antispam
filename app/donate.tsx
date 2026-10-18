import { useMemo, useState } from 'react';
import { Alert, Clipboard, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { spacing, useTheme } from '../src/ui/theme';
import { MetroButton, MetroHeader, MetroPage, MetroSection, MetroTag, MetroText, MetroTextField } from '../src/ui/metro';
import { buildPixPayload } from '../src/core/donation/pix';

const AMOUNTS = [5, 10, 20];

export default function Donate() {
  const theme = useTheme();
  const [amount, setAmount] = useState<number>(10);
  const [custom, setCustom] = useState('');
  const pixKey = process.env.EXPO_PUBLIC_DONATION_PIX_KEY ?? '';

  const value = custom ? Number(custom.replace(',', '.')) : amount;
  const payload = useMemo(() => {
    if (!pixKey || !value || value <= 0) return null;
    try {
      return buildPixPayload({
        key: pixKey,
        merchantName: 'AntiSpam BR',
        merchantCity: 'BRASIL',
        amount: value,
        txid: 'ANTISPAMBR',
      });
    } catch {
      return null;
    }
  }, [pixKey, value]);

  function copy() {
    if (!payload) {
      Alert.alert('Indisponível', 'Configure EXPO_PUBLIC_DONATION_PIX_KEY no build para habilitar doações.');
      return;
    }
    Clipboard.setString(payload);
    Alert.alert('PIX Copia e Cola copiado!', 'Cole no app do seu banco para doar. Obrigado por apoiar! ❤️');
  }

  return (
    <MetroPage header={<MetroHeader title="Apoie o projeto" />}>
      <MetroSection>
        <MetroText variant="body">
          O AntiSpam BR é gratuito, open source, sem anúncios e sem venda de dados. Se ele estiver sendo útil,
          você pode ajudar voluntariamente a manter o desenvolvimento e a base de spam comunitária.
        </MetroText>
        <View style={{ flexDirection: 'row', gap: spacing.xs, flexWrap: 'wrap' }}>
          <MetroTag text="nunca obrigatória" color={theme.primary} />
          <MetroTag text="não desbloqueia recursos" color={theme.primary} />
          <MetroTag text="sem versão premium" color={theme.primary} />
        </View>
      </MetroSection>

      <MetroSection title="Valores sugeridos">
        <View style={{ flexDirection: 'row', gap: spacing.s }}>
          {AMOUNTS.map((v) => (
            <View key={v} style={{ flex: 1 }}>
              <MetroButton
                title={`R$ ${v}`}
                small
                variant={amount === v && !custom ? 'primary' : 'secondary'}
                accessibilityLabel={`R$ ${v}${amount === v && !custom ? ', selecionado' : ''}`}
                onPress={() => { setAmount(v); setCustom(''); }}
              />
            </View>
          ))}
        </View>
        <MetroTextField
          placeholder="Outro valor (R$)"
          keyboardType="decimal-pad"
          value={custom}
          onChangeText={setCustom}
        />
      </MetroSection>

      <MetroSection title="PIX (Copia e Cola)">
        {pixKey ? (
          <>
            {payload && (
              <View style={{ alignItems: 'center', paddingVertical: spacing.m, backgroundColor: theme.fieldFocusBg }}>
                <QRCode value={payload} size={220} color={theme.fieldFocusText} backgroundColor={theme.fieldFocusBg} />
              </View>
            )}
            <MetroButton title="Copiar código PIX" icon="copy" variant="primary" onPress={copy} />
            {payload && (
              <MetroText variant="caption" numberOfLines={2}>
                {payload}
              </MetroText>
            )}
          </>
        ) : (
          <MetroText variant="body" tone="muted">
            Doação PIX ainda não configurada neste build.{'\n'}
            Defina a variável <MetroText variant="body" tone="info">EXPO_PUBLIC_DONATION_PIX_KEY</MetroText> na sua
            sessão de build (nunca no código-fonte) e esta tela gerará o BR Code automaticamente.
          </MetroText>
        )}
      </MetroSection>

      <MetroText variant="caption">
        Obrigado por usar um app livre.
      </MetroText>
    </MetroPage>
  );
}
