import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, View, useWindowDimensions } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { MetroButton } from "./MetroButton";
import { MetroText } from "./MetroText";
import { useMetro } from "./theme";
import { borders, dialogButtonOrder, spacing, type DialogButtonStyle } from "./tokens";

export type MetroDialogButton = {
  readonly text: string;
  readonly onPress?: () => void;
  /** Mesmos estilos do Alert.alert: "cancel" vai por último; "destructive" fica vermelho. */
  readonly style?: DialogButtonStyle;
};

type Props = {
  readonly visible: boolean;
  readonly title: string;
  readonly message?: string;
  readonly buttons?: readonly MetroDialogButton[];
  /** Chamado por qualquer botão, pela tecla Voltar e (se cancelable) pelo toque no fundo. */
  readonly onDismiss: () => void;
  readonly cancelable?: boolean;
  readonly children?: ReactNode;
};

/**
 * Diálogo de conteúdo W10M: painel chrome no topo da tela em largura total (celular) ou 480 dp no centro
 * (tablet/paisagem), fundo escurecido 60%, título SUBTITLE, mensagem e botões lado a lado.
 */
export function MetroDialog({ visible, title, message, buttons, onDismiss, cancelable = true, children }: Props) {
  const { c } = useMetro();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const wide = width >= 600 || width > height;
  const ordered = dialogButtonOrder(buttons?.length ? buttons : [{ text: "OK" }]);
  const primary = ordered.find((b) => b.style !== "cancel");

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onDismiss} statusBarTranslucent>
      <View style={[styles.root, wide && styles.center]}>
        <Pressable
          style={[StyleSheet.absoluteFill, { backgroundColor: c.scrim }]}
          onPress={cancelable ? onDismiss : undefined}
          accessibilityLabel="Fechar"
          accessibilityRole="button"
          importantForAccessibility={cancelable ? "yes" : "no-hide-descendants"}
        />
        <View
          accessibilityViewIsModal
          style={[
            styles.panel,
            { backgroundColor: c.chrome, borderColor: c.divider },
            wide ? styles.panelWide : { paddingTop: insets.top + spacing.lg },
          ]}
        >
          <MetroText variant="subtitle" accessibilityRole="header">
            {title}
          </MetroText>
          <ScrollView bounces={false} style={styles.body} keyboardShouldPersistTaps="handled">
            {message ? <MetroText style={styles.message}>{message}</MetroText> : null}
            {children}
          </ScrollView>
          <View style={styles.buttons}>
            {ordered.map((button) => (
              <MetroButton
                key={button.text}
                label={button.text}
                block
                style={styles.button}
                variant={button === primary ? (button.style === "destructive" ? "danger" : "primary") : "secondary"}
                onPress={() => {
                  onDismiss();
                  button.onPress?.();
                }}
              />
            ))}
          </View>
        </View>
      </View>
    </Modal>
  );
}

type AlertRequest = { readonly title: string; readonly message?: string; readonly buttons?: readonly MetroDialogButton[] };

type DialogApi = {
  /** Substituto do Alert.alert com o diálogo W10M (mesma assinatura). */
  readonly alert: (title: string, message?: string, buttons?: readonly MetroDialogButton[]) => void;
};

const DialogContext = createContext<DialogApi | null>(null);

/** Hospeda os diálogos pedidos com `useMetroDialog().alert(...)`, um por vez, em ordem. */
export function MetroDialogHost({ children }: { readonly children: ReactNode }) {
  const [queue, setQueue] = useState<readonly AlertRequest[]>([]);
  const alert = useCallback((title: string, message?: string, buttons?: readonly MetroDialogButton[]) => {
    setQueue((current) => [...current, { title, message, buttons }]);
  }, []);
  const api = useMemo(() => ({ alert }), [alert]);
  const current = queue[0];
  const dismiss = useCallback(() => setQueue((items) => items.slice(1)), []);
  return (
    <DialogContext.Provider value={api}>
      {children}
      {current ? (
        <MetroDialog visible title={current.title} message={current.message} buttons={current.buttons} onDismiss={dismiss} />
      ) : null}
    </DialogContext.Provider>
  );
}

export function useMetroDialog(): DialogApi {
  const api = useContext(DialogContext);
  if (!api) throw new Error("[MetroDialog] useMetroDialog precisa de MetroProvider");
  return api;
}

const styles = StyleSheet.create({
  root: { flex: 1, justifyContent: "flex-start" },
  center: { justifyContent: "center", alignItems: "center", padding: spacing.xl },
  panel: {
    alignSelf: "stretch",
    borderBottomWidth: borders.panel,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.lg,
    paddingBottom: spacing.xl,
    maxHeight: "85%",
  },
  panelWide: { alignSelf: "center", width: 480, maxWidth: "100%", borderWidth: borders.panel },
  body: { flexGrow: 0 },
  message: { marginTop: spacing.md },
  buttons: { flexDirection: "row", gap: spacing.sm, marginTop: spacing.xl },
  button: { flex: 1 },
});
