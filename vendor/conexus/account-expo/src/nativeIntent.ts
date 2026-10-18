/**
 * Retornos do navegador chegam como deep link `<scheme>://oauth/callback`. Quem conclui o login é o
 * `openAuthSessionAsync` (que recebe a URL), então o expo-router não deve navegar para uma rota inexistente: fica
 * onde está ou, se o app foi aberto do zero por esse link, vai para `afterLogin`.
 *
 * `app/+native-intent.tsx`:
 * ```ts
 * import { createConexusNativeIntent } from "@conexus/account-expo";
 * export const redirectSystemPath = createConexusNativeIntent("finhub", { afterLogin: "/login" });
 * ```
 */
export type NativeIntentOptions = {
  /** Rota quando o app é aberto do zero pelo retorno do login (padrão "/"). */
  readonly afterLogin?: string;
  /** Outros retornos do navegador: caminho do deep link → rota (ex.: `{ "billing/return": "/billing" }`). */
  readonly routes?: Readonly<Record<string, string>>;
  /** Rota quando algo dá errado ao interpretar o link (padrão "/"). */
  readonly fallback?: string;
};

export type RedirectSystemPath = (event: { path: string; initial: boolean }) => string | null;

function escape(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export function createConexusNativeIntent(scheme: string, options: NativeIntentOptions = {}): RedirectSystemPath {
  const { afterLogin = "/", routes = {}, fallback = "/" } = options;
  const host = `(^|${escape(scheme.toLowerCase())}://|//|/)`;
  const handlers: [RegExp, string][] = [
    [new RegExp(`${host}oauth/callback([?#]|$)`), afterLogin],
    ...Object.entries(routes).map(([path, route]): [RegExp, string] => [
      new RegExp(`${host}${escape(path.replace(/^\/+/, ""))}([?#/]|$)`),
      route,
    ]),
  ];
  return ({ path, initial }) => {
    try {
      for (const [pattern, route] of handlers) if (pattern.test(path)) return initial ? route : null;
      return path;
    } catch {
      return fallback;
    }
  };
}
