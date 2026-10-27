# Migração para Rust (Slint)

Decisão do Tiago: reescrever o app inteiro em Rust e abandonar o Expo. Toolkit de UI: **Slint**.

## Estado

- `rust/antispam-app`: Slint com as quatro abas ligadas ao `antispam-core`:
  - **Verificar**: normaliza o número e roda o `DecisionEngine` (sem agenda nem listas ainda).
  - **Histórico**: verificações feitas na sessão (sem persistência ainda).
  - **Ajustes**: seletor de modo (Desligado, Básico, Equilibrado, Agressivo).
  - **Início**: mostra o modo atual.
  Testes: `cargo test -p antispam-app` (3 passando). Ainda não empacotada para Android.
- `rust/antispam-core`: já cobre normalização, reputação, decisão, dataset e assinatura. Não é reescrito.
- Expo (`app/`, `src/`, `vendor/`) segue como está até paridade. Removê-lo é destrutivo e exige aprovação.

## Fica em Kotlin (não migra)

- `CallScreeningService` e o papel de triagem de chamadas. O Android exige a classe de serviço; Rust entra por JNI, como em `rust/antispam-android`.
- Pacote `native/android/` (serviço, DAO e notificações).

## Paridade pendente (por tela)

| Tela TS | Arquivo atual | Equivalente Slint |
|---|---|---|
| Início | `app/(tabs)/index.tsx` | aba Início |
| Histórico | `app/(tabs)/history.tsx` | aba Histórico |
| Verificar | `app/(tabs)/verify.tsx` | aba Verificar |
| Ajustes | `app/(tabs)/settings.tsx` | aba Ajustes |
| Onboarding | `app/onboarding.tsx` | a implementar |
| Verificação com agenda/listas | `src/core/*` | parcial: sem contatos, allowlist ou reputação local |
| Conta | `app/account.tsx`, `vendor/conexus/account-expo` | a decidir (login e BFF) |
| Doação | `app/donate.tsx` | a implementar |

## Pendências técnicas

1. Empacotar o `antispam-app` para Android (`cargo-ndk` + backend Android do Slint).
2. Ligar o `CallScreeningService` Kotlin ao core Rust via JNI.
3. AdMob: não há SDK oficial em Rust. Usar o SDK Kotlin pelo mesmo bridge.
4. Substituir a lógica TS (`src/core/`) pelo core Rust nas telas.
5. Decidir o destino de `vendor/conexus/*` (metro-ui e account-expo) e de `packages/*`.
