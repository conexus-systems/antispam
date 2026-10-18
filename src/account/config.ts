/**
 * Configuração pública da Conta Conexus (opcional). Nenhum segredo: só o client_id público e as URLs base.
 * `EXPO_PUBLIC_CONEXUS_CLIENT_ID` vazio = login desligado e nenhuma chamada de rede.
 * As variáveis são lidas como `process.env.EXPO_PUBLIC_*` literais para o Expo embuti-las no build.
 */

export const REDIRECT_URI = 'antispambr://oauth/callback';
export const BILLING_RETURN_URI = 'antispambr://billing/return';
export const PRODUCT_SLUG = 'antispam-br';

export const DEFAULT_AUTH_URL = 'https://auth.conexus-systems.com.br';
export const DEFAULT_API_URL = 'https://api.conexus-systems.com.br';
export const DEFAULT_PAYMENTS_URL = 'https://payments.conexus-systems.com.br';
/** Página que explica como excluir a conta Conexus (o Auth BFF ainda não tem autoexclusão pelo app). */
export const DEFAULT_ACCOUNT_DELETION_URL = 'https://github.com/conexus-systems/antispam-br/blob/main/docs/ACCOUNT.md#excluir-a-conta-conexus';

export interface AccountConfig {
  clientId: string | null;
  authUrl: string;
  apiUrl: string;
  paymentsUrl: string;
  accountDeletionUrl: string;
}

/** Aceita só HTTPS sem credenciais, query ou fragmento; remove a barra final. Inválida = padrão. */
export function sanitizeBaseUrl(value: string | undefined, fallback: string): string {
  const raw = (value ?? '').trim();
  if (!raw) return fallback;
  const m = /^https:\/\/([A-Za-z0-9.-]+)(:\d+)?(\/[A-Za-z0-9._~\/-]*)?$/.exec(raw);
  if (!m || raw.includes('@')) return fallback;
  return raw.replace(/\/+$/, '');
}

export function sanitizeClientId(value: string | undefined): string | null {
  const v = (value ?? '').trim();
  return /^[A-Za-z0-9._~-]{1,200}$/.test(v) ? v : null;
}

export function buildConfig(env: {
  clientId?: string;
  authUrl?: string;
  apiUrl?: string;
  paymentsUrl?: string;
  accountDeletionUrl?: string;
}): AccountConfig {
  return {
    clientId: sanitizeClientId(env.clientId),
    authUrl: sanitizeBaseUrl(env.authUrl, DEFAULT_AUTH_URL),
    apiUrl: sanitizeBaseUrl(env.apiUrl, DEFAULT_API_URL),
    paymentsUrl: sanitizeBaseUrl(env.paymentsUrl, DEFAULT_PAYMENTS_URL),
    accountDeletionUrl: sanitizeBaseUrl(env.accountDeletionUrl, DEFAULT_ACCOUNT_DELETION_URL),
  };
}

export const accountConfig: AccountConfig = buildConfig({
  clientId: process.env.EXPO_PUBLIC_CONEXUS_CLIENT_ID,
  authUrl: process.env.EXPO_PUBLIC_CONEXUS_AUTH_URL,
  apiUrl: process.env.EXPO_PUBLIC_CONEXUS_API_URL,
  paymentsUrl: process.env.EXPO_PUBLIC_CONEXUS_PAYMENTS_URL,
  accountDeletionUrl: process.env.EXPO_PUBLIC_CONEXUS_ACCOUNT_DELETION_URL,
});

export function isAccountEnabled(config: AccountConfig = accountConfig): boolean {
  return config.clientId !== null;
}
