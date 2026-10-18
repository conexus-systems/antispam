import { useEffect, useState } from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { hydrate, getState } from '../src/app/store';
import { ensureDb } from '../src/app/screening';
import { useTheme } from '../src/ui/theme';
import { getDefaultExpoNotificationBehavior } from '../src/app/notifications';
import { account } from '../src/account';

export default function RootLayout() {
  const theme = useTheme();
  const [ready, setReady] = useState(false);
  useEffect(() => {
    (async () => {
      await hydrate();
      await ensureDb();
      await getDefaultExpoNotificationBehavior();
      // Só lê tokens do armazenamento seguro; sem client_id não faz nada (e nunca chama a rede aqui).
      await account.init().catch(() => {});
      setReady(true);
    })();
  }, []);

  if (!ready || !getState().hydrated) {
    return <View style={{ flex: 1, backgroundColor: theme.bg }} />;
  }

  return (
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
        <Stack.Screen name="verify" />
        <Stack.Screen name="donate" />
        <Stack.Screen name="account" />
      </Stack>
    </View>
  );
}
