# AntiSpam BR 🛡️

**Bloqueio inteligente de chamadas de spam — 100% open source, cadastro opcional, privacy-first.**

Feito para o Brasil: entende `+55`, DDD, 9º dígito, `0800`, `0303`, `4004/3003`, emergências (que **nunca** são bloqueadas), e toma decisões **explicáveis** em **< 100 ms**, totalmente **offline**.

> ⚠️ Nome provisório. Monetização: **nenhuma** — doação opcional via PIX (chave via env de build, nunca no código).

---

## ✨ O que já funciona (MVP)

| Funcionalidade | Status |
|---|---|
| Motor de decisão local (`ALLOW / WARN / SILENCE / BLOCK`) com fail-safe | ✅ 42 testes |
| Normalização BR (`(11) 99999-9999` ≡ `11999999999` ≡ `+5511999999999`) | ✅ |
| SpamScore 0–100 auditável (reputação + campanha + padrão + STIR/SHAKEN + histórico) | ✅ |
| Modos: Desativado / Básico / Equilibrado / Agressivo / Personalizado | ✅ |
| Whitelist, blacklist, regras por prefixo/regex/ocultos/internacionais | ✅ |
| Emergências (190, 192, 193…) e contatos: **nunca bloqueados** | ✅ |
| Denúncias locais com anti-abuse (denunciantes únicos, recência, falso-positivos) | ✅ |
| Detecção de campanhas (dezenas de números similares em 10 min) | ✅ |
| Histórico explicável ("por que bloqueou?") + tempo poupado (estimativa) | ✅ |
| Verificar número (antes de retornar ligação suspeita) | ✅ |
| IA Assistente opcional via **OpenRouter** (off por padrão, **nunca** decide bloqueio) | ✅ |
| Backup JSON validado (anti-payload-malicioso) | ✅ |
| Doação PIX com BR Code gerado no aparelho (CRC16/EMV) | ✅ |
| Conta Conexus **opcional**: backup manual na nuvem, denúncias identificadas, doação pela conta ([docs/ACCOUNT.md](docs/ACCOUNT.md)) | ✅ 0.3.0 (padrão Conexus na 0.4.0) |
| Simulador de chamadas (funciona no Expo Go, iOS e Android) | ✅ |
| Módulo nativo Kotlin de referência (CallScreeningService + RoleManager) | ✅ código de referência |
| Bloqueio real em device | 🔜 dev build (ver [Roadmap](docs/ROADMAP.md)) |

## 🚀 Rodando

```bash
npm install --legacy-peer-deps   # expo-router × expo-constants têm conflito de peer no SDK 58
npx expo start          # escaneie o QR com Expo Go (iOS/Android)
```

- **Expo Go**: tudo funciona em **modo demonstração** — use o **Simulador de chamadas** na aba Início para ver o pipeline completo (normalização → regras → base → score → decisão → histórico → denúncia).
- **Bloqueio real**: exige *development build* Android com o módulo nativo (`native/android/` — CallScreeningService não existe no Expo Go nem no iOS via Expo Go; no iOS o equivalente é a Call Directory Extension, planejada no M2 do roadmap).

```bash
# dev build (quando integrar o módulo nativo ao prebuild)
npx expo prebuild --platform android
npx expo run:android
```

## 👤 Conta Conexus (opcional)

O app funciona inteiro sem conta. Com ela: backup manual na nuvem, denúncias identificadas e doações pela conta
(detalhes em [docs/ACCOUNT.md](docs/ACCOUNT.md)). Login, sessão e o kit visual W10M vêm dos pacotes compartilhados
da Conexus, vendorizados em `vendor/conexus/`:

| Pacote | Uso aqui |
|---|---|
| `@conexus/account-expo` | login PKCE no navegador (bff-auth), sessão no `expo-secure-store`, refresh, `authorizedRequest`, `<ConexusLoginScreen/>` |
| `@conexus/metro-ui` | tema e componentes W10M (destaque verde `#60A917`) |

