/**
 * Configuração pública da Conta Conexus num app Expo. Nenhum segredo: só o client_id público (cliente OAuth
 * público com PKCE), as URLs base e o scheme do app. `clientId` vazio = login desligado e nenhuma chamada de rede.
 *
 * O app lê as variáveis `EXPO_PUBLIC_CONEXUS_*` com `process.env.EXPO_PUBLIC_…` literal (só assim o Expo as embute
 * no bundle) e passa os valores para `configureConexusAccount`.
 */

export const DEFAULT_AUTH_URL = "https://auth.conexus-systems.com.br";
/** Caminho, na API do produto, que devolve o espelho do usuário (`ConexusBearerAuth`). */
export const DEFAULT_ME_PATH = "/conexus/me";

export interface ConexusAccountOptions {
  /** client_id público registrado no bff-auth para este app (EXPO_PUBLIC_CONEXUS_CLIENT_ID). */
  clientId?: string | null;
  /** Scheme do app (app.json `expo.scheme`); a redirect URI é `<scheme>://oauth/callback`. */
  scheme: string;
  /** URL base do bff-auth (EXPO_PUBLIC_CONEXUS_AUTH_URL). Padrão: produção. */
  authUrl?: string;
  /** URL base da API do produto (EXPO_PUBLIC_CONEXUS_API_URL): `authedFetch("/x")` e `GET /conexus/me`. */
  apiBaseUrl?: string;
  /** Caminho do "me" na API do produto (padrão `/conexus/me`; `null` desliga a busca). */
  mePath?: string | null;
  /** Página de exclusão da conta (padrão `<authUrl>/conta/excluir`). */
  accountDeletionUrl?: string;
  /** `scope` enviado no authorize (opcional; o bff-auth ignora se não usar). */
  scope?: string;
  /** Prefixo das chaves no expo-secure-store (padrão "conexus"). */
  storagePrefix?: string;
}

export interface ConexusAccountConfig {
  clientId: string | null;
  scheme: string;
  redirectUri: string;
  authUrl: string;
  apiBaseUrl: string | null;
  mePath: string | null;
  accountDeletionUrl: string;
  manageAccountUrl: string;
  scope: string | null;
  storagePrefix: string;
}

/**
 * Aceita só HTTPS sem credenciais, query ou fragmento; remove a barra final. Inválida = `fallback`.
 * `http://localhost`/`10.0.2.2`/IP privado é aceito só com `allowInsecureLocal` (desenvolvimento).
 */
export function sanitizeBaseUrl<T extends string | null>(value: string | null | undefined, fallback: T, allowInsecureLocal = false): string | T {
  const raw = (value ?? "").trim();
  if (!raw) return fallback;
  const https = /^https:\/\/([A-Za-z0-9.-]+)(:\d+)?(\/[A-Za-z0-9._~/-]*)?$/.test(raw);
  const local = allowInsecureLocal && /^http:\/\/(localhost|127\.0\.0\.1|10\.0\.2\.2|192\.168\.\d+\.\d+)(:\d+)?(\/[A-Za-z0-9._~/-]*)?$/.test(raw);
  if ((!https && !local) || raw.includes("@")) return fallback;
  return raw.replace(/\/+$/, "");
}

export function sanitizeClientId(value: string | null | undefined): string | null {
  const v = (value ?? "").trim();
  return /^[A-Za-z0-9._~-]{1,200}$/.test(v) ? v : null;
}

/** Scheme de URL válido (RFC 3986), em minúsculas. Lança se inválido: é erro de programação do app. */
export function sanitizeScheme(value: string): string {
  const v = (value ?? "").trim().toLowerCase().replace(/:\/*$/, "");
  if (!/^[a-z][a-z0-9+.-]{1,63}$/.test(v)) throw new Error(`[ConexusAccount] scheme inválido: "${value}"`);
  return v;
}

export function redirectUriFor(scheme: string): string {
  return `${sanitizeScheme(scheme)}://oauth/callback`;
}

export function buildConfig(options: ConexusAccountOptions): ConexusAccountConfig {
  const scheme = sanitizeScheme(options.scheme);
  const authUrl = sanitizeBaseUrl(options.authUrl, DEFAULT_AUTH_URL);
  const dev = typeof __DEV__ !== "undefined" && __DEV__;
  const mePath = options.mePath === null ? null : (options.mePath ?? DEFAULT_ME_PATH);
  return {
    clientId: sanitizeClientId(options.clientId),
    scheme,
    redirectUri: `${scheme}://oauth/callback`,
    authUrl,
    apiBaseUrl: sanitizeBaseUrl(options.apiBaseUrl, null, dev),
    mePath: mePath && /^\/[A-Za-z0-9._~/-]*$/.test(mePath) ? mePath : null,
    accountDeletionUrl: sanitizeBaseUrl(options.accountDeletionUrl, `${authUrl}/conta/excluir`),
    manageAccountUrl: `${authUrl}/conta`,
    scope: options.scope?.trim() || null,
    storagePrefix: /^[A-Za-z0-9._-]{1,40}$/.test(options.storagePrefix ?? "") ? options.storagePrefix! : "conexus",
  };
}
