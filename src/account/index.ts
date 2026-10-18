/**
 * Instância real da Conta Conexus: tokens no expo-secure-store, login no navegador do sistema
 * (expo-web-browser `openAuthSessionAsync`) e `fetch` do React Native.
 * Sem `EXPO_PUBLIC_CONEXUS_CLIENT_ID` nada aqui chama a rede.
 */
import { useSyncExternalStore } from 'react';
import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import * as WebBrowser from 'expo-web-browser';
import { getState, setState } from '../app/store';
import { createAntispamApi, createPaymentsApi } from './api';
import { CLOUD_BACKUP_MAX_BYTES, parseCloudBackupData, payloadBytes, serializeBackup, summarize, type BackupSummary, type RestorePatch } from './cloudBackup';
import { accountConfig } from './config';
import { AccountError } from './errors';
import type { FetchLike } from './http';
import { createSession, type AccountSnapshot } from './session';

export const account = createSession({
  config: accountConfig,
  fetch: ((url: string, init: Parameters<FetchLike>[1]) => fetch(url, init)) as FetchLike,
  now: () => Date.now(),
  storage: {
    get: (key) => SecureStore.getItemAsync(key),
    set: (key, value) => SecureStore.setItemAsync(key, value, { keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY }),
    remove: (key) => SecureStore.deleteItemAsync(key),
  },
  openAuthSession: async (url, redirectUri) => {
    const result = await WebBrowser.openAuthSessionAsync(url, redirectUri);
    return result.type === 'success' ? { type: 'success', url: result.url } : { type: result.type };
  },
});

export const antispamApi = createAntispamApi(account);
export const paymentsApi = createPaymentsApi(account);

export function useAccount(): AccountSnapshot {
  return useSyncExternalStore(account.subscribe, account.getSnapshot);
}

/** Abre o checkout (somente HTTPS) no navegador do sistema. */
export async function openCheckout(url: string): Promise<void> {
  if (!url.startsWith('https://')) throw new AccountError('checkout_insecure');
  await WebBrowser.openBrowserAsync(url);
}

export async function openExternal(url: string): Promise<void> {
  if (!url.startsWith('https://')) return;
  await WebBrowser.openBrowserAsync(url);
}

function deviceName(): string {
  return Platform.OS === 'android' ? 'Android' : Platform.OS === 'ios' ? 'iPhone' : 'Web';
}

export async function backupNow(): Promise<{ updatedAt: string; summary: BackupSummary }> {
  const s = getState();
  const payload = serializeBackup(s, deviceName());
  if (payloadBytes(payload) > CLOUD_BACKUP_MAX_BYTES) throw new AccountError('payload_too_large');
  const { updatedAt } = await antispamApi.putBackup(payload);
  return {
    updatedAt,
    summary: { blocked: payload.data.blocked.length, allowed: payload.data.allowed.length, rules: payload.data.rules.length, mode: s.mode },
  };
}

/** Busca e valida o backup da nuvem SEM aplicar (para mostrar o resumo na confirmação). */
export async function fetchRestorePreview(): Promise<{ patch: RestorePatch; summary: BackupSummary; updatedAt: string; deviceName: string | null } | null> {
  const backup = await antispamApi.getBackup();
  if (!backup) return null;
  if (backup.version !== 1) throw new AccountError('backup_invalid');
  const patch = parseCloudBackupData(backup.data, getState());
  return { patch, summary: summarize(patch), updatedAt: backup.updatedAt, deviceName: backup.deviceName };
}

export function applyRestore(patch: RestorePatch): void {
  setState(patch);
}

export { accountConfig, isAccountEnabled } from './config';
export { accountErrorMessage, AccountError } from './errors';
export { summaryText } from './cloudBackup';
export {
  CLOUD_REPORT_CATEGORY_LABELS,
  DONATION_STATUS_LABELS,
  formatReais,
  isE164,
  parseReais,
  type CloudReport,
  type Donation,
} from './api';
