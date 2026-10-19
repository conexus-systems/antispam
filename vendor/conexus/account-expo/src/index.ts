/**
 * @conexus/account-expo — Conta Conexus (bff-auth) nos apps Expo: cliente OAuth público com PKCE S256, sessão no
 * expo-secure-store, refresh 60 s antes de expirar, revoke ao sair, fetch autenticado com refresh-on-401 e a tela
 * de login padrão em W10M. Fonte da verdade: conexus-platform/js/account-expo (vendorizado com bin/conexus-vendor).
 */
export {
  buildConfig,
  DEFAULT_AUTH_URL,
  DEFAULT_ME_PATH,
  redirectUriFor,
  sanitizeBaseUrl,
  sanitizeClientId,
  sanitizeScheme,
  type ConexusAccountConfig,
  type ConexusAccountOptions,
} from "./config";
export {
  accountErrorMessage,
  CONEXUS_ACCOUNT_ERROR_MESSAGES,
  ConexusAccountError,
  errorFromResponse,
  isConexusAccountError,
  type ConexusAccountErrorCode,
} from "./errors";
export {
  authorizeUrl,
  needsRefresh,
  parseRedirect,
  parseTokens,
  profileFromJwt,
  PENDING_TTL_MS,
  REFRESH_MARGIN_MS,
  type Profile,
  type Tokens,
} from "./authContract";
export { httpRequest, isAllowedUrl, type FetchLike, type HttpResponse, type RequestOptions } from "./http";
export { challengeS256, newState, newVerifier } from "./pkce";
export {
  createConexusSession,
  mergeMe,
  userFromProfile,
  type AuthSessionResult,
  type ConexusAccountSnapshot,
  type ConexusAccountStatus,
  type ConexusSession,
  type ConexusUser,
  type SecureStorage,
  type SessionDeps,
} from "./session";
export { configureConexusAccount, getConexusAccount, openConexusPage, secureStoreStorage } from "./native";
export { ConexusAccountProvider, useConexusAccount, type UseConexusAccount } from "./context";
export { ConexusLoginScreen, type ConexusLoginScreenProps } from "./ConexusLoginScreen";
export { createConexusNativeIntent, type NativeIntentOptions, type RedirectSystemPath } from "./nativeIntent";
