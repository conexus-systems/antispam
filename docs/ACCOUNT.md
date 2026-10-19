# Conta Conexus no AntiSpam BR (opcional) — 0.4.0

O AntiSpam BR continua 100% funcional **sem conta e offline**. A Conta Conexus (a mesma do Conexus Launcher) é opcional e serve para:

- **Backup na nuvem** das listas, regras e ajustes — sempre manual ("Fazer backup agora" / "Restaurar deste backup").
- **Denúncias identificadas** — escolha por denúncia ("Enviar com minha conta", desligado por padrão). A denúncia local continua igual.
- **Doações pela conta** — checkout hospedado pelo Payments BFF e lista "Minhas doações". A doação Pix continua.

Nada é desbloqueado pela conta. Histórico de chamadas, contatos, base de reputação e a chave da IA **nunca** vão para o servidor.

## Onde fica no app

| Tela | O que tem |
|---|---|
| `app/account.tsx` (Ajustes › geral › Conta, ou "…" da app bar › Conta) | Entrar ou criar conta, nome e e-mail, Sair, backup (fazer/restaurar/apagar), Minhas denúncias (com exclusão), Excluir meus dados na nuvem, **Excluir a Conta Conexus** |
| `app/login.tsx` | Tela padrão `<ConexusLoginScreen/>`: só "Entrar com a Conta Conexus", "Criar conta" e "Continuar sem conta" |
| `app/(tabs)/history.tsx` › Denunciar | Opção "Enviar com minha conta" (só aparece logado; só números E.164 `+…`) |
| `app/donate.tsx` | "Doar pela Conta Conexus" e "Minhas doações" quando logado; Pix inalterado |

Desde a 0.4.0 o login, a sessão e o HTTP vêm do pacote compartilhado **`@conexus/account-expo`** (vendorizado em
`vendor/conexus/account-expo`, fonte da verdade em `conexus-platform/js/account-expo`, que nasceu deste
`src/account/`). O que é do AntiSpam BR ficou em `src/account/`:

| Arquivo | Papel |
|---|---|
| `index.ts` | `configureConexusAccount({ clientId, authUrl, apiBaseUrl, scheme: "antispambr", mePath: null })` com os `EXPO_PUBLIC_CONEXUS_*` e os padrões de produção; instâncias das APIs; backup/restauração; checkout |
| `api.ts` | `/api/v1/antispam/{backup,reports,me}` (via `authorizedRequest` da sessão, com `disconnectOnForbidden`) e `/v1/me/donations` (Payments BFF), validação campo a campo, erros do app (`AntispamError`: `reauth_required`, `number_not_supported`, `backup_invalid`, `checkout_insecure`) e `antispamErrorMessage` (mensagens fixas, com textos do app para rede/limites) |
| `cloudBackup.ts` | Serialização/restauração do backup (reaproveita a validação de `src/app/backup.ts`) |

`app/_layout.tsx` envolve o app em `MetroProvider` e `ConexusAccountProvider`; as telas usam `useConexusAccount()`.
`app/+native-intent.tsx` usa `createConexusNativeIntent("antispambr", { afterLogin: "/account", routes: { "billing/return": "/donate" } })`.

**Sessões da 0.3.0 continuam valendo:** o pacote usa as mesmas chaves do `expo-secure-store`
(`conexus.tokens`, `conexus.pending_login`) e o mesmo formato de tokens; quem atualiza o app não precisa entrar de novo.

## Fluxos

**Login.** O app gera `code_verifier` (32 bytes aleatórios do `expo-crypto`, 43 caracteres) e `state`, guarda os dois com o horário no `expo-secure-store` e abre no navegador do sistema (`WebBrowser.openAuthSessionAsync`):

```
GET {AUTH}/oauth/authorize?response_type=code&client_id=…&redirect_uri=antispambr://oauth/callback
    &state=…&code_challenge=…&code_challenge_method=S256
```

A página do Auth BFF oferece Google e e-mail/senha; "Criar conta" abre o mesmo authorize com `screen=signup`. A senha nunca passa pelo app. O retorno só é aceito com a mesma redirect URI, o mesmo `state` e em até 10 minutos; o pendente é apagado no primeiro uso. A troca é `POST {AUTH}/oauth/token` (form `grant_type=authorization_code, code, client_id, redirect_uri, code_verifier`, sem segredo). `app/+native-intent.tsx` impede o roteador de navegar para `oauth/callback`.

