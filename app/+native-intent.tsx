/**
 * Retornos do navegador (login e pagamento) chegam como deep links `antispambr://oauth/callback` e
 * `antispambr://billing/return`. O login é concluído pelo `openAuthSessionAsync` (que recebe a URL),
 * então o roteador não navega para uma rota inexistente: fica onde está ou abre a tela da conta
 * / de doação se o app foi aberto do zero por esse link.
 */
import { createConexusNativeIntent } from '@conexus/account-expo';

export const redirectSystemPath = createConexusNativeIntent('antispambr', {
  afterLogin: '/account',
  routes: { 'billing/return': '/donate' },
});
