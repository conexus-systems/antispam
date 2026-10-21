import { Tabs, useRouter } from 'expo-router';
import { MetroAppBar } from '@conexus/metro-ui';
import { FluentIcon } from '../../src/ui/metro/icons';
import type { FluentName } from '../../src/ui/metro/fluentPaths';
import { useTheme } from '../../src/ui/theme';

/** Ícones de traço fino da app bar W10M. */
const ICONS: Record<string, FluentName> = {
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
      tabBar={({ state, descriptors, emitter, navigateToTab }) => (
        <MetroAppBar
          asTabs
          labels="expanded"
          buttons={state.routes.map((route, index) => {
            const focused = index === state.index;
            const { title } = descriptors[route.key].options;
            const label = typeof title === 'string' ? title : route.name;
            return {
              key: route.key,
              label,
              icon: <FluentIcon name={ICONS[route.name] ?? 'search'} size={24} color={focused ? theme.primary : theme.text} />,
              active: focused,
              accessibilityLabel: `${label}, aba ${index + 1} de ${state.routes.length}`,
              onPress: () => {
                const event = emitter.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
                if (!focused && !event.defaultPrevented) navigateToTab(route.key);
              },
              onLongPress: () => emitter.emit({ type: 'tabLongPress', target: route.key }),
            };
          })}
          menu={[
            { label: 'Conta', onPress: () => router.push('/account') },
            { label: 'Apoiar o projeto', onPress: () => router.push('/donate') },
          ]}
        />
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
