import { useEffect, useState } from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { MetroProvider } from '@conexus/metro-ui';
import { ConexusAccountProvider } from '@conexus/account-expo';
import { hydrate, getState } from '../src/app/store';
import { ensureDb } from '../src/app/screening';
import { ACCENT, useTheme } from '../src/ui/theme';
import { getDefaultExpoNotificationBehavior } from '../src/app/notifications';
// Configura a Conta Conexus ao carregar o módulo (sem client_id: login desligado, sem rede).
import { account } from '../src/account';

export default function RootLayout() {
  const theme = useTheme();
  const [ready, setReady] = useState(false);
  useEffect(() => {
    (async () => {
      await hydrate();
      await ensureDb();
      await getDefaultExpoNotificationBehavior();
      // Só lê tokens do armazenamento seguro (mePath desligado: nenhuma chamada de rede aqui).
      await account.init().catch(() => {});
      setReady(true);
    })();
  }, []);

  if (!ready || !getState().hydrated) {
    return <View style={{ flex: 1, backgroundColor: theme.bg }} />;
  }

  return (
    <MetroProvider accent={ACCENT}>
      <ConexusAccountProvider account={account}>
        <View style={{ flex: 1, backgroundColor: theme.bg }}>
          <StatusBar style={theme.dark ? 'light' : 'dark'} />
          <Stack
            screenOptions={{
              headerShown: false,
              contentStyle: { backgroundColor: theme.bg },
            }}
          >
            <Stack.Screen name="onboarding" />
            <Stack.Screen name="(tabs)" />
            <Stack.Screen name="donate" />
            <Stack.Screen name="account" />
            <Stack.Screen name="login" />
          </Stack>
        </View>
      </ConexusAccountProvider>
    </MetroProvider>
  );
}
