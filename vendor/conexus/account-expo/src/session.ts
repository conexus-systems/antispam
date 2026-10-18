/**
 * Sessão da Conta Conexus (porta de `BffSession.kt` do Conexus Launcher, via antispam-br/src/account/session.ts):
 * login no navegador do sistema com PKCE S256, troca de código, renovação 60 s antes de expirar (refresh recusado
 * desconecta), revogação ao sair e requisições autenticadas com 401 → refresh → nova tentativa única.
 * Sem dependências nativas: tudo é injetado (`./native` liga expo-secure-store/expo-web-browser/fetch).
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
} from "./authContract";
import type { ConexusAccountConfig } from "./config";
import { ConexusAccountError, errorFromResponse } from "./errors";
import { errorCode, httpRequest, isAllowedUrl, isOk, type FetchLike, type HttpResponse, type RequestOptions } from "./http";
import { challengeS256, newState, newVerifier } from "./pkce";

export interface SecureStorage {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  remove(key: string): Promise<void>;
}

export type AuthSessionResult = { type: "success"; url: string } | { type: string; url?: undefined };

/** `fetch` real (RN/whatwg) para `authedFetch`. */
export type RawFetch = (input: string, init?: RequestInit) => Promise<Response>;

export interface SessionDeps {
  config: ConexusAccountConfig;
  fetch: FetchLike;
  rawFetch?: RawFetch;
  storage: SecureStorage;
  now: () => number;
  openAuthSession: (url: string, redirectUri: string) => Promise<AuthSessionResult>;
}

export type ConexusAccountStatus = "disabled" | "loading" | "signedOut" | "signedIn";

/** Usuário Conexus: claims do token, enriquecidas com o espelho da API do produto (`GET /conexus/me`). */
export interface ConexusUser {
  /** conexus_user_id (claim `sub`). */
  id: string;
  email: string | null;
  name: string | null;
  avatarUrl: string | null;
  /** id interno do usuário no app (do `/conexus/me`), quando houver. */
  appUserId: string | number | null;
}

export interface ConexusAccountSnapshot {
  status: ConexusAccountStatus;
  user: ConexusUser | null;
}

export function userFromProfile(profile: Profile | null): ConexusUser | null {
  if (!profile) return null;
  return { id: profile.subject, email: profile.email, name: profile.name, avatarUrl: profile.avatarUrl ?? null, appUserId: null };
}

/** Junta o `/conexus/me` (`{id, conexus_user_id, email, name, avatar_url}`) ao usuário do token. Ignora outro `sub`. */
export function mergeMe(user: ConexusUser, me: unknown): ConexusUser {
  if (!me || typeof me !== "object") return user;
  const m = me as Record<string, unknown>;
  const str = (v: unknown) => (typeof v === "string" && v.trim() ? v.trim() : null);
  const conexusId = str(m.conexus_user_id);
  if (conexusId && conexusId !== user.id) return user;
  const appId = typeof m.id === "number" || typeof m.id === "string" ? m.id : null;
  return {
    id: user.id,
    email: str(m.email) ?? user.email,
    name: str(m.name) ?? user.name,
    avatarUrl: str(m.avatar_url) ?? user.avatarUrl,
    appUserId: appId,
  };
}

