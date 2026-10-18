import { useMemo, useState } from 'react';
import { Alert, FlatList, View } from 'react-native';
import { actionColor, actionLabel, spacing, useTheme } from '../../src/ui/theme';
import {
  MetroButton,
  MetroDialog,
  MetroEmpty,
  MetroHeader,
  MetroListItem,
  MetroMenu,
  MetroPage,
  MetroTag,
  MetroText,
  MetroToggle,
} from '../../src/ui/metro';
import { useAppState, getState, toggleList } from '../../src/app/store';
import { reportHistoryEntry } from '../../src/app/screening';
import { REPORT_CATEGORY_LABELS, type HistoryEntry, type ReportCategory } from '../../src/core/types';
import { accountErrorMessage, antispamApi, isE164, useAccount } from '../../src/account';

const CATEGORIES = Object.keys(REPORT_CATEGORY_LABELS) as ReportCategory[];

export default function History() {
  const theme = useTheme();
  const history = useAppState((s) => s.history);
  const [openId, setOpenId] = useState<string | null>(null);
  const [reporting, setReporting] = useState<HistoryEntry | null>(null);
  const [menuFor, setMenuFor] = useState<HistoryEntry | null>(null);
  /** Denúncia identificada: opcional por denúncia, sempre começa desligada. */
  const [sendWithAccount, setSendWithAccount] = useState(false);
  const account = useAccount();

  function openReport(entry: HistoryEntry) {
    setSendWithAccount(false);
    setReporting(entry);
  }

  const stats = useMemo(() => {
    const blocked = history.filter((h) => h.action === 'BLOCK').length;
    const savedMin = Math.round(blocked * 0.75);
    return { blocked, savedMin };
  }, [history]);

  async function chooseCategory(cat: ReportCategory) {
    if (!reporting) return;
    const entry = reporting;
    const identified = sendWithAccount && account.status === 'signedIn';
    await reportHistoryEntry(entry.id, cat);
    setReporting(null);
    setSendWithAccount(false);
    if (!identified) {
      Alert.alert('Obrigado!', 'Denúncia registrada localmente. Ela fortalece sua base e, quando a base comunitária estiver ativa, ajudará todos.');
      return;
    }
    try {
      await antispamApi.createReport({ number: entry.number, category: cat, occurredAt: entry.at });
      Alert.alert('Obrigado!', 'Denúncia registrada no aparelho e enviada com a sua Conta Conexus. Veja ou exclua em Conta › Minhas denúncias.');
    } catch (e) {
      Alert.alert('Denúncia registrada só no aparelho', accountErrorMessage(e));
    }
  }

  function quickAction(entry: HistoryEntry, kind: 'allow' | 'block') {
    if (!entry.number) return;
    toggleList(kind === 'allow' ? 'allowlist' : 'blacklist', entry.number);
    Alert.alert(
      kind === 'allow' ? 'Sempre permitir' : 'Bloqueado',
      `${entry.display} ${kind === 'allow' ? 'adicionado à allowlist' : 'adicionado à blacklist'}.`,
    );
  }

  return (
    <MetroPage
      scroll={false}
      header={
        <MetroHeader
          title="Histórico"
          subtitle={`${history.length} chamadas · ${stats.blocked} bloqueadas · ~${stats.savedMin} min poupados (estimativa)`}
        />
      }
    >
      <FlatList
        data={history}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{ paddingHorizontal: theme.gutter, paddingBottom: spacing.xl * 2 }}
        ListEmptyComponent={
          <MetroEmpty message={'Nenhuma chamada ainda.\nUse o simulador na aba Início para ver o app funcionando.'} />
        }
        renderItem={({ item }) => {
          const open = openId === item.id;
          return (
            <View style={{ borderBottomWidth: 1, borderBottomColor: theme.border }}>
              <MetroListItem
                title={item.name || item.display || 'Número oculto'}
                subtitle={`${new Date(item.at).toLocaleString('pt-BR')} · score ${item.score}`}
                selected={open}
                onPress={() => setOpenId(open ? null : item.id)}
                onLongPress={() => setMenuFor(item)}
                accessibilityHint={open ? 'Recolher detalhes' : 'Mostrar motivos e ações'}
                right={<MetroTag text={actionLabel(item.action)} color={actionColor(item.action, theme)} filled />}
              />

              {open && (
                <View style={{ paddingBottom: spacing.m, gap: spacing.s }}>
                  <MetroText variant="caption">Motivos (decisão explicável):</MetroText>
                  {item.reasons.length === 0 && <MetroText variant="body" tone="mute">• Nenhum sinal relevante.</MetroText>}
                  {item.reasons.map((r) => (
                    <MetroText key={r} variant="body">• {r}</MetroText>
                  ))}
                  {item.simulated && <MetroTag text="simulada" color={theme.info} />}
                  <View style={{ flexDirection: 'row', gap: spacing.s, flexWrap: 'wrap', marginTop: spacing.xs }}>
                    <MetroButton title="Denunciar" icon="flag" variant="danger" small onPress={() => openReport(item)} />
                    <MetroButton title="Sempre permitir" icon="check" small onPress={() => quickAction(item, 'allow')} />
                    <MetroButton title="Bloquear nº" icon="block" variant="danger" small onPress={() => quickAction(item, 'block')} />
                  </View>
                </View>
              )}
            </View>
          );
        }}
      />

      <MetroMenu
        visible={!!menuFor}
        onClose={() => setMenuFor(null)}
        items={
          menuFor
            ? [
                { label: 'Denunciar', icon: 'flag', destructive: true, onPress: () => openReport(menuFor) },
                { label: 'Sempre permitir', icon: 'check', onPress: () => quickAction(menuFor, 'allow') },
                { label: 'Bloquear nº', icon: 'block', destructive: true, onPress: () => quickAction(menuFor, 'block') },
              ]
            : []
        }
      />

      <MetroDialog
        visible={!!reporting}
        title={`Denunciar ${reporting?.display ?? ''}`}
        message="Obrigado por ajudar! A denúncia é processada localmente (anti-abuse: denunciantes únicos, recência e falso-positivos)."
        onRequestClose={() => setReporting(null)}
        buttons={[{ title: 'Cancelar', onPress: () => setReporting(null) }]}
      >
        {account.status === 'signedIn' && (
          <MetroToggle
            label="Enviar com minha conta"
            description={
              reporting && isE164(reporting.number)
                ? 'Envia número, categoria e data para a sua Conta Conexus. A denúncia local acontece de qualquer jeito.'
                : 'Só números no formato internacional (+55…) podem ser enviados.'
            }
            value={sendWithAccount}
            onValueChange={setSendWithAccount}
            disabled={!reporting || !isE164(reporting.number)}
          />
        )}
        <View>
          {CATEGORIES.map((cat) => (
            <MetroListItem key={cat} title={REPORT_CATEGORY_LABELS[cat]} onPress={() => chooseCategory(cat)} />
          ))}
        </View>
      </MetroDialog>
    </MetroPage>
  );
}
