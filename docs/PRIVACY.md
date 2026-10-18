# PRIVACY — AntiSpam BR

**Resumo em uma frase:** por padrão, nada sai do seu aparelho; com a Conta Conexus (opcional), só sai o que você mandar.

## O que o app processa

| Dado | Onde fica | Sai do aparelho? |
|---|---|---|
| Números das chamadas recebidas | Banco local + histórico | ❌ Nunca (por padrão) |
| Contatos (para "é contato?") | Resolvido localmente, sem envio | ❌ Nunca |
| Denúncias | Base local; futura contribuição comunitária é **opt-in por denúncia** | Só se você escolher |
| Histórico | Local, 500 registros, apagável | ❌ |
| Chave PIX de doação | Variável de build (EXPO_PUBLIC_DONATION_PIX_KEY) | Só para gerar o BR Code localmente |
| Chave OpenRouter (IA opcional) | Aparelho (AsyncStorage) | Só para openrouter.ai quando VOCÊ pede análise |
| Tokens da Conta Conexus (opcional) | Aparelho (expo-secure-store, cifrado pelo sistema) | Só para os serviços Conexus, para autenticar |
| Nome e e-mail da conta | Lidos do token, só para exibir | Ficam no Auth BFF (Conta Conexus) |
| Backup na nuvem (listas, regras, modo, ajustes) | API Conexus (`api.conexus-systems.com.br`) | Só quando você toca em "Fazer backup agora" |
| Denúncia identificada (número, categoria, data) | API Conexus | Só a denúncia em que você marca "Enviar com minha conta" |
| Doações pela conta (valor, mensagem opcional) | Payments Conexus | Só quando você doa pela conta |

## LGPD

- **Minimização**: só o necessário para a função; nada de identidade real.
- **Finalidade**: dados de chamada servem exclusivamente à proteção local.
- **Sem perfilamento**: nenhuma telemetria, nenhum tracker, nenhum SDK de anúncios.
- Direitos do titular: apagar histórico/base = privacidade restaurada instantânea no aparelho. Se você usou a conta,
  **Conta › Excluir meus dados na nuvem** apaga backup e denúncias do servidor (`DELETE /api/v1/antispam/me`), e
  cada denúncia pode ser excluída em **Conta › Minhas denúncias**. A Conta Conexus inteira se exclui em
  **Conta › Excluir a Conta Conexus** ou em <https://auth.conexus-systems.com.br/conta/excluir>.

## Conta Conexus (opcional)

- Desligada até você entrar. O app funciona inteiro sem conta e offline; nada é desbloqueado pela conta.
- Login no navegador (página da Conta Conexus, Google ou e-mail/senha). A senha nunca passa pelo app.
- Nunca vão para a nuvem: histórico de chamadas, contatos, base de reputação, chave da IA.
- Backup e restauração são sempre manuais. A denúncia anônima/local continua existindo; a identificada é escolha por denúncia.
- "Sair" apaga os tokens do aparelho e revoga a sessão. Detalhes em [ACCOUNT.md](ACCOUNT.md).

## IA Assistente (opcional)

- Desligada por padrão. Você fornece a chave do OpenRouter; o app envia **apenas o número que você pediu para analisar** e recebe uma opinião rotulada como tal.
- A IA **nunca** participa da decisão de bloqueio (latência, privacidade e auditabilidade).

## Base comunitária (futuro)

- Consultas por **prefixo de hash** (k-anonymity) — o servidor não aprende seus números.
- Contribuições agregadas e opt-in; denúncias nunca incluem seu nome/contato.

## Permissões Android

- `POST_NOTIFICATIONS` — avisar sobre chamadas bloqueadas.
- Papel de **filtragem de chamadas** via RoleManager (concedido por você nas configurações do sistema).
- Nada de acessibilidade, nada de SMS por padrão, nada de localização.
