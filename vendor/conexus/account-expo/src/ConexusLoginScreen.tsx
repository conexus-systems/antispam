import {
  MetroButton,
  MetroEnter,
  MetroHeader,
  MetroNotice,
  MetroPage,
  MetroProgress,
  MetroScreen,
  MetroText,
  spacing,
} from "@conexus/metro-ui";
import { useState, type ReactNode } from "react";
import { StyleSheet, View } from "react-native";

import { useConexusAccount } from "./context";
import type { ConexusUser } from "./session";

export type ConexusLoginScreenProps = {
  /** Nome do app, em maiúsculas no kicker. */
  readonly appName: string;
  /** Título grande (padrão "entrar"). */
  readonly title?: string;
  /** Texto sob o título (o que a conta dá neste app). */
  readonly message?: string;
  /** Chamado depois de entrar/criar conta com sucesso (ex.: `router.replace("/")`). */
  readonly onSignedIn?: (user: ConexusUser | null) => void;
  /** Mostra "Continuar sem conta" (apps que funcionam sem login). */
  readonly onGuest?: () => void;
  readonly guestLabel?: string;
  /** Voltar no cabeçalho (quando a tela é empilhada). */
  readonly onBack?: () => void;
  /** Conteúdo extra abaixo dos botões (termos, privacidade). */
  readonly footer?: ReactNode;
};

/**
 * Tela de login padrão Conexus (W10M): só "Entrar com a Conta Conexus" e "Criar conta" — senha, Google e cadastro
 * acontecem no bff-auth, no navegador do sistema. Opcionalmente "Continuar sem conta".
 */
export function ConexusLoginScreen({
  appName,
  title = "entrar",
  message = "Use a sua Conta Conexus, a mesma de todos os apps Conexus.",
  onSignedIn,
  onGuest,
  guestLabel = "Continuar sem conta",
  onBack,
  footer,
}: ConexusLoginScreenProps) {
  const account = useConexusAccount();
  const [busy, setBusy] = useState<"signin" | "signup" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function run(kind: "signin" | "signup") {
    setBusy(kind);
    setError(null);
    try {
      const user = await (kind === "signin" ? account.signIn() : account.signUp());
      onSignedIn?.(user);
    } catch (e) {
      setError(account.errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  let body: ReactNode;
  if (account.status === "loading") {
    body = <MetroProgress accessibilityLabel="Lendo sessão" />;
  } else if (account.status === "disabled") {
    body = (
      <MetroNotice title="Em breve" tone="info">
        O login com a Conta Conexus ainda não está ativo nesta versão do app.
      </MetroNotice>
    );
  } else {
    body = (
      <View style={styles.actions}>
        <MetroButton
          label="Entrar com a Conta Conexus"
          variant="primary"
          block
          loading={busy === "signin"}
          disabled={busy !== null}
          onPress={() => void run("signin")}
          testID="conexus-signin"
        />
        <MetroButton
          label="Criar conta"
          block
          loading={busy === "signup"}
          disabled={busy !== null}
          onPress={() => void run("signup")}
          testID="conexus-signup"
        />
        {error ? (
          <MetroText tone="danger" accessibilityRole="alert" accessibilityLiveRegion="polite">
            {error}
          </MetroText>
        ) : null}
        <MetroText variant="caption">
          Abre a página segura da Conta Conexus no navegador. Sua senha nunca passa pelo app.
        </MetroText>
      </View>
    );
  }

  return (
    <MetroScreen>
      <MetroHeader kicker={appName} title={title} onBack={onBack} />
      <MetroPage scroll>
        <MetroEnter>
          <MetroText style={styles.message}>{message}</MetroText>
        </MetroEnter>
        {body}
        {onGuest && account.status !== "loading" ? (
          <MetroButton label={guestLabel} variant="text" onPress={onGuest} disabled={busy !== null} style={styles.guest} testID="conexus-guest" />
        ) : null}
        {footer}
      </MetroPage>
    </MetroScreen>
  );
}

const styles = StyleSheet.create({
  message: { marginBottom: spacing.xl },
  actions: { gap: spacing.md },
  guest: { marginTop: spacing.lg },
});
