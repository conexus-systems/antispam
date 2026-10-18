/**
 * Retornos do navegador (login e pagamento) chegam como deep links `antispambr://oauth/callback` e
 * `antispambr://billing/return`. O login é concluído pelo `openAuthSessionAsync` (que recebe a URL),
 * então o roteador não deve navegar para uma rota inexistente: fica onde está ou abre a tela da conta
 * / de doação se o app foi aberto do zero por esse link.
 */
export function redirectSystemPath({ path, initial }: { path: string; initial: boolean }): string | null {
  try {
    if (/(^|\/\/|\/)oauth\/callback/.test(path)) return initial ? '/account' : null;
    if (/(^|\/\/|\/)billing\/return/.test(path)) return initial ? '/donate' : null;
    return path;
  } catch {
    return '/';
  }
}