export function createConexusSession(deps: SessionDeps) {
  const { config, storage, now } = deps;
  const enabled = config.clientId !== null;
  const TOKENS_KEY = `${config.storagePrefix}.tokens`;
  const PENDING_KEY = `${config.storagePrefix}.pending_login`;
  let snapshot: ConexusAccountSnapshot = { status: enabled ? "loading" : "disabled", user: null };
  const listeners = new Set<() => void>();
  let refreshing: Promise<Tokens> | null = null;
  let initialized: Promise<void> | null = null;

  function emit(next: ConexusAccountSnapshot) {
    snapshot = next;
    for (const listener of listeners) listener();
  }

  function requireEnabled(): string {
    if (!config.clientId) throw new ConexusAccountError("disabled");
    return config.clientId;
  }

  async function readTokens(): Promise<Tokens | null> {
    try {
      const raw = await storage.get(TOKENS_KEY);
      if (!raw) return null;
      const t = JSON.parse(raw) as Partial<Tokens>;
      if (typeof t.accessToken !== "string" || typeof t.refreshToken !== "string" || typeof t.expiresAt !== "number") return null;
      return t as Tokens;
    } catch {
      return null;
    }
  }

  function signedInWith(tokens: Tokens) {
    const fresh = userFromProfile(profileFromJwt(tokens.accessToken));
    // Mantém o que veio do /conexus/me enquanto for o mesmo usuário.
    const previous = snapshot.user;
    const user = fresh && previous && previous.id === fresh.id ? { ...previous, ...stripNulls(fresh), appUserId: previous.appUserId } : fresh;
    emit({ status: "signedIn", user });
  }

  async function storeTokens(tokens: Tokens): Promise<void> {
    await storage.set(TOKENS_KEY, JSON.stringify(tokens));
    signedInWith(tokens);
  }

  async function clearSession(): Promise<void> {
    try {
      await storage.remove(TOKENS_KEY);
    } catch {
      // nada a fazer
    }
    emit({ status: enabled ? "signedOut" : "disabled", user: null });
  }

  /** Lê os tokens guardados (sem rede) e, se houver API, atualiza o usuário pelo `/conexus/me` em segundo plano. */
  function init(): Promise<void> {
    if (!enabled) return Promise.resolve();
    initialized ??= (async () => {
      const tokens = await readTokens();
      if (!tokens) {
        emit({ status: "signedOut", user: null });
        return;
      }
      signedInWith(tokens);
      void refreshUser().catch(() => undefined);
    })();
    return initialized;
  }

  async function startLogin(screen?: "signup"): Promise<ConexusUser | null> {
    const clientId = requireEnabled();
    const verifier = newVerifier();
    const state = newState();
    const pending: PendingLogin = { state, verifier, at: now() };
    await storage.set(PENDING_KEY, JSON.stringify(pending));
    const url = authorizeUrl({
      authUrl: config.authUrl,
      clientId,
      redirectUri: config.redirectUri,
      state,
      challenge: await challengeS256(verifier),
      screen,
      scope: config.scope,
    });
    let result: AuthSessionResult;
    try {
      result = await deps.openAuthSession(url, config.redirectUri);
    } catch {
      await storage.remove(PENDING_KEY);
      throw new ConexusAccountError("unavailable");
    }
    if (result.type !== "success" || !result.url) {
      await storage.remove(PENDING_KEY);
      throw new ConexusAccountError("cancelled");
    }
    return completeSignIn(result.url);
  }

  /** "Entrar com a Conta Conexus": abre o authorize do bff-auth no navegador do sistema (Custom Tabs). */
  const signIn = () => startLogin();
  /** "Criar conta": o mesmo authorize, abrindo direto o cadastro (`screen=signup`). */
  const signUp = () => startLogin("signup");

  /** Valida o retorno (URI, state, 10 min) e troca o código por tokens. O pendente é usado uma única vez. */
  async function completeSignIn(redirectUrl: string): Promise<ConexusUser | null> {
    const clientId = requireEnabled();
    let pending: PendingLogin | null = null;
    try {
      const raw = await storage.get(PENDING_KEY);
      pending = raw ? (JSON.parse(raw) as PendingLogin) : null;
    } catch {
      pending = null;
    }
    await storage.remove(PENDING_KEY);
    const code = parseRedirect(redirectUrl, config.redirectUri, pending, now());
    const res = await httpRequest(deps.fetch, `${config.authUrl}/oauth/token`, {
      method: "POST",
      form: codeExchangeForm(clientId, config.redirectUri, code, pending!.verifier),
    });
    if (!isOk(res)) throw tokenError(res);
    await storeTokens(parseTokens(res.json, now()));
    try {
      return await refreshUser();
    } catch (error) {
      // A API do produto pode recusar (403 account_inactive/identity_conflict): o login no bff-auth valeu, mas o
      // app não aceita esta conta — desconecta e informa.
      if (error instanceof ConexusAccountError && (error.code === "account_inactive" || error.code === "identity_conflict")) {
        await clearSession();
        throw error;
      }
      return snapshot.user;
    }
  }

  function tokenError(res: HttpResponse): ConexusAccountError {
    const code = errorCode(res.json);
    if (code === "invalid_client") return new ConexusAccountError("invalid_client");
    if (code === "invalid_grant") return new ConexusAccountError("invalid_grant");
    return new ConexusAccountError("unavailable");
  }

  /** Apaga os tokens do aparelho e revoga o refresh token no bff-auth (melhor esforço). */
  async function signOut(): Promise<void> {
    const tokens = await readTokens();
    await clearSession();
    if (tokens && config.clientId) {
      try {
        await httpRequest(deps.fetch, `${config.authUrl}/oauth/revoke`, {
          method: "POST",
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
      method: "POST",
      form: refreshForm(clientId, tokens.refreshToken),
    });
    if (res.status === 400 || res.status === 401) {
      await clearSession();
      throw new ConexusAccountError("session_expired");
    }
    if (!isOk(res)) throw new ConexusAccountError("unavailable");
    const fresh = parseTokens(res.json, now());
    await storeTokens(fresh);
    return fresh;
  }

  /** Access token válido; renova 60 s antes de expirar (ou já, com `force`). Uma renovação por vez. */
  async function getAccessToken(force = false): Promise<string> {
    requireEnabled();
    const tokens = await readTokens();
    if (!tokens) {
      if (snapshot.status === "signedIn") emit({ status: "signedOut", user: null });
      throw new ConexusAccountError("not_signed_in");
    }
    if (!force && !needsRefresh(tokens, now())) return tokens.accessToken;
    refreshing ??= refresh(tokens).finally(() => {
      refreshing = null;
    });
    return (await refreshing).accessToken;
  }

  /** Caminho relativo (`/x`) vira `apiBaseUrl + /x`; URL absoluta passa direto. */
  function resolveUrl(pathOrUrl: string): string {
    if (/^https?:\/\//.test(pathOrUrl)) return pathOrUrl;
    if (!config.apiBaseUrl) throw new ConexusAccountError("not_configured");
    return `${config.apiBaseUrl}${pathOrUrl.startsWith("/") ? "" : "/"}${pathOrUrl}`;
  }

  /**
   * Requisição JSON autenticada na API do produto: 401 → força refresh e tenta de novo uma vez; 401 de novo
   * desconecta (`session_expired`). Outros erros viram `ConexusAccountError` com mensagem fixa.
   * `disconnectOnForbidden`: 403 `forbidden`/`account_inactive` encerra a sessão local (token não serve a este app).
   */
  async function authorizedRequest(
    pathOrUrl: string,
    opts: Omit<RequestOptions, "bearer"> = { method: "GET" },
    policy: { disconnectOnForbidden?: boolean } = {},
  ): Promise<HttpResponse> {
    const url = resolveUrl(pathOrUrl);
    let token = await getAccessToken();
    let res = await httpRequest(deps.fetch, url, { ...opts, bearer: token });
    if (res.status === 401) {
      token = await getAccessToken(true);
      res = await httpRequest(deps.fetch, url, { ...opts, bearer: token });
      if (res.status === 401) {
        await clearSession();
        throw new ConexusAccountError("session_expired");
      }
    }
    if (isOk(res)) return res;
    const code = errorCode(res.json);
    if (res.status === 403 && (code === "account_inactive" || (policy.disconnectOnForbidden && (code === "forbidden" || code === null)))) {
      await clearSession();
    }
    // 503 (JWKS do bff-auth fora, Retry-After) não desconecta: só informa.
    throw errorFromResponse(res.status, code);
  }

  /**
   * `fetch` autenticado (Response real, para quem já tem cliente próprio): põe `Authorization: Bearer`, e em 401
   * renova o token e repete uma vez; o segundo 401 desconecta e devolve a resposta. Caminho relativo usa `apiBaseUrl`.
   */
  async function authedFetch(pathOrUrl: string, init: RequestInit = {}): Promise<Response> {
    const raw = deps.rawFetch;
    if (!raw) throw new ConexusAccountError("not_configured");
    const url = resolveUrl(pathOrUrl);
    if (!isAllowedUrl(url)) throw new ConexusAccountError("insecure_url");
    const send = (token: string) => {
      const headers = new Headers(init.headers);
      headers.set("Authorization", `Bearer ${token}`);
      if (!headers.has("Accept")) headers.set("Accept", "application/json");
      return raw(url, { ...init, headers, credentials: "omit" });
    };
    let res = await send(await getAccessToken());
    if (res.status === 401) {
      res = await send(await getAccessToken(true));
      if (res.status === 401) await clearSession();
    }
    return res;
  }

  /** Atualiza o usuário pelo `GET <apiBaseUrl><mePath>` (espelho do app). Sem API configurada, só o token. */
  async function refreshUser(): Promise<ConexusUser | null> {
    if (snapshot.status !== "signedIn" || !snapshot.user) return snapshot.user;
    if (!config.apiBaseUrl || !config.mePath) return snapshot.user;
    const res = await authorizedRequest(config.mePath, { method: "GET" });
    const current = snapshot.user;
    if (snapshot.status === "signedIn" && current) emit({ status: "signedIn", user: mergeMe(current, res.json) });
    return snapshot.user;
  }

  return {
    config,
    enabled,
    init,
    signIn,
    signUp,
    completeSignIn,
    signOut,
    getAccessToken,
    authorizedRequest,
    authedFetch,
    refreshUser,
    getSnapshot: () => snapshot,
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}

function stripNulls<T extends object>(value: T): Partial<T> {
  return Object.fromEntries(Object.entries(value).filter(([, v]) => v !== null && v !== undefined)) as Partial<T>;
}

export type ConexusSession = ReturnType<typeof createConexusSession>;
