# Migração para o padrão Conta Conexus — roteiro de produção (AntiSpam BR 0.4.0)

O AntiSpam BR é só app Expo (sem `web/`) e **não tem usuários locais**: desde a 0.3.0 a identidade é a Conta
Conexus (bff-auth) e os dados da conta ficam na API compartilhada (bff-api, `/api/v1/antispam/*`, chaveados pelo
`sub` do token). Por isso não há exportação/importação de usuários nem migration de limpeza neste repositório:
a 0.4.0 troca o código próprio de login pelo pacote `@conexus/account-expo` e o kit W10M por `@conexus/metro-ui`,
mantendo as mesmas chaves do `expo-secure-store` (quem já estava logado continua logado).

## (a) bff-auth — registrar o client mobile (uma vez)

Num pod `platform-auth-web`, com o comando idempotente do bff-auth (`lib/tasks/oauth_clients_upsert.rake`;
rodar de novo não duplica o client nem remove URIs):

```bash
bin/rails oauth_clients:upsert NAME="AntiSpam BR" PUBLIC=true REDIRECT_URIS=antispambr://oauth/callback
# público, PKCE S256, sem segredo; o client_id impresso → CONEXUS_CLIENT_ID (b)
```

- O scheme `antispambr` não é DNS reverso (RFC 8252 §7.1) e é aceito pela lista exata do bff-auth
  (`OAuthClient::ALLOWED_CUSTOM_SCHEMES`).
- Sem client web (não há `web/`), sem `POST_LOGOUT_URIS` e sem `WEBHOOK_URL`: o app não guarda
  espelho de usuário, então não há o que sincronizar por webhook.
- Cadastro aberto (`registration_mode` padrão): qualquer pessoa pode criar a conta pelo botão "Criar conta".
- Exclusão de conta: página do bff-auth `https://auth.conexus-systems.com.br/conta/excluir` (link no app e na Play).

## (b) Variáveis

| Onde | Variável | Valor |
|---|---|---|
| GitHub (Settings → Variables → Actions) | `CONEXUS_CLIENT_ID` | `client_id` do passo (a). O workflow `github-release-apk.yml` já o passa como `EXPO_PUBLIC_CONEXUS_CLIENT_ID` |
| Build (opcional) | `EXPO_PUBLIC_CONEXUS_AUTH_URL` | vazio = `https://auth.conexus-systems.com.br` (padrão no código) |
| Build (opcional) | `EXPO_PUBLIC_CONEXUS_API_URL` | vazio = `https://api.conexus-systems.com.br` |
| Build (opcional) | `EXPO_PUBLIC_CONEXUS_PAYMENTS_URL` | vazio = `https://payments.conexus-systems.com.br` |
| bff-api | `CONEXUS_AUTH_ALLOWED_AUDIENCES` | acrescentar `<client_id>` |
| bff-api | `CONEXUS_AUTH_AUDIENCE_APPS` | acrescentar `<client_id>:antispam-br` |
| bff-payment | `CONEXUS_AUTH_ALLOWED_AUDIENCES` | acrescentar `<client_id>` |
| bff-payment | `CONEXUS_ALLOWED_RETURN_SCHEMES` | acrescentar `antispambr` |

Todas as `EXPO_PUBLIC_*` são públicas (vão no bundle). Sem `CONEXUS_CLIENT_ID` o APK sai com o login desligado
("Em breve") e o resto do app funciona igual.

## (c) Usuários existentes

Nada a fazer: não há tabela de usuários no app. As sessões abertas na 0.3.0 continuam (chaves `conexus.tokens` e
`conexus.pending_login`, mesmo formato de tokens). Backup e denúncias já estão no bff-api pelo `sub`.

## (d) Deploy

1. bff-api: fazer merge e deploy do PR conexus-systems/bff-api#81 (rotas `/api/v1/antispam/{backup,reports,me}`)
   e aplicar as variáveis de (b). Conferir com um token do AntiSpam: `GET /api/v1/antispam/reports` → 200.
2. bff-payment: variáveis de (b).
3. Gerar o APK pela Action "GitHub release (APK)" com `CONEXUS_CLIENT_ID` definido.
4. Teste no aparelho: Conta › Entrar ou criar conta → navegador → volta logado; Fazer backup agora; Histórico ›
   Denunciar com "Enviar com minha conta"; Sair; Excluir a Conta Conexus abre a página do bff-auth.
5. Play Console: *Data safety* e link de exclusão conforme [ACCOUNT.md](ACCOUNT.md#play-console-data-safety).

## (e) Depois

- CI: trocar `.github/workflows/github-release-apk.yml` pelo caller padrão (`conexus-platform/docs/ci-callers/antispam-br.yml`)
  com `conexus-vendor-check: true` quando o acesso do repositório à `conexus-platform` (privada) for liberado.
- Atualizar os pacotes: `bin/conexus-vendor <este repo>` na `conexus-platform`, commitar `vendor/conexus/**`.
- bff-api: quando adotar a gem `Conexus::Identity`, manter o contrato de erros do AntiSpam
  (`error_format: :flat`, `forbidden`/`unauthorized`/`service_unavailable`), do qual o app depende para mapear
  `403 forbidden` → "entre novamente" (`conexus-platform/docs/identity.md`).