Não edite `vendor/conexus/**`: para atualizar, rode `bin/conexus-vendor <caminho deste repo>` na
`conexus-platform` e commite (o CI pode conferir com `--check`).

**Rodando local com login:** as variáveis são públicas (vão no bundle) e opcionais:

```bash
EXPO_PUBLIC_CONEXUS_CLIENT_ID=<client_id público do "AntiSpam BR" no bff-auth> \
EXPO_PUBLIC_CONEXUS_AUTH_URL=https://auth.conexus-systems.com.br \
EXPO_PUBLIC_CONEXUS_API_URL=https://api.conexus-systems.com.br \
npx expo start
```

Sem `EXPO_PUBLIC_CONEXUS_CLIENT_ID` o login fica desligado ("Em breve") e o app não chama a rede da Conta.
`EXPO_PUBLIC_CONEXUS_AUTH_URL` vazio = produção. Redirect registrado no bff-auth: `antispambr://oauth/callback`
(o Expo Go usa outro scheme; teste o login num build de desenvolvimento ou no APK). Excluir a conta:
<https://auth.conexus-systems.com.br/conta/excluir>. Roteiro de produção: [docs/MIGRACAO_CONTA_CONEXUS.md](docs/MIGRACAO_CONTA_CONEXUS.md).

## 🧪 Testes

```bash
npm test          # 42 testes do motor crítico
npm run typecheck # TypeScript estrito
```

## 🔐 Privacidade por design

- Processamento **100% local** por padrão; conta opcional, sem telemetria. A versão da loja exibe anúncios AdMob só dentro do app (nunca durante chamadas), com consentimento UMP — ver [docs/PRIVACY.md](docs/PRIVACY.md).
- Contatos/histórico **nunca** saem do aparelho.
- Rede só no que você escolhe: **IA Assistente** (sua chave OpenRouter; envia só o número que você pediu para analisar) e a **Conta Conexus** opcional (backup que você manda fazer, denúncias que você marca para enviar, doações) — ver [docs/ACCOUNT.md](docs/ACCOUNT.md).
- Base comunitária (futuro) usará **hash-prefix / k-anonymity** — ver `docs/PRIVACY.md` e `docs/THREAT_MODEL.md`.

## 🗂️ Estrutura

```
app/                  # Telas (expo-router): onboarding, (tabs), donate, account, login
src/core/             # Motor puro e testável
  phone/              #   normalização BR + emergências + regras 0303
  detection/          #   SpamScore + CampaignDetector
  decision/           #   SpamDecisionEngine (pipeline §4)
  rules/              #   motor de regras do usuário
  reputation/         #   reputação local com anti-abuse
  database/           #   banco de spam (formato aberto) + seed
  security/           #   SHA-256 (hash-prefix privacy)
  donation/           #   PIX BR Code (EMV + CRC16)
  ai/                 #   cliente OpenRouter (opcional, off por padrão)
src/app/              # Store global, screening service, backup, ponte nativa
src/account/          # Conta Conexus do app (APIs do AntiSpam/Payments, backup na nuvem) sobre @conexus/account-expo
src/ui/               # Tokens do app (score, ações) + peças próprias sobre @conexus/metro-ui
vendor/conexus/       # Pacotes compartilhados vendorizados (não editar)
native/android/       # Kotlin de referência (CallScreeningService)
docs/                 # RESEARCH, PRD, ARCHITECTURE, THREAT_MODEL, PRIVACY, ROADMAP…
__tests__/            # Testes do motor crítico (Jest)
```

## 🗺️ Próximos passos

Ver **[docs/ROADMAP.md](docs/ROADMAP.md)** — inclui a avaliação de viabilidade (técnica e legal) dos recursos avançados pedidos: SMS spam, "modo troll", gravação de áudio, geolocalização de originadores, cruzamento com dados públicos, integração OLX/Mercado Livre, e-mails/WhatsApp vinculados.

## 📄 Licença

MIT — veja [LICENSE](LICENSE). Contribua: [CONTRIBUTING.md](CONTRIBUTING.md) · Segurança: [SECURITY.md](SECURITY.md) · Doar: [DONATE.md](DONATE.md)
