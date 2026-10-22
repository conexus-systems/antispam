# Segurança e credenciais

## Credenciais necessárias

| Credencial | Onde fica | Usada em | Privilégio mínimo |
|---|---|---|---|
| Conta de serviço da Play Developer API (JSON) | GitHub Secret `PLAY_SERVICE_ACCOUNT_JSON` | upload e status | Acesso só ao app `com.conexus.antispam`, só às tracks autorizadas |
| Token EAS (`EXPO_TOKEN`) | GitHub Secret | build | Conta do projeto `82eabd0b-…` |
| Upload keystore / Play App Signing | Gerenciado pelo Google Play (não exportar a chave privada) | assinatura | Preservar o existente |
| App ID e unidades AdMob de produção | Variáveis `EXPO_PUBLIC_ADMOB_*` no ambiente `production` do EAS | build de produção | Só unidades do próprio app |

Nenhuma dessas credenciais existe neste repositório. O pipeline deve **falhar com mensagem clara** se alguma faltar.

## Regras

1. Nenhum token, chave privada, keystore ou service account é gravado no Git.
2. Nenhum segredo é impresso em log. Scripts usam `::add-mask::` ou leitura por arquivo temporário fora do repo.
3. Não criar credencial falsa para contornar configuração ausente.
4. Validar acesso com operação de leitura antes de qualquer escrita (ex.: `edits.insert` seguido de `edits.delete`, ou apenas `tracks.list`).
5. Nenhuma operação de escrita em recurso remoto sem autorização registrada.
6. Produção: exige aprovação explícita por escrito do proprietário, a cada envio.
7. Testes e checagens de segurança nunca são desativados para deixar o build verde.

## Verificações de segurança do código

- Chaves de IDs AdMob de teste nunca vão para `production` (`app.config.js` falha o build).
- Não há segredo hardcoded: o CI `security-secrets` procura padrões de chave OpenRouter e PIX.
- Dependências: `npm audit --omit=dev` (informativo no CI). Não bloqueia hoje; propõe-se torná-lo bloqueante após triagem.

## Dados pessoais

- `docs/PRIVACY.md` descreve o tratamento local e o uso do SDK AdMob.
- Nenhuma declaração legal é marcada automaticamente. Data safety e política são propostas, com itens que dependem do proprietário sinalizados.
