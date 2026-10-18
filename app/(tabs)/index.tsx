import { useMemo, useState } from 'react';
import { View } from 'react-native';
import { Link, useRouter } from 'expo-router';
import * as Notifications from 'expo-notifications';
import { spacing, useTheme } from '../../src/ui/theme';
import {
  MetroButton,
  MetroHeader,
  MetroListItem,
  MetroNotice,
  MetroPage,
  MetroRow,
  MetroSection,
  MetroTag,
  MetroText,
} from '../../src/ui/metro';
import { getState, setState, useAppState } from '../../src/app/store';
import { simulateCall } from '../../src/app/screening';
import { isCallScreeningRoleHeld, isNativeScreeningAvailable } from '../../src/app/nativeBridge';
import { requestNotificationPermission } from '../../src/app/notifications';

function isToday(iso: string): boolean {
  const d = new Date(iso);
  const now = new Date();
  return d.toDateString() === now.toDateString();
}

export default function Home() {
  const router = useRouter();
  const theme = useTheme();
  const history = useAppState((s) => s.history);
  const mode = useAppState((s) => s.mode);
  const [simulating, setSimulating] = useState(false);
  const [lastScore, setLastScore] = useState<number | null>(null);

  const today = useMemo(() => {
    const t = history.filter((h) => isToday(h.at));
    return {
      analyzed: t.length,
      blocked: t.filter((h) => h.action === 'BLOCK').length,
      silenced: t.filter((h) => h.action === 'SILENCE').length,
      allowed: t.filter((h) => h.action === 'ALLOW').length,
    };
  }, [history]);

  const active = mode !== 'OFF';
  const [roleHeld, setRoleHeld] = useState<boolean | null>(null);

  async function simulate(number: string) {
    setSimulating(true);
    try {
      if (getState().settings.notifications) {
        await requestNotificationPermission().catch(() => {});
      }
      const decision = await simulateCall(number);
      setLastScore(decision.score);
    } finally {
      setSimulating(false);
    }
  }

  return (
    <MetroPage header={<MetroHeader title="Início" />}>
      <MetroTag text={active ? 'PROTEÇÃO ATIVA' : 'DESATIVADA'} color={active ? theme.primary : theme.mute} filled={active} />

      <MetroSection>
        <MetroRow label="Modo" value={mode} color={theme.info} />
        <MetroRow label="Hoje" value={`${today.analyzed} analisadas`} />
        <MetroRow label="Permitidas" value={String(today.allowed)} color={theme.primary} />
        <MetroRow label="Silenciadas" value={String(today.silenced)} color={theme.mute} />
        <MetroRow label="Bloqueadas" value={String(today.blocked)} color={theme.danger} />
        <View style={{ marginTop: spacing.s, flexDirection: 'row', gap: spacing.s }}>
          <View style={{ flex: 1 }}>
            <MetroButton title="Ver histórico" small onPress={() => router.push('/(tabs)/history')} />
          </View>
          <View style={{ flex: 1 }}>
            <MetroButton title="Apoiar projeto" small onPress={() => router.push('/donate')} />
          </View>
        </View>
      </MetroSection>

      {!isNativeScreeningAvailable() && (
        <MetroNotice title="Modo demonstração" tone="warn">
          Você está no Expo Go: todas as telas e o motor de decisão funcionam, mas o Android só entrega chamadas
          reais a um development build (APK/AAB com o módulo nativo). Use o simulador abaixo para ver o app em ação.
        </MetroNotice>
      )}
      {isNativeScreeningAvailable() && roleHeld === false && (
        <MetroNotice title="Proteção nativa não ativada" tone="danger">
          Conceda o papel de filtragem de chamadas nas Configurações do app.
        </MetroNotice>
      )}

      <MetroSection title="Simular chamada recebida">
        <MetroText variant="caption">
          Teste o pipeline completo (normalização → regras → base → score → decisão).
        </MetroText>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.s }}>
          <View style={{ width: '48%' }}>
            <MetroButton title="0303 313 0303" small loading={simulating} onPress={() => simulate('03033130303')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton title="0800 empresa" small onPress={() => simulate('08001234567')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton title="Telemarketing +55 11 4002-8922" small onPress={() => simulate('+551140028922')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton title="Golpe +55 11 98765-4321" small onPress={() => simulate('+5511987654321')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton title="Fixo SP (11) 3061-4000" small variant="primary" onPress={() => simulate('1130614000')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton title="190 (emergência)" small variant="primary" onPress={() => simulate('190')} />
          </View>
        </View>
        {lastScore != null && (
          <MetroText variant="body" tone="accent" accessibilityLiveRegion="polite">
            Última decisão registrada no histórico (score {lastScore}).
          </MetroText>
        )}
      </MetroSection>

      <MetroListItem
        icon="search"
        large
        title="Verificar número"
        subtitle="Consulte score local, explicabilidade e (opcional) análise de IA antes de retornar uma ligação."
        onPress={() => router.push('/verify')}
      />

      <MetroSection title="Atalhos">
        <View>
          <MetroListItem icon="settings" title="Configurações e regras" onPress={() => router.push('/(tabs)/settings')} />
          <MetroListItem
            icon="bell"
            title="Permissão de notificações"
            onPress={async () => {
              await requestNotificationPermission();
              void Notifications.getPermissionsAsync();
            }}
          />
          <MetroListItem icon="delete" title="Limpar histórico" onPress={() => setState({ history: [] })} />
        </View>
      </MetroSection>

      <MetroText variant="caption">
        100% local · sem anúncios · open source{'\n'}Decisões explicáveis e fail-safe: em dúvida, permite.
      </MetroText>
    </MetroPage>
  );
}