**Sessão.** Access token JWT (~15 min) e refresh opaco (30 dias, rotacionado) ficam no `expo-secure-store` (`AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY`). Antes de cada chamada, se faltar ≤ 60 s para expirar, renova com `grant_type=refresh_token` (uma renovação por vez). Refresh recusado (400/401) desconecta; erro de rede mantém a sessão. Resposta 401 de uma API força um refresh e repete a chamada uma vez; 401 de novo desconecta. Nome e e-mail exibidos vêm do payload do JWT, só para mostrar (quem valida a assinatura são os BFFs, via JWKS). **Sair** apaga os tokens do aparelho e chama `POST {AUTH}/oauth/revoke` (`token`, `client_id`).

**Backup.** "Fazer backup agora" mostra um resumo e, confirmado, envia:

```json
PUT {API}/api/v1/antispam/backup
{ "version": 1, "device_name": "Android",
  "data": { "blocked": ["+55…"], "allowed": ["+55…"], "rules": [ {"id","name","enabled","action","match"} ],
            "settings": { "mode": "BALANCED", "notifications": true, "haptics": true, "custom": { … só no modo Personalizado } } } }
```

Limite de 256 KB verificado antes do envio. "Restaurar deste backup" baixa (`GET`), valida com o mesmo validador estrito do backup local (schema, limites, regex), mostra quantos números bloqueados/permitidos e regras serão aplicados e só substitui o estado local depois da confirmação. "Apagar backup da nuvem" usa `DELETE`.

**Denúncias identificadas.** Na denúncia do Histórico, logado, aparece "Enviar com minha conta" (sempre começa desligado). A denúncia local acontece sempre; se marcado, também envia `POST {API}/api/v1/antispam/reports {number (E.164), category, occurred_at}`. Categorias locais são agrupadas nas do servidor: golpe/fraude/banco → `golpe`; telemarketing/spam/pesquisa/operadora → `telemarketing`; cobrança → `cobranca`; robocall → `robo`; resto → `outro`. O número é normalizado para E.164 (`+` e 7 a 15 dígitos, mesmo padrão do bff-api) antes do envio; números sem `+` (0800, 0303, curtos) não são enviados. O servidor limita o corpo a 16 KB (413). "Minhas denúncias" lista `GET …/reports?page=N` e exclui com `DELETE …/reports/:id`. Limite do servidor: 50 por dia (429 vira mensagem fixa).

**Exclusão.** "Excluir meus dados na nuvem" (com confirmação) chama `DELETE {API}/api/v1/antispam/me`, que apaga backup e denúncias. "Excluir a Conta Conexus" abre `https://auth.conexus-systems.com.br/conta/excluir` no navegador (ver abaixo).

**Doações.** Logado, a tela de doação usa o valor escolhido e chama `POST {PAYMENTS}/v1/me/donations {amount_cents (100..1000000), currency:"BRL", product_slug:"antispam-br", return_url:"antispambr://billing/return", message?}`; da resposta `201 {donation_id, checkout_url, provider, provider_ref}` só abre `checkout_url` se for HTTPS. "Minhas doações" lê `GET {PAYMENTS}/v1/me/donations` (`{donations:[{id, amount_cents, currency, status, paid_at, product_slug, created_at}]}`). Pelo código atual do bff-payment, `/v1/me/donations` usa o provedor padrão (`crypto`, NOWPayments); a doação Pix por BR Code local continua disponível.

**Erros.** Cada falha vira um código fixo (`ConexusAccountError` do pacote: `invalid_client`, `invalid_grant`, `unauthorized`, `forbidden`, `audience_not_allowed`, `not_found`, `validation_failed`, `payload_too_large`, `rate_limited`, `network`, …; `AntispamError` do app) com mensagem fixa (`antispamErrorMessage` em `src/account/api.ts`). Corpo de resposta (inclusive `details` de 422) e tokens nunca aparecem na tela nem em log. Casos especiais do bff-api: `503 service_unavailable` (JWKS do Auth BFF fora, `Retry-After: 30`) mostra "Serviço indisponível, tente em instantes" e **não** desconecta; `403 forbidden` (token que não é do AntiSpam) desconecta (`disconnectOnForbidden` do pacote) e vira `reauth_required` no app ("Esta sessão não vale para o AntiSpam BR. Entre novamente."); `429` das denúncias vale até a meia-noite de São Paulo.

## Configuração

| Variável (build) | Padrão | Uso |
|---|---|---|
| `EXPO_PUBLIC_CONEXUS_CLIENT_ID` | vazio | `client_id` público do OAuthClient. **Vazio = login desligado, nenhuma chamada de rede**, a tela Conta mostra "Em breve" |
| `EXPO_PUBLIC_CONEXUS_AUTH_URL` | `https://auth.conexus-systems.com.br` | Auth BFF |
| `EXPO_PUBLIC_CONEXUS_API_URL` | `https://api.conexus-systems.com.br` | API BFF (backup/denúncias) |
| `EXPO_PUBLIC_CONEXUS_PAYMENTS_URL` | `https://payments.conexus-systems.com.br` | Payments BFF |

