import { useState } from 'react';
import { Alert, Platform, View } from 'react-native';
import { useRouter } from 'expo-router';
import * as Haptics from 'expo-haptics';
import { spacing, useTheme } from '../src/ui/theme';
import { Glyph, GLYPHS, MetroButton, MetroScreen, MetroText, type GlyphName } from '@conexus/metro-ui';
import { setState } from '../src/app/store';
import { isCallScreeningRoleHeld, isNativeScreeningAvailable, requestCallScreeningRole } from '../src/app/nativeBridge';

const STEPS = [
  {
    icon: 'phoneOff' as GlyphName,
    title: 'Chega de chamadas indesejadas',
    body: 'Bloqueie telemarketing, golpes e robocalls antes do telefone tocar. 100% local, explicável e gratuito.',
  },
  {
    icon: 'lock' as GlyphName,
    title: 'Seus dados ficam no seu celular',
    body: 'Sem envio de contatos ou histórico e sem cadastro obrigatório. A análise de cada chamada acontece no aparelho; a conta é opcional. Há anúncios só dentro do app, nunca durante uma chamada.',
  },
  {
    icon: 'shield' as GlyphName,
    title: 'Ative a proteção',
    body: 'O Android pede que o AntiSpam BR seja definido como app de identificação e filtragem de chamadas. É assim que ele consegue proteger você.',
  },
];

export default function Onboarding() {
  const router = useRouter();
  const theme = useTheme();
  const [step, setStep] = useState(0);
  const [activating, setActivating] = useState(false);
  const s = STEPS[step];

  async function activate() {
    setActivating(true);
    try {
      if (isNativeScreeningAvailable()) {
        const held = await isCallScreeningRoleHeld();
        if (!held) {
          const granted = await requestCallScreeningRole();
          if (!granted) {
            setActivating(false);
            return;
          }
        }
      } else {
        Alert.alert(
          'Modo demonstração',
          Platform.OS === 'ios'
            ? 'No iOS, o bloqueio de chamadas usa Call Directory Extension, configurado na próxima fase nativa. Por enquanto você pode explorar o app com o simulador.'
            : 'Você está no Expo Go (sem módulo nativo). A proteção real será ativada na versão instalada (development build / APK). Explore o simulador!',
        );
      }
      setState({ onboardingDone: true });
      router.replace('/(tabs)');
    } finally {
      setActivating(false);
    }
  }

  return (
    <MetroScreen padded>
      <View style={{ flex: 1, justifyContent: 'center', gap: spacing.xl }}>
        <MetroText variant="kicker">AntiSpam BR</MetroText>
        <Glyph name={s.icon} size={72} color={theme.primary} />
        <MetroText variant="header" accessibilityRole="header">{s.title}</MetroText>
        <MetroText variant="body" tone="muted">{s.body}</MetroText>
        <View
          style={{ flexDirection: 'row', gap: spacing.s }}
          accessible
          accessibilityLabel={`Passo ${step + 1} de ${STEPS.length}`}
        >
          {STEPS.map((_, i) => (
            <View key={i} style={{ width: i === step ? 24 : 8, height: 8, backgroundColor: i === step ? theme.primary : theme.border }} />
          ))}
        </View>
      </View>
      <View style={{ gap: spacing.m, paddingBottom: spacing.xl }}>
        {step < STEPS.length - 1 ? (
          <>
            <MetroButton block label="Continuar" variant="primary" onPress={() => { Haptics.selectionAsync().catch(() => {}); setStep(step + 1); }} />
            <MetroButton block label="Pular" onPress={() => router.replace('/(tabs)')} />
          </>
        ) : (
          <>
            <MetroButton block label={activating ? 'Ativando…' : 'Ativar proteção'} icon={GLYPHS.shield} variant="primary" onPress={activate} loading={activating} />
            <MetroButton
              label="Ativar depois — explorar em modo demonstração"
              variant="text"
              onPress={() => { setState({ onboardingDone: true }); router.replace('/(tabs)'); }}
            />
          </>
        )}
      </View>
    </MetroScreen>
  );
}
