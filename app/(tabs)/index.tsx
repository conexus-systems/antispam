import { useMemo, useState } from 'react';
import { View } from 'react-native';
import { Link, useRouter } from 'expo-router';
import * as Notifications from 'expo-notifications';
import { spacing, useTheme } from '../../src/ui/theme';
import { MetroButton, MetroListItem, MetroNotice, MetroSection, MetroTag, MetroText } from '@conexus/metro-ui';
import { AppScreen, Flush, MetroRow } from '../../src/ui/app';
import { getState, setState, useAppState } from '../../src/app/store';
import { simulateCall } from '../../src/app/screening';
import { isCallScreeningRoleHeld, isNativeScreeningAvailable } from '../../src/app/nativeBridge';
import { requestNotificationPermission } from '../../src/app/notifications';
import { FluentIcon } from '../../src/ui/metro/icons';

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
    <AppScreen title="Início">
      <MetroSection>
        <MetroTag text={active ? 'PROTEÇÃO ATIVA' : 'DESATIVADA'} color={active ? theme.primary : theme.mute} filled={active} />
        <MetroRow label="Modo" value={mode} color={theme.info} />
        <MetroRow label="Hoje" value={`${today.analyzed} analisadas`} />
        <MetroRow label="Permitidas" value={String(today.allowed)} color={theme.primary} />
        <MetroRow label="Silenciadas" value={String(today.silenced)} color={theme.mute} />
        <MetroRow label="Bloqueadas" value={String(today.blocked)} color={theme.danger} />
        <View style={{ marginTop: spacing.s, flexDirection: 'row', gap: spacing.s }}>
          <View style={{ flex: 1 }}>
            <MetroButton block label="Ver histórico" size="small" onPress={() => router.push('/(tabs)/history')} />
          </View>
          <View style={{ flex: 1 }}>
            <MetroButton block label="Apoiar projeto" size="small" onPress={() => router.push('/donate')} />
          </View>
        </View>
      </MetroSection>

      {!isNativeScreeningAvailable() && (
        <MetroSection>
          <MetroNotice title="Modo demonstração" tone="warning">
            Você está no Expo Go: todas as telas e o motor de decisão funcionam, mas o Android só entrega chamadas
            reais a um development build (APK/AAB com o módulo nativo). Use o simulador abaixo para ver o app em ação.
          </MetroNotice>
        </MetroSection>
      )}
      {isNativeScreeningAvailable() && roleHeld === false && (
        <MetroSection>
          <MetroNotice title="Proteção nativa não ativada" tone="danger">
            Conceda o papel de filtragem de chamadas nas Configurações do app.
          </MetroNotice>
        </MetroSection>
      )}

      <MetroSection title="Simular chamada recebida">
        <MetroText variant="caption">
          Teste o pipeline completo (normalização → regras → base → score → decisão).
        </MetroText>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.s }}>
          <View style={{ width: '48%' }}>
            <MetroButton block label="0303 313 0303" size="small" loading={simulating} onPress={() => simulate('03033130303')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton block label="0800 empresa" size="small" onPress={() => simulate('08001234567')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton block label="Telemarketing +55 11 4002-8922" size="small" onPress={() => simulate('+551140028922')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton block label="Golpe +55 11 98765-4321" size="small" onPress={() => simulate('+5511987654321')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton block label="Fixo SP (11) 3061-4000" size="small" variant="primary" onPress={() => simulate('1130614000')} />
          </View>
          <View style={{ width: '48%' }}>
            <MetroButton block label="190 (emergência)" size="small" variant="primary" onPress={() => simulate('190')} />
          </View>
        </View>
        {lastScore != null && (
          <MetroText variant="body" tone="accent" accessibilityLiveRegion="polite">
            Última decisão registrada no histórico (score {lastScore}).
          </MetroText>
        )}
      </MetroSection>

      <MetroListItem
        icon={<FluentIcon name="search" />}
        large
        title="Verificar número"
        subtitle="Consulte score local, explicabilidade e (opcional) análise de IA antes de retornar uma ligação."
        onPress={() => router.push('/verify')}
      />

      <MetroSection title="Atalhos">
        <Flush>
          <MetroListItem icon={<FluentIcon name="settings" />} title="Configurações e regras" onPress={() => router.push('/(tabs)/settings')} />
          <MetroListItem
            icon={<FluentIcon name="alert" />}
            title="Permissão de notificações"
            onPress={async () => {
              await requestNotificationPermission();
              void Notifications.getPermissionsAsync();
            }}
          />
          <MetroListItem icon={<FluentIcon name="delete" />} title="Limpar histórico" onPress={() => setState({ history: [] })} />
        </Flush>
      </MetroSection>

      <MetroSection>
        <MetroText variant="caption">
          análise 100% local · open source{'\n'}Decisões explicáveis e fail-safe: em dúvida, permite.
        </MetroText>
      </MetroSection>
    </AppScreen>
  );
}
