/**
 * Sessão da Conta Conexus (porta de `BffSession.kt` do Conexus Launcher):
 * login no navegador com PKCE S256, troca de código, renovação 60 s antes de expirar (refresh recusado
 * desconecta), revogação ao sair e requisições autenticadas com 401 → refresh → nova tentativa.
 * Dependências injetáveis para teste; a instância real fica em `./index`.
 */
import {
  authorizeUrl,
  codeExchangeForm,
  needsRefresh,
  parseRedirect,
  parseTokens,
  profileFromJwt,
  refreshForm,
  revokeForm,
  type PendingLogin,
  type Profile,
  type Tokens,
} from './authContract';
import { REDIRECT_URI, type AccountConfig } from './config';
import { AccountError, errorFromResponse } from './errors';
import { errorCode, httpRequest, isOk, type FetchLike, type HttpResponse, type RequestOptions } from './http';
import { challengeS256, newState, newVerifier } from './pkce';

export interface SecureStorage {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  remove(key: string): Promise<void>;
}

export type AuthSessionResult = { type: 'success'; url: string } | { type: string; url?: undefined };

export interface SessionDeps {
  config: AccountConfig;
  fetch: FetchLike;
  storage: SecureStorage;
  now: () => number;
  openAuthSession: (url: string, redirectUri: string) => Promise<AuthSessionResult>;
}

export type AccountStatus = 'disabled' | 'loading' | 'signedOut' | 'signedIn';

export interface AccountSnapshot {
  status: AccountStatus;
  profile: Profile | null;
}

export const TOKENS_KEY = 'conexus.tokens';
export const PENDING_KEY = 'conexus.pending_login';