O link "Excluir a Conta Conexus" é `<EXPO_PUBLIC_CONEXUS_AUTH_URL>/conta/excluir` (produção:
`https://auth.conexus-systems.com.br/conta/excluir`). URLs que não sejam `https://` são ignoradas (volta o padrão).
Nenhum segredo vai no app. Roteiro de produção: [MIGRACAO_CONTA_CONEXUS.md](MIGRACAO_CONTA_CONEXUS.md).

### O que o dono do projeto precisa fazer

1. **Auth BFF:** criar o `OAuthClient` público `name: "AntiSpam BR"`, `public_client: true`, sem segredo, `redirect_uris: ["antispambr://oauth/callback"]` (mesmo modelo do cliente do Conexus Launcher). Anotar o `client_id`.
2. **bff-api:** implantar as rotas `/api/v1/antispam/*` (PR conexus-systems/bff-api#81) e incluir o `client_id` do AntiSpam em `CONEXUS_AUTH_ALLOWED_AUDIENCES` e `CONEXUS_AUTH_AUDIENCE_APPS` (`<client_id>:antispam-br`).
3. **bff-payment:** incluir o `client_id` em `CONEXUS_AUTH_ALLOWED_AUDIENCES` e `antispambr` em `CONEXUS_ALLOWED_RETURN_SCHEMES` (senão o `return_url` é recusado com `validation_failed`).
4. **Build:** definir `EXPO_PUBLIC_CONEXUS_CLIENT_ID`.
   - GitHub release (APK): em *Settings → Secrets and variables → Actions → Variables*, criar `CONEXUS_CLIENT_ID`. O workflow `.github/workflows/github-release-apk.yml` passa `vars.CONEXUS_CLIENT_ID` como `EXPO_PUBLIC_CONEXUS_CLIENT_ID`; sem a variável o APK sai com o login desligado, sem erro.
   - EAS/local: `EXPO_PUBLIC_CONEXUS_CLIENT_ID=… npx expo …` ou variável de ambiente do EAS.
5. **Play Console:** atualizar *Data safety* e o link de exclusão de conta (ver "Play Console" abaixo).

## Excluir a Conta Conexus

- **Dados do AntiSpam BR na nuvem:** no app, *Conta › Excluir meus dados na nuvem* apaga backup e denúncias na hora. Denúncias também podem ser excluídas uma a uma em *Minhas denúncias*.
- **A Conta Conexus inteira (login, nome, e-mail):** no app, *Conta › Excluir a Conta Conexus*, ou direto em
  <https://auth.conexus-systems.com.br/conta/excluir> (página do Auth BFF; pede login e confirmação). A exclusão
  vale para todos os apps Conexus. Antes, use "Excluir meus dados na nuvem" se quiser apagar também os dados do
  AntiSpam BR guardados pela conta.

## Play Console (Data safety)

- **Link de exclusão de conta:** `https://auth.conexus-systems.com.br/conta/excluir` (campo "Delete account URL").
  Exclusão também disponível dentro do app (Conta › Excluir a Conta Conexus) e, só dos dados do AntiSpam, em
  Conta › Excluir meus dados na nuvem.
- **Dados coletados (só com a conta opcional):** informações pessoais › nome e e-mail (gestão de conta; vêm do
  login, não são digitados no app); atividade/conteúdo do app › backup das listas, regras e ajustes (funcionalidade,
  só quando o usuário toca em "Fazer backup agora") e denúncias identificadas (número, categoria, data; só as marcadas);
  informações financeiras › histórico de doações pela conta (valor; pagamento processado pelo Payments BFF).
- **Não coletados:** histórico de chamadas, contatos, base de reputação, chave da IA, localização, identificadores
  de publicidade. Sem conta, nada sai do aparelho além do que o usuário pedir à IA (OpenRouter, com a chave dele).
- **Opcional:** sim, o app funciona inteiro sem conta. **Criptografia em trânsito:** sim (HTTPS obrigatório).
  **Exclusão a pedido:** sim (acima).

## Testes

PKCE, contrato OAuth e sessão são testados no pacote (`conexus-platform/js/account-expo`). Aqui:
`__tests__/accountApi.test.ts` (sessão do pacote com a config do app: login com navegador simulado, refresh 60 s antes,
refresh recusado, refresh único, 401 → refresh → retry, 403 → `reauth_required` e desconexão, 429/404/413/422/503 com
mensagens fixas, revoke, doações, redirect `antispambr://oauth/callback`, link de exclusão e sessão gravada pela 0.3.0)
e `__tests__/cloudBackup.test.ts` (serialização sem histórico/chave de IA, ida e volta, rejeição de dados malformados).
