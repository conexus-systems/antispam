# Publicação na Google Play

## Estados — nunca tratar como equivalentes

1. **Build local concluído**: AAB gerado e verificado na máquina ou no EAS.
2. **Upload concluído**: a API aceitou a edição (confirmado por ID de edição).
3. **Release criada**: a release existe na track (interna, fechada ou produção).
4. **Release em revisão**: enviada ao Google para revisão.
5. **Aplicativo aprovado**: revisão concluída sem bloqueio.
6. **Aplicativo publicado**: visível para os usuários da track (confirmado no Console).

Cada etapa exige evidência própria no relatório.

## Track interna

- Primeira release: ainda **não verificado** se o app já existe na Play Console. Se estiver em rascunho, a primeira publicação é clique humano (regra da skill `play-console`).
- Upload via API só após confirmação de que o app existe e que a conta tem permissão.

## versionCode

- Fonte: `app.json` (`android.versionCode`), com `appVersionSource: local` no `eas.json`.
- Regra: o novo `versionCode` deve ser maior que o último publicado em qualquer track. Validar consultando a API antes do upload.
- Incremento: manual, por commit explícito. Nunca automático sem registro.

## Metadados e recursos

| Item | Estado |
|---|---|
| Ícone 512x512 | Não verificado |
| Feature graphic 1024x500 | Não verificado |
| Screenshots | Não verificado |
| Descrição curta e longa (pt-BR) | Não verificado |
| Política de privacidade (URL pública) | Não verificado (existe `docs/PRIVACY.md` interno) |
| Data safety | Não preenchido; proposta a gerar e confirmar |
| Classificação indicativa | Não preenchida |
| Público-alvo e conteúdo | Não preenchido; depende do proprietário |
| Declaração de anúncios (AdMob) | Não preenchida; depende da ativação de monetização |

A skill `brand-identity` gera ícone e feature graphic a partir de `fleet.yml`.

## Permissões declaradas

- `POST_NOTIFICATIONS`, `READ_PHONE_STATE`, `CALL_PHONE` (em `app.json`).
- `READ_PHONE_STATE` e `CALL_PHONE` exigem justificativa na Play Console e podem exigir revisão de política. **Não verificado.**

## Target API e requisitos

- Target API atual do Expo SDK 58: **não verificado nesta fase**. Confirmar em `expo-build-properties` ou no manifest gerado antes do envio.

## Promoção

- Interna → fechada → produção: apenas com aprovação explícita por track.
- Rollout de produção por percentual: exige decisão do proprietário.
