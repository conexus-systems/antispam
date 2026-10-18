/**
 * Conta Conexus (opcional) do AntiSpam BR, sobre `@conexus/account-expo`: login PKCE no navegador do sistema,
 * tokens no expo-secure-store (chaves `conexus.tokens`/`conexus.pending_login`, as mesmas da 0.3.0: quem já
 * estava logado continua logado), refresh 60 s antes de expirar e revoke ao sair.
 * Sem `EXPO_PUBLIC_CONEXUS_CLIENT_ID` o login fica desligado e nada aqui chama a rede.
 *
 * As variáveis são lidas como `process.env.EXPO_PUBLIC_*` literais para o Expo embuti-las no build; vazias usam
 * os padrões de produção abaixo.
 */
import { Platform } from 'react-native';
import { configureConexusAccount, ConexusAccountError, openConexusPage, sanitizeBaseUrl } from '@conexus/account-expo';
import { getState, setState } from '../app/store';
import { AntispamError, createAntispamApi, createPaymentsApi, SCHEME } from './api';
import { CLOUD_BACKUP_MAX_BYTES, parseCloudBackupData, payloadBytes, serializeBackup, summarize, type BackupSummary, type RestorePatch } from './cloudBackup';

export const DEFAULT_AUTH_URL = 'https://auth.conexus-systems.com.br';
export const DEFAULT_API_URL = 'https://api.conexus-systems.com.br';
export const DEFAULT_PAYMENTS_URL = 'https://payments.conexus-systems.com.br';

export const account = configureConexusAccount({
  clientId: process.env.EXPO_PUBLIC_CONEXUS_CLIENT_ID,
  authUrl: process.env.EXPO_PUBLIC_CONEXUS_AUTH_URL || DEFAULT_AUTH_URL,
  apiBaseUrl: process.env.EXPO_PUBLIC_CONEXUS_API_URL || DEFAULT_API_URL,
  scheme: SCHEME,
  // A API do AntiSpam (bff-api) é servidor de recurso sem espelho de usuário: não há GET /conexus/me.
  // Nome e e-mail vêm do access token.
  mePath: null,
});

export const paymentsUrl = sanitizeBaseUrl(process.env.EXPO_PUBLIC_CONEXUS_PAYMENTS_URL, DEFAULT_PAYMENTS_URL);

export const antispamApi = createAntispamApi(account);
export const paymentsApi = createPaymentsApi(account, paymentsUrl);

/** Abre o checkout (somente HTTPS) no navegador do sistema. */
export async function openCheckout(url: string): Promise<void> {
  if (!url.startsWith('https://')) throw new AntispamError('checkout_insecure');
  await openConexusPage(url);
}

function deviceName(): string {
  return Platform.OS === 'android' ? 'Android' : Platform.OS === 'ios' ? 'iPhone' : 'Web';
}

export async function backupNow(): Promise<{ updatedAt: string; summary: BackupSummary }> {
  const s = getState();
  const payload = serializeBackup(s, deviceName());
  if (payloadBytes(payload) > CLOUD_BACKUP_MAX_BYTES) throw new ConexusAccountError('payload_too_large');
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
  if (backup.version !== 1) throw new AntispamError('backup_invalid');
  const patch = parseCloudBackupData(backup.data, getState());
  return { patch, summary: summarize(patch), updatedAt: backup.updatedAt, deviceName: backup.deviceName };
}

export function applyRestore(patch: RestorePatch): void {
  setState(patch);
}

export { summaryText } from './cloudBackup';
export {
  antispamErrorMessage,
  CLOUD_REPORT_CATEGORY_LABELS,
  DONATION_STATUS_LABELS,
  formatReais,
  isE164,
  parseReais,
  SCHEME,
  type CloudReport,
  type Donation,
} from './api';
