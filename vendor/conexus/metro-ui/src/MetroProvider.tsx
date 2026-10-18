import { MetroDialogHost } from "./MetroDialog";
import { MetroThemeProvider, type MetroThemeProviderProps } from "./theme";

/**
 * Tema W10M + hospedeiro de diálogos (`useMetroDialog().alert`). Envolve o app inteiro no layout raiz,
 * dentro do SafeAreaProvider (o expo-router já fornece um).
 */
export function MetroProvider({ children, ...theme }: MetroThemeProviderProps) {
  return (
    <MetroThemeProvider {...theme}>
      <MetroDialogHost>{children}</MetroDialogHost>
    </MetroThemeProvider>
  );
}
