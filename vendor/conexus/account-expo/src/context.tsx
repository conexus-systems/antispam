import { createContext, useContext, useEffect, useMemo, useSyncExternalStore, type ReactNode } from "react";

import { accountErrorMessage } from "./errors";
import { getConexusAccount, openConexusPage } from "./native";
import type { ConexusAccountStatus, ConexusSession, ConexusUser } from "./session";

const AccountContext = createContext<ConexusSession | null>(null);

type ProviderProps = {
  /** Sessão a usar (testes, mais de uma conta). Padrão: a de `configureConexusAccount`. */
  readonly account?: ConexusSession;
  readonly children: ReactNode;
};

/** Disponibiliza a Conta Conexus e lê a sessão guardada ao montar (sem rede quando não há login). */
export function ConexusAccountProvider({ account, children }: ProviderProps) {
  const session = account ?? getConexusAccount();
  useEffect(() => {
    void session.init().catch(() => undefined);
  }, [session]);
  return <AccountContext.Provider value={session}>{children}</AccountContext.Provider>;
}

export type UseConexusAccount = {
  readonly status: ConexusAccountStatus;
  readonly user: ConexusUser | null;
  readonly isSignedIn: boolean;
  /** Login desligado (sem `clientId`). */
  readonly isDisabled: boolean;
  /** "Entrar com a Conta Conexus". Lança `ConexusAccountError` (use `accountErrorMessage`). */
  readonly signIn: () => Promise<ConexusUser | null>;
  /** "Criar conta" (authorize com `screen=signup`). */
  readonly signUp: () => Promise<ConexusUser | null>;
  readonly signOut: () => Promise<void>;
  /** Busca de novo o espelho do usuário na API do produto. */
  readonly refreshUser: () => Promise<ConexusUser | null>;
  /** `fetch` autenticado com refresh-on-401. */
  readonly authedFetch: ConexusSession["authedFetch"];
  /** Requisição JSON autenticada com erros tipados. */
  readonly authorizedRequest: ConexusSession["authorizedRequest"];
  readonly getAccessToken: ConexusSession["getAccessToken"];
  /** Página de exclusão da conta no bff-auth (exigência das lojas). */
  readonly deleteAccountUrl: string;
  readonly manageAccountUrl: string;
  readonly openDeleteAccount: () => Promise<void>;
  readonly openManageAccount: () => Promise<void>;
  readonly errorMessage: (error: unknown) => string;
  readonly session: ConexusSession;
};

export function useConexusAccount(): UseConexusAccount {
  const session = useContext(AccountContext);
  if (!session) throw new Error("[ConexusAccount] useConexusAccount precisa de <ConexusAccountProvider>");
  const snapshot = useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot);
  return useMemo(
    () => ({
      status: snapshot.status,
      user: snapshot.user,
      isSignedIn: snapshot.status === "signedIn",
      isDisabled: snapshot.status === "disabled",
      signIn: session.signIn,
      signUp: session.signUp,
      signOut: session.signOut,
      refreshUser: session.refreshUser,
      authedFetch: session.authedFetch,
      authorizedRequest: session.authorizedRequest,
      getAccessToken: session.getAccessToken,
      deleteAccountUrl: session.config.accountDeletionUrl,
      manageAccountUrl: session.config.manageAccountUrl,
      openDeleteAccount: () => openConexusPage(session.config.accountDeletionUrl),
      openManageAccount: () => openConexusPage(session.config.manageAccountUrl),
      errorMessage: accountErrorMessage,
      session,
    }),
    [session, snapshot],
  );
}
