/**
 * Liga a sessão às peças nativas do Expo: tokens no expo-secure-store (só neste aparelho, após o 1º desbloqueio),
 * login no navegador do sistema (expo-web-browser `openAuthSessionAsync`: Custom Tabs / ASWebAuthenticationSession,
 * nunca WebView) e o `fetch` do React Native.
 */
import * as SecureStore from "expo-secure-store";
import * as WebBrowser from "expo-web-browser";

import { buildConfig, type ConexusAccountOptions } from "./config";
import type { FetchLike } from "./http";
import { createConexusSession, type ConexusSession, type SecureStorage } from "./session";

export const secureStoreStorage: SecureStorage = {
  get: (key) => SecureStore.getItemAsync(key),
  set: (key, value) => SecureStore.setItemAsync(key, value, { keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY }),
  remove: (key) => SecureStore.deleteItemAsync(key),
};

let current: ConexusSession | null = null;

/**
 * Cria (ou recria) a Conta Conexus do app. Chame uma vez, no topo do layout raiz (fora de componentes):
 *
 * ```ts
 * configureConexusAccount({
 *   clientId: process.env.EXPO_PUBLIC_CONEXUS_CLIENT_ID,
 *   authUrl: process.env.EXPO_PUBLIC_CONEXUS_AUTH_URL,
 *   apiBaseUrl: process.env.EXPO_PUBLIC_CONEXUS_API_URL,
 *   scheme: "finhub",
 * });
 * ```
 */
export function configureConexusAccount(options: ConexusAccountOptions): ConexusSession {
  current = createConexusSession({
    config: buildConfig(options),
    fetch: ((url: string, init: Parameters<FetchLike>[1]) => fetch(url, init)) as FetchLike,
    rawFetch: (url, init) => fetch(url, init),
    now: () => Date.now(),
    storage: secureStoreStorage,
    openAuthSession: async (url, redirectUri) => {
      const result = await WebBrowser.openAuthSessionAsync(url, redirectUri);
      return result.type === "success" ? { type: "success", url: result.url } : { type: result.type };
    },
  });
  return current;
}

/** A sessão configurada. Lança se `configureConexusAccount` ainda não foi chamado. */
export function getConexusAccount(): ConexusSession {
  if (!current) throw new Error("[ConexusAccount] chame configureConexusAccount({...}) antes de usar a conta");
  return current;
}

/** Abre uma página HTTPS (exclusão/gestão da conta, checkout) no navegador do sistema. */
export async function openConexusPage(url: string): Promise<void> {
  if (!url.startsWith("https://")) return;
  await WebBrowser.openBrowserAsync(url);
}
