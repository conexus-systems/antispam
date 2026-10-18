import { useMemo, useState } from 'react';
import { Alert, FlatList, View } from 'react-native';
import { actionColor, actionLabel, spacing, useTheme } from '../../src/ui/theme';
import {
  GLYPHS,
  MetroButton,
  MetroDialog,
  MetroEmpty,
  MetroListItem,
  MetroTag,
  MetroText,
  MetroToggle,
} from '@conexus/metro-ui';
import { useAppState, getState, toggleList } from '../../src/app/store';
import { reportHistoryEntry } from '../../src/app/screening';
import { REPORT_CATEGORY_LABELS, type HistoryEntry, type ReportCategory } from '../../src/core/types';
import { useConexusAccount } from '@conexus/account-expo';
import { antispamApi, antispamErrorMessage, isE164 } from '../../src/account';
import { AppScreen } from '../../src/ui/app';

const CATEGORIES = Object.keys(REPORT_CATEGORY_LABELS) as ReportCategory[];

export default function History() {
  const theme = useTheme();
  const history = useAppState((s) => s.history);
  const [openId, setOpenId] = useState<string | null>(null);
  const [reporting, setReporting] = useState<HistoryEntry | null>(null);
  /** Denúncia identificada: opcional por denúncia, sempre começa desligada. */
  const [sendWithAccount, setSendWithAccount] = useState(false);
  const account = useConexusAccount();

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
    const identified = sendWithAccount && account.isSignedIn;
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
      Alert.alert('Denúncia registrada só no aparelho', antispamErrorMessage(e));
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
    <AppScreen
      scroll={false}
      title="Histórico"
      subtitle={`${history.length} chamadas · ${stats.blocked} bloqueadas · ~${stats.savedMin} min poupados (estimativa)`}
    >
      <FlatList
        data={history}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{ paddingBottom: spacing.xl * 2 }}
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
                accessibilityHint={open ? 'Recolher detalhes' : 'Mostrar motivos e ações'}
                trailing={<MetroTag text={actionLabel(item.action)} color={actionColor(item.action, theme)} filled />}
              />

              {open && (
                <View style={{ paddingHorizontal: spacing.m, paddingBottom: spacing.m, gap: spacing.s }}>
                  <MetroText variant="caption">Motivos (decisão explicável):</MetroText>
                  {item.reasons.length === 0 && <MetroText variant="body" color={theme.mute}>• Nenhum sinal relevante.</MetroText>}
                  {item.reasons.map((r) => (
                    <MetroText key={r} variant="body">• {r}</MetroText>
                  ))}
                  {item.simulated && <MetroTag text="simulada" color={theme.info} />}
                  <View style={{ flexDirection: 'row', gap: spacing.s, flexWrap: 'wrap', marginTop: spacing.xs }}>
                    <MetroButton label="Denunciar" icon={GLYPHS.flag} variant="danger" size="small" onPress={() => openReport(item)} />
                    <MetroButton label="Sempre permitir" icon={GLYPHS.check} size="small" onPress={() => quickAction(item, 'allow')} />
                    <MetroButton label="Bloquear nº" icon={GLYPHS.block} variant="danger" size="small" onPress={() => quickAction(item, 'block')} />
                  </View>
                </View>
              )}
            </View>
          );
        }}
      />

      <MetroDialog
        visible={!!reporting}
        title={`Denunciar ${reporting?.display ?? ''}`}
        message="Obrigado por ajudar! A denúncia é processada localmente (anti-abuse: denunciantes únicos, recência e falso-positivos)."
        onDismiss={() => setReporting(null)}
        buttons={[{ text: 'Cancelar', style: 'cancel' }]}
      >
        {account.isSignedIn && (
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
    </AppScreen>
  );
}