export function createSession(deps: SessionDeps) {
  const { config, storage, now } = deps;
  const enabled = config.clientId !== null;
  let snapshot: AccountSnapshot = { status: enabled ? 'loading' : 'disabled', profile: null };
  const listeners = new Set<() => void>();
  let refreshing: Promise<Tokens> | null = null;

  function emit(next: AccountSnapshot) {
    snapshot = next;
    for (const l of listeners) l();
  }

  function requireEnabled(): string {
    if (!config.clientId) throw new AccountError('disabled');
    return config.clientId;
  }

  async function readTokens(): Promise<Tokens | null> {
    try {
      const raw = await storage.get(TOKENS_KEY);
      if (!raw) return null;
      const t = JSON.parse(raw) as Partial<Tokens>;
      if (typeof t.accessToken !== 'string' || typeof t.refreshToken !== 'string' || typeof t.expiresAt !== 'number') return null;
      return t as Tokens;
    } catch {
      return null;
    }
  }

  async function storeTokens(tokens: Tokens): Promise<void> {
    await storage.set(TOKENS_KEY, JSON.stringify(tokens));
    emit({ status: 'signedIn', profile: profileFromJwt(tokens.accessToken) });
  }

  async function clearSession(): Promise<void> {
    try {
      await storage.remove(TOKENS_KEY);
    } catch {
      // nada a fazer
    }
    emit({ status: enabled ? 'signedOut' : 'disabled', profile: null });
  }

  /** Lê os tokens guardados, sem rede. Sem client_id não faz nada. */
  async function init(): Promise<void> {
    if (!enabled) return;
    const tokens = await readTokens();
    emit(tokens ? { status: 'signedIn', profile: profileFromJwt(tokens.accessToken) } : { status: 'signedOut', profile: null });
  }

  async function signIn(): Promise<Profile | null> {
    const clientId = requireEnabled();
    const verifier = newVerifier();
    const state = newState();
    const pending: PendingLogin = { state, verifier, at: now() };
    await storage.set(PENDING_KEY, JSON.stringify(pending));
    const url = authorizeUrl(config.authUrl, clientId, state, await challengeS256(verifier));
    let result: AuthSessionResult;
    try {
      result = await deps.openAuthSession(url, REDIRECT_URI);
    } catch {
      await storage.remove(PENDING_KEY);
      throw new AccountError('unavailable');
    }
    if (result.type !== 'success' || !result.url) {
      await storage.remove(PENDING_KEY);
      throw new AccountError('cancelled');
    }
    return completeSignIn(result.url);
  }

  /** Valida o retorno (URI, state, 10 min) e troca o código por tokens. O pendente é usado uma única vez. */
  async function completeSignIn(redirectUrl: string): Promise<Profile | null> {
    const clientId = requireEnabled();
    let pending: PendingLogin | null = null;
    try {
      const raw = await storage.get(PENDING_KEY);
      pending = raw ? (JSON.parse(raw) as PendingLogin) : null;
    } catch {
      pending = null;
    }
    await storage.remove(PENDING_KEY);
    const code = parseRedirect(redirectUrl, pending, now());
    const res = await httpRequest(deps.fetch, `${config.authUrl}/oauth/token`, {
      method: 'POST',
      form: codeExchangeForm(clientId, code, pending!.verifier),
    });
    if (!isOk(res)) throw authError(res);
    const tokens = parseTokens(res.json, now());
    await storeTokens(tokens);
    return profileFromJwt(tokens.accessToken);
  }

  function authError(res: HttpResponse): AccountError {
    const code = errorCode(res.json);
    if (code === 'invalid_client') return new AccountError('invalid_client');
    if (code === 'invalid_grant') return new AccountError('invalid_grant');
    return new AccountError('unavailable');
  }

  /** Apaga os tokens do aparelho e revoga o refresh token no Auth BFF (melhor esforço). */
  async function signOut(): Promise<void> {
    const tokens = await readTokens();
    await clearSession();
    if (tokens && config.clientId) {
      try {
        await httpRequest(deps.fetch, `${config.authUrl}/oauth/revoke`, {
          method: 'POST',
          form: revokeForm(config.clientId, tokens.refreshToken),
        });
      } catch {
        // revogação é melhor esforço; os tokens locais já foram apagados
      }
    }
  }

  async function refresh(tokens: Tokens): Promise<Tokens> {
    const clientId = requireEnabled();
    const res = await httpRequest(deps.fetch, `${config.authUrl}/oauth/token`, {
      method: 'POST',
      form: refreshForm(clientId, tokens.refreshToken),
    });
    if (res.status === 400 || res.status === 401) {
      await clearSession();
      throw new AccountError('session_expired');
    }
    if (!isOk(res)) throw new AccountError('unavailable');
    const fresh = parseTokens(res.json, now());
    await storeTokens(fresh);
    return fresh;
  }

  /** Access token válido; renova 60 s antes de expirar (ou já, com `force`). Uma renovação por vez. */
  async function getAccessToken(force = false): Promise<string> {
    requireEnabled();
    const tokens = await readTokens();
    if (!tokens) {
      if (snapshot.status === 'signedIn') emit({ status: 'signedOut', profile: null });
      throw new AccountError('not_signed_in');
    }
    if (!force && !needsRefresh(tokens, now())) return tokens.accessToken;
    if (!refreshing) {
      refreshing = refresh(tokens).finally(() => {
        refreshing = null;
      });
    }
    return (await refreshing).accessToken;
  }

  /** Requisição autenticada: 401 → força refresh e tenta de novo uma vez; 401 de novo desconecta. */
  async function authorizedRequest(
    url: string,
    opts: Omit<RequestOptions, 'bearer'>,
    policy: { disconnectOnForbidden?: boolean } = {},
  ): Promise<HttpResponse> {
    let token = await getAccessToken();
    let res = await httpRequest(deps.fetch, url, { ...opts, bearer: token });
    if (res.status === 401) {
      token = await getAccessToken(true);
      res = await httpRequest(deps.fetch, url, { ...opts, bearer: token });
      if (res.status === 401) {
        await clearSession();
        throw new AccountError('session_expired');
      }
    }
    if (isOk(res)) return res;
    const code = errorCode(res.json);
    // API do AntiSpam: 403 `forbidden` = token não emitido para este app → nova sessão é necessária.
    if (policy.disconnectOnForbidden && res.status === 403 && (code === 'forbidden' || code === null)) {
      await clearSession();
      throw new AccountError('reauth_required');
    }
    // 503 (JWKS do Auth BFF fora, Retry-After) não desconecta: só informa.
    throw errorFromResponse(res.status, code);
  }

  return {
    config,
    enabled,
    init,
    signIn,
    completeSignIn,
    signOut,
    getAccessToken,
    authorizedRequest,
    getSnapshot: () => snapshot,
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

export type AccountSession = ReturnType<typeof createSession>;
