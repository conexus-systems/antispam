# @conexus/account-expo

Conta Conexus (bff-auth) nos apps Expo: cliente OAuth público com PKCE S256 no navegador do sistema, tokens no
expo-secure-store, refresh 60 s antes de expirar, revoke ao sair, `authedFetch` com refresh-on-401, erros tipados
em pt-BR e a tela `<ConexusLoginScreen/>` (W10M, via `@conexus/metro-ui`). Fonte da verdade:
`conexus-platform/js/account-expo`; vendorize com `bin/conexus-vendor`. Guia completo: `docs/js.md`.

Peer deps: `@conexus/metro-ui`, `expo-web-browser`, `expo-secure-store`, `expo-crypto`, `react`, `react-native`.
