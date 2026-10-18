/**
 * Contrato do Auth BFF para cliente público: `GET /oauth/authorize`, `POST /oauth/token` e
 * `POST /oauth/revoke` — Authorization Code + PKCE S256, sem segredo. Funções puras (testáveis).
 * Porta de `AuthContract.kt` do Conexus Launcher.
 */
import { base64UrlDecode, utf8Decode } from './base64';
import { REDIRECT_URI } from './config';
import { AccountError } from './errors';

export const PENDING_TTL_MS = 10 * 60_000;
export const REFRESH_MARGIN_MS = 60_000;

export interface Tokens {
  accessToken: string;
  refreshToken: string;
  /** Epoch em ms. */
  expiresAt: number;
}

export interface PendingLogin {
  state: string;
  verifier: string;
  /** Epoch em ms de quando o login começou. */
  at: number;
}

export interface Profile {
  subject: string;
  email: string | null;
  name: string | null;
}

export function form(pairs: Record<string, string>): string {
  return Object.entries(pairs)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
}

export function authorizeUrl(authBaseUrl: string, clientId: string, state: string, challenge: string): string {
  return `${authBaseUrl}/oauth/authorize?${form({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: REDIRECT_URI,
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  })}`;
}

export function codeExchangeForm(clientId: string, code: string, verifier: string): string {
  return form({ grant_type: 'authorization_code', code, client_id: clientId, redirect_uri: REDIRECT_URI, code_verifier: verifier });
}

export function refreshForm(clientId: string, refreshToken: string): string {
  return form({ grant_type: 'refresh_token', refresh_token: refreshToken, client_id: clientId });
}

export function revokeForm(clientId: string, token: string): string {
  return form({ token, client_id: clientId });
}

function parseQuery(raw: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const pair of raw.split('&')) {
    if (!pair) continue;
    const idx = pair.indexOf('=');
    const key = idx < 0 ? pair : pair.slice(0, idx);
    const value = idx < 0 ? '' : pair.slice(idx + 1);
    try {
      out[decodeURIComponent(key.replace(/\+/g, ' '))] = decodeURIComponent(value.replace(/\+/g, ' '));
    } catch {
      // par malformado: ignora
    }
  }
  return out;
}

/**
 * Aceita só a nossa redirect URI, o mesmo `state` enviado e um login iniciado há menos de 10 minutos.
 * Retorna o `code` ou lança AccountError com código fixo.
 */
export function parseRedirect(redirect: string, pending: PendingLogin | null, now: number): string {
  const [base, ...rest] = redirect.split('?');
  const query = rest.join('?').split('#')[0];
  if (base !== REDIRECT_URI) throw new AccountError('invalid_redirect');
  const params = parseQuery(query);
  if (!pending || now - pending.at > PENDING_TTL_MS || now < pending.at || !params.state || params.state !== pending.state) {
    throw new AccountError('state_mismatch');
  }
  if (params.error) throw new AccountError('login_denied');
  const code = params.code;
  if (!code || code.length > 512) throw new AccountError('invalid_redirect');
  return code;
}

export function parseTokens(body: unknown, now: number): Tokens {
  if (!body || typeof body !== 'object') throw new AccountError('invalid_response');
  const b = body as Record<string, unknown>;
  if (typeof b.token_type !== 'string' || b.token_type.toLowerCase() !== 'bearer') throw new AccountError('invalid_response');
  const access = b.access_token;
  const refresh = b.refresh_token;
  const expiresIn = b.expires_in;
  if (typeof access !== 'string' || access.split('.').length !== 3) throw new AccountError('invalid_response');
  if (typeof refresh !== 'string' || !refresh.trim()) throw new AccountError('invalid_response');
  if (typeof expiresIn !== 'number' || !Number.isFinite(expiresIn) || expiresIn < 1 || expiresIn > 86_400) {
    throw new AccountError('invalid_response');
  }
  return { accessToken: access, refreshToken: refresh, expiresAt: now + expiresIn * 1000 };
}

export function needsRefresh(tokens: Tokens, now: number): boolean {
  return tokens.expiresAt - now <= REFRESH_MARGIN_MS;
}

/** Só para exibir nome/e-mail. Quem valida a assinatura é cada BFF (JWKS do Auth BFF). */
export function profileFromJwt(accessToken: string): Profile | null {
  try {
    const parts = accessToken.split('.');
    if (parts.length !== 3) return null;
    const claims = JSON.parse(utf8Decode(base64UrlDecode(parts[1]))) as Record<string, unknown>;
    if (typeof claims.sub !== 'string' || !claims.sub) return null;
    const opt = (k: string) => (typeof claims[k] === 'string' && (claims[k] as string).trim() ? (claims[k] as string).trim() : null);
    return { subject: claims.sub, email: opt('email'), name: opt('name') };
  } catch {
    return null;
  }
}
