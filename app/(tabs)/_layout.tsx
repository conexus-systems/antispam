import { Tabs, useRouter } from 'expo-router';
import { useTheme } from '../../src/ui/theme';
import { MetroAppBar, type GlyphName } from '../../src/ui/metro';

/** Ícones de traço fino da app bar W10M (substituem os emojis da tab bar antiga). */
const ICONS: Record<string, GlyphName> = {
  index: 'shield',
  history: 'history',
  verify: 'search',
  settings: 'settings',
};

export default function TabsLayout() {
  const router = useRouter();
  const theme = useTheme();
  return (
    <Tabs
      tabBar={(props) => (
        <MetroAppBar {...props} icons={ICONS} menu={[
            { label: 'Conta', onPress: () => router.push('/account') },
            { label: 'Apoiar o projeto', onPress: () => router.push('/donate') },
          ]} />
      )}
      screenOptions={{
        headerShown: false,
        sceneStyle: { backgroundColor: theme.bg },
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Início' }} />
      <Tabs.Screen name="history" options={{ title: 'Histórico' }} />
      <Tabs.Screen name="verify" options={{ title: 'Verificar' }} />
      <Tabs.Screen name="settings" options={{ title: 'Ajustes' }} />
    </Tabs>
  );
}
