import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Clipboard, View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { useRouter } from 'expo-router';
import { spacing, useTheme } from '../src/ui/theme';
import {
  MetroButton,
  MetroEmpty,
  MetroHeader,
  MetroListItem,
  MetroLoading,
  MetroPage,
  MetroSection,
  MetroTag,
  MetroText,
  MetroTextField,
} from '../src/ui/metro';
import { buildPixPayload } from '../src/core/donation/pix';
import {
  accountErrorMessage,
  DONATION_STATUS_LABELS,
  formatReais,
  openCheckout,
  parseReais,
  paymentsApi,
  useAccount,
  type Donation,
} from '../src/account';

const AMOUNTS = [5, 10, 20];

export default function Donate() {
  const theme = useTheme();
  const [amount, setAmount] = useState<number>(10);
  const [custom, setCustom] = useState('');
  const pixKey = process.env.EXPO_PUBLIC_DONATION_PIX_KEY ?? '';
  const router = useRouter();
  const account = useAccount();
  const [note, setNote] = useState('');
  const [donating, setDonating] = useState(false);
  const [donations, setDonations] = useState<{ loading: boolean; items: Donation[]; error: string | null }>({ loading: false, items: [], error: null });

  const loadDonations = useCallback(async () => {
    setDonations((d) => ({ ...d, loading: true, error: null }));
    try {
      setDonations({ loading: false, items: await paymentsApi.listDonations(), error: null });
    } catch (e) {
      setDonations((d) => ({ ...d, loading: false, error: accountErrorMessage(e) }));
    }
  }, []);

  useEffect(() => {
    if (account.status === 'signedIn') void loadDonations();
  }, [account.status, loadDonations]);

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

  const cents = custom ? parseReais(custom) : parseReais(String(amount));

  async function donateWithAccount() {
    if (!cents) {
      Alert.alert('Valor inválido', 'Escolha um valor entre R$ 1,00 e R$ 10.000,00.');
      return;
    }
    setDonating(true);
    try {
      const checkout = await paymentsApi.donate(cents, note);
      await openCheckout(checkout.checkoutUrl);
      setNote('');
      void loadDonations();
    } catch (e) {
      Alert.alert('Doação pela conta', accountErrorMessage(e));
    } finally {
      setDonating(false);
    }
  }

  const accountSection =
    account.status === 'signedIn' ? (
      <>
        <MetroSection title="Doar pela Conta Conexus">
          <MetroText variant="body" tone="muted">
            Abre o pagamento hospedado pelo serviço de pagamentos Conexus no navegador. A doação fica registrada na sua conta.
          </MetroText>
          <MetroTextField
            label="Mensagem (opcional)"
            placeholder="Até 500 caracteres"
            value={note}
            onChangeText={(v) => setNote(v.slice(0, 500))}
            maxLength={500}
          />
          <MetroButton
            title={cents ? `Doar ${formatReais(cents)} pela conta` : 'Doar pela conta'}
            icon="heart"
            variant="primary"
            loading={donating}
            disabled={!cents}
            onPress={donateWithAccount}
          />
        </MetroSection>
        <MetroSection title="Minhas doações">
          {donations.error && <MetroText variant="body" tone="danger">{donations.error}</MetroText>}
          {donations.loading && <MetroLoading label="Carregando doações" />}
          {!donations.loading && !donations.error && donations.items.length === 0 && <MetroEmpty message="Nenhuma doação pela conta ainda." />}
          <View>
            {donations.items.map((d) => (
              <MetroListItem
                key={d.id}
                title={d.currency === 'BRL' ? formatReais(d.amountCents) : `${d.currency} ${(d.amountCents / 100).toFixed(2)}`}
                subtitle={`${new Date(d.createdAt).toLocaleDateString('pt-BR')}${d.productSlug && d.productSlug !== 'antispam-br' ? ` · ${d.productSlug}` : ''}`}
                value={DONATION_STATUS_LABELS[d.status]}
                valueColor={d.status === 'paid' ? theme.primary : d.status === 'pending' ? theme.warn : theme.mute}
                divider
              />
            ))}
          </View>
          <MetroButton title="Atualizar" small style={{ alignSelf: 'flex-start' }} onPress={() => void loadDonations()} />
        </MetroSection>
      </>
    ) : account.status === 'signedOut' ? (
      <MetroSection title="Doar pela Conta Conexus">
        <MetroText variant="body" tone="muted">
          Opcional: entre com a Conta Conexus para doar pelo serviço de pagamentos e acompanhar suas doações.
        </MetroText>
        <MetroButton title="Entrar na conta" icon="lock" small style={{ alignSelf: 'flex-start' }} onPress={() => router.push('/account')} />
      </MetroSection>
    ) : null;

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

      {accountSection}

      <MetroText variant="caption">
        Obrigado por usar um app livre.
      </MetroText>
    </MetroPage>
  );
}
