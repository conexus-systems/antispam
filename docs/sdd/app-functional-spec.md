# Especificação funcional — AntiSpam BR (Android nativo Kotlin)

Versão: 0.1 (rascunho SDD). Data: 2026-10-28. Escopo: app em `native/android/`. Sem código nesta entrega.
Base: `docs/sdd/current-state.md`, `docs/sdd/admob-integration.md`, `docs/sdd/competitive-analysis.md` e leitura do código Kotlin (`decision/`, `screening/`, `database/`, `ui/`).

## 1. Estado real do app Kotlin (ponto de partida)

Existe e funciona em código: motor de decisão (`DecisionEngine`: OFF > emergência > contato > allowlist > blacklist > regra > 0303 > score > campanha > STIR; fail-safe para ALLOW), `CallerIdScreeningService` (só chamadas recebidas; BLOCK usa disallow+reject+skipCallLog+skipNotification), Room (`blocklist_entries`, `reputation_entries`, `history_entries`, `user_settings`, versão 1), WorkManager (seed/delta), telas Home, Histórico, Verificar, Ajustes, Onboarding.

Lacunas confirmadas: Histórico e Verificar com dados falsos; Ajustes sem persistência; `contactNumbers = emptySet()` e `rules = emptyList()` no serviço (TODOs); sem dataset seed; sem denúncia; sem backup; sem AdMob.

Achados ao ler o código e o manifesto, que viram requisitos:
- O serviço grava em `history` somente via `screeningCallback.publishDecision(number, action)`, que recebe só número e ação. O score, as razões e a explicação **se perdem**. Requisito RF-HIST-02.
- Em BLOCK o serviço usa `setSkipCallLog(true)`: a chamada bloqueada **some do registro do sistema**. Isso torna o histórico do app a única evidência. Requisito: toda decisão (inclusive ALLOW de números desconhecidos) é gravada.
- O manifesto pede permissões desnecessárias (seção 8).

## 2. Personas

| Persona | Contexto | Quer | Teme |
|---|---|---|---|
| **Marlene, 62 anos** | Recebe muitas ligações de cobrança e golpe; usa pouco o celular | Que o telefone pare de tocar com spam sem ter que configurar; ver claramente o que foi barrado | Perder ligação de filho ou médico |
| **Rafael, 29, autônomo** | Atende clientes desconhecidos | Modo equilibrado, lista permitida para clientes novos, desbloquear rápido | Bloquear cliente |
| **Camila, 35, atenta a privacidade** | Evita apps que leem agenda | Saber o que sai do aparelho; uso offline; zero conta obrigatória | App que vende dados |
| **Revisor da Play** | Avalia política e funcionamento | Permissões justificadas, funcionalidade central visível, sem anúncio abusivo, privacidade coerente com Data safety | Permissão sem uso, tela falsa, anúncio na chamada |

## 3. Princípios do produto

1. **Local primeiro.** Decisão de chamada sempre local; a rede nunca decide e nunca atrasa a chamada.
2. **Nunca atrapalhar o essencial.** Emergência nunca bloqueada; na dúvida, permitir.
3. **Explicar sempre.** Toda decisão mostra os motivos que o motor gerou.
4. **Pouca permissão.** Só o necessário, pedida no momento do uso.
5. **Reversível.** Todo bloqueio se desfaz em poucos toques.

## 4. Histórias de usuário (MoSCoW)

Convenção: RF-<área>-<n>. M = Must (MVP Play), S = Should (primeira atualização), C = Could, W = Won't (agora).
Critérios em Gherkin. Cenários numerados para o plano de testes (seção 9).

### 4.1 Must

**RF-PROT-01 (M) Ativar a proteção (papel de triagem)**
Como usuário, quero ativar o AntiSpam BR como app de triagem de chamadas para que ele analise chamadas recebidas.
```
Cenário: Conceder o papel pela primeira vez
  Dado que o onboarding explica o que o app faz e o que não faz (não lê agenda, não grava)
  Quando eu toco em "Ativar proteção" e aceito o diálogo do sistema do papel de triagem
  Então a tela inicial mostra "Proteção ativa"
  E o modo padrão é "Equilibrado"

Cenário: Recusar o papel
  Dado que estou no onboarding
  Quando eu recuso o diálogo do sistema
  Então o app continua utilizável
  E a tela inicial mostra "Proteção inativa" com o botão "Ativar proteção"
  E a aba Verificar continua funcionando

Cenário: Perder o papel depois
  Dado que a proteção estava ativa
  Quando eu troco o app de triagem nas configurações do Android
  Então ao abrir o app vejo "Proteção inativa" sem erro
```

**RF-PROT-02 (M) Emergência nunca bloqueada**
```
Cenário: Número de emergência em qualquer modo
  Dado que o modo é "Agressivo"
  E existe a regra "Bloquear tudo que não é contato"
  Quando uma chamada chega de um número de emergência (190, 192, 193, 112 etc. conforme PhoneNormalizer)
  Então a chamada é permitida
  E o histórico registra a razão "Número de emergência"

Cenário: Emergência na lista bloqueada
  Dado que o usuário adicionou 190 à lista bloqueada
  Quando a chamada chega
  Então a chamada é permitida (emergência vence lista)
```

**RF-PROT-03 (M) Identificar e bloquear chamada de spam conhecido**
```
Cenário: Número do dataset com reputação alta
  Dado o modo "Equilibrado" e um número com score de reputação acima do limiar de bloqueio no dataset local
  Quando a chamada chega de um número fora da agenda
  Então a chamada é rejeitada sem tocar
  E o histórico registra BLOQUEADA, a pontuação e a explicação
  E uma notificação silenciosa informa "Chamada bloqueada" (se notificações ligadas)

Cenário: Modo "Alertar"/Básico
  Dado o modo "Básico" e um número de score intermediário
  Quando a chamada chega
  Então o telefone toca normalmente
  E o histórico marca ALERTA

Cenário: Silenciar
  Dado que a pontuação está na faixa de silenciar
  Quando a chamada chega
  Então a chamada é aceita pelo sistema mas o toque é silenciado
  E o histórico marca SILENCIADA
```
Nota técnica: `CallResponse.setSilenceCall(true)` hoje **não é usado** pelo serviço (só `disallow`). Requisito de implementação RF-PROT-03a: mapear SILENCE para `setSilenceCall`.

**RF-PROT-04 (M) Número desconhecido sem sinais**
```
Cenário: Desconhecido limpo no modo Equilibrado
  Dado um número fora da agenda, sem reputação e sem regra
  Quando a chamada chega
  Então a chamada toca normalmente
  E o histórico registra PERMITIDA com a explicação "Nenhum sinal de risco encontrado"
```

**RF-PROT-05 (M) Agenda como permitida**
```
Cenário: Contato salvo
  Dado um número que está na agenda do aparelho
  Quando a chamada chega
  Então a chamada toca normalmente
  E nenhuma regra do usuário ou do modo Agressivo a bloqueia

Cenário: Regra por prefixo que cobre um contato
  Dado a regra "Bloquear prefixo +5511 9000"
  E um contato com número nesse prefixo
  Quando a chamada chega
  Então a chamada toca (agenda vence regra)
```
Nota: a documentação do Android informa que o serviço de triagem é chamado para números fora da lista de contatos; **a verificar em aparelho** (teste T5). Com isso, o MVP não precisa de `READ_CONTACTS` para esta história. O engine já trata `CONTACT` caso o contexto traga o nome.

**RF-LIST-01 (M) Lista bloqueada**
```
Cenário: Bloquear um número manualmente
  Dado que estou em Ajustes > Números bloqueados
  Quando eu digito +55 11 91234-5678 e confirmo
  Então o número é normalizado (E.164) e aparece na lista
  E a próxima chamada desse número é rejeitada

Cenário: Número inválido
  Quando eu digito "abc"
  Então vejo "Número inválido" e nada é salvo

Cenário: Duplicado
  Dado que o número já está bloqueado
  Quando eu tento adicionar de novo
  Então vejo "Já está na lista" e a lista não muda
```

**RF-LIST-02 (M) Lista permitida**
```
Cenário: Permitir um número
  Dado que o número está na lista bloqueada
  Quando eu o adiciono à lista permitida
  Então ele sai da lista bloqueada e entra na permitida (mutuamente exclusivas)
  E a próxima chamada toca

Cenário: Permitida vence reputação
  Dado um número com reputação alta de spam no dataset
  E o número está na lista permitida
  Quando a chamada chega
  Então toca e o histórico explica "Número na sua lista de permitidos"
```

**RF-LIST-03 (M) Desbloquear**
```
Cenário: Desbloquear pelo histórico
  Dado uma entrada BLOQUEADA no Histórico
  Quando eu abro a entrada e toco em "Desbloquear / Não é spam"
  Então o número vai para a lista permitida
  E o histórico passa a mostrar a ação disponível "Bloquear novamente"
  E a próxima chamada toca

Cenário: Desfazer
  Quando eu bloqueio um número e vejo a barra de aviso
  Então posso tocar em "Desfazer" por alguns segundos
```

**RF-REG-01 (M) Regras por prefixo e DDD**
```
Cenário: Criar regra por prefixo
  Dado Ajustes > Regras
  Quando eu crio "Bloquear prefixo +55 11 4000" com ação Bloquear
  Então a regra aparece ativa na lista
  E chamadas de +55114000xxxx são rejeitadas, com a regra citada na explicação

Cenário: Regra por DDD
  Quando eu crio "Silenciar DDD 21"
  Então chamadas de números +5521... fora da agenda são silenciadas

Cenário: Desativar sem apagar
  Quando eu desligo a chave da regra
  Então a regra permanece na lista, mas não atua

Cenário: Ordem
  Dado duas regras que casam com o mesmo número
  Então vale a primeira por ordem de prioridade (como no motor)
```

**RF-REG-02 (M) Ocultos, internacionais e 0303**
```
Cenário: Bloquear números ocultos
  Dado a regra "Ocultos" com ação Bloquear
  Quando chega uma chamada com identificação restrita
  Então a chamada é rejeitada

Cenário: 0303 configurável
  Dado a opção de 0303 = "Silenciar"
  Quando chega chamada 0303
  Então ela é silenciada e a explicação cita "rota de telemarketing"
```

**RF-DENUN-01 (M) Denunciar como spam**
```
Cenário: Denunciar pelo histórico
  Dado uma chamada PERMITIDA de número desconhecido no Histórico
  Quando eu toco em "Denunciar como spam" e escolho a categoria (Telemarketing, Golpe, Cobrança, Outro)
  Então o número entra na minha lista bloqueada
  E a reputação local aumenta (reports +1)
  E vejo a confirmação "Denúncia registrada neste aparelho"

Cenário: Sem rede
  Dado o aparelho em modo avião
  Quando eu denuncio
  Então a denúncia é salva localmente sem erro

Cenário: Envio opcional (Should)
  Dado que o usuário ativou "Compartilhar denúncias anonimamente"
  Quando uma denúncia é feita
  Então somente o número denunciado e a categoria são enviados (nunca agenda nem histórico)
```
Padrão do MVP: **denúncia apenas local**, sem upload (evita Data safety adicional). Envio fica em Should.

**RF-HIST-01 (M) Histórico real**
```
Cenário: Registrar cada decisão
  Dado uma chamada recebida de qualquer número fora da agenda
  Quando o serviço decide
  Então uma linha é gravada em history_entries com número, hora, ação, score e motivos

Cenário: Lista vazia
  Dado que ainda não há decisões
  Quando abro o Histórico
  Então vejo estado vazio explicando o que aparece ali (sem dados de exemplo)

Cenário: Filtro
  Quando escolho o filtro "Bloqueadas"
  Então só aparecem entradas BLOQUEADAS

Cenário: Apagar
  Quando toco em "Apagar histórico" e confirmo
  Então todas as entradas são removidas
```

**RF-HIST-02 (M) Explicação da decisão**
```
Cenário: Ver o porquê
  Dado uma entrada do histórico
  Quando eu a abro
  Então vejo ação, score, confiança e a lista de motivos legíveis, por exemplo
    "Reputação local (+X)", "Regra \"Prefixo 4000\" aplicada", "Número na sua lista de bloqueados"
```

**RF-VER-01 (M) Verificar número manualmente**
```
Cenário: Verificar com o motor real
  Quando eu digito um número e toco em "Verificar"
  Então o resultado vem do DecisionEngine com os dados reais (listas, regras, reputação)
  E a verificação NÃO cria entrada de bloqueio nem aparece como chamada real (marcada simulated=true ou separada)

Cenário: Ação após o resultado
  Dado o resultado "Provável spam"
  Então vejo os botões "Bloquear número" e "Denunciar"
```

**RF-AJ-01 (M) Ajustes persistentes**
```
Cenário: Alterar o modo
  Quando eu escolho "Agressivo"
  E fecho e reabro o app
  Então "Agressivo" continua selecionado
  E a próxima chamada usa os limiares do modo Agressivo

Cenário: Opção 0303 e notificações
  Quando eu altero qualquer opção
  Então o valor é gravado em user_settings imediatamente
```

**RF-OFF-01 (M) Modo offline total**
```
Cenário: Sem rede
  Dado o aparelho em modo avião com dataset instalado
  Quando uma chamada de número do dataset chega
  Então a decisão é a mesma da condição com rede
  E nenhuma requisição de rede é feita no caminho da chamada

Cenário: Primeiro uso sem rede
  Dado a instalação sem conexão
  Então o app usa o dataset seed empacotado no APK
  E mostra "Base local: versão X, de <data>"
```

**RF-PRIV-01 (M) Transparência de privacidade**
```
Cenário: Tela de privacidade
  Quando abro Ajustes > Privacidade
  Então vejo, em linguagem simples: o que fica no aparelho, o que nunca é lido (agenda, no MVP), se algo é enviado, link da política pública e botão "Apagar todos os meus dados"
```

**RF-ADS-01 (M se AdMob ativo) Anúncios não intrusivos** — ver seção 7.

### 4.2 Should (primeira atualização depois do MVP)

| ID | História | Critério principal |
|---|---|---|
| RF-CONT-01 | Mostrar o nome do contato no histórico e permitir regra "só contatos tocam" | `READ_CONTACTS` pedida só ao ligar esse recurso, com explicação antes; leitura local; recusar não quebra nada |
| RF-DENUN-02 | Compartilhar denúncias anonimamente (opt-in) | Padrão desligado; envia só número e categoria; Data safety atualizada antes |
| RF-BKP-01 | Exportar e importar listas e regras (arquivo JSON via seletor de arquivos do sistema) | Sem permissão de armazenamento; arquivo contém só listas e regras, nunca histórico por padrão |
| RF-REG-03 | Regra por horário e por regex | Já no motor (`BySchedule`, `ByRegex`); precisa de UI |
| RF-DATA-01 | Atualizar dataset por Wi-Fi com assinatura verificada | Falha de assinatura mantém a versão anterior e avisa |
| RF-HIST-03 | Retenção configurável (30/90/365 dias) | Limpeza automática por WorkManager |
| RF-NOTIF-01 | Notificação resumo diário | Sem anúncio na notificação |

### 4.3 Could
- Identificação de empresas (nomes comerciais) a partir de dataset público, se licença permitir.
- Notas pessoais por número.
- Widget de estado da proteção.

### 4.4 Won't (agora)
- Gravação de chamadas (inválido na política da Play para Call Log, e risco legal).
- Bloqueio de SMS (exige papel de SMS padrão).
- Triagem por IA / transcrição.
- Conta obrigatória, login, assinatura paga de segurança.
- Ler registro de chamadas do sistema (`READ_CALL_LOG`).

## 5. Requisitos não funcionais

| ID | Requisito |
|---|---|
| RNF-01 | Decisão p95 < 100 ms no caminho local (já é meta do serviço); resposta ao sistema em menos de 5 s sempre |
| RNF-02 | Qualquer exceção resulta em ALLOW e é registrada no log local (fail-safe) |
| RNF-03 | Nenhuma chamada de rede a partir do serviço de triagem |
| RNF-04 | `minSdk 29`; em API < 29 o serviço permite tudo (como hoje); verificação STIR só em API 30+ |
| RNF-05 | Acessibilidade: alvos 48dp, contraste AA, TalkBack nas ações de bloquear e desbloquear |
| RNF-06 | pt-BR como idioma principal; strings em recursos |
| RNF-07 | Banco com migrações Room versionadas (sem `fallbackToDestructiveMigration` em produção) |
| RNF-08 | Backup automático do Android desligado por padrão (`allowBackup=false`, como hoje), para que histórico não vá à nuvem sem aviso |

## 6. Modelo de dados (Room)

Estado atual: 4 tabelas, versão 1. Proposta (MVP) abaixo; mudanças marcadas. Todas as datas em ISO 8601 UTC como hoje.

| Tabela | Colunas principais | Mudança |
|---|---|---|
| `user_settings` | id=1, mode, blockThreshold, silenceThreshold, warnThreshold, handle0303, blockUnknownInAggressive, theme, accent, reduceMotion, notificationsEnabled, onboardingDone, datasetVersion | Acrescentar `blockHidden`, `blockInternational` (ou usar regras), `historyRetentionDays` (default 90), `consentShareReports` (default false) |
| `blocklist_entries` | number (PK, E.164), action (`block`/`allow`), source (`local`/`report`/`remote`/`rule`), createdAt | Acrescentar `note` opcional. Lista bloqueada e permitida compartilham a tabela; PK única garante exclusividade |
| `rules` (**nova**) | id (PK), name, enabled, kind (`PREFIX`/`DDD`/`HIDDEN`/`INTERNATIONAL`/`NOT_CONTACT`/`SCHEDULE`/`REGEX`), value, action (`BLOCK`/`SILENCE`/`WARN`/`ALLOW`), priority (int), createdAt | Mapeia 1:1 para `Rule`/`RuleMatch` do motor. Hoje o serviço passa `rules = emptyList()` |
| `history_entries` | id, number, display, name, at, action, score, reasons (JSON), category, simulated | Acrescentar `confidence`, `explanation` (JSON de textos), `reported` (bool). Hoje só número e ação chegam do serviço |
| `reputation_entries` | key (E.164), country, category, score, reports, uniqueReporters, firstSeen, lastSeen, confidence, falsePositives, reporterIds | Sem mudança estrutural. Atenção: denúncia local incrementa `reports`, mas `uniqueReporters` local seria sempre 1 e o motor exige peso mínimo 2 (`MIN_WEIGHTED_REPORTERS`). Decidir: denúncia do próprio usuário vai para a lista bloqueada (efeito imediato) e **não** depende do peso do dataset |
| `dataset_meta` (**nova**) | id=1, version, publishedAt, signatureOk, source (`seed`/`delta`), entries, installedAt | Para mostrar "Base local: versão X" e auditar assinatura. Hoje só `datasetVersion` em settings |

Índices: `history_entries(at DESC)`, `history_entries(number)`, `rules(priority)`.
Sem tabela de contatos no MVP (agenda não é lida).

## 7. AdMob não intrusivo (regras obrigatórias)

Complementa `docs/sdd/admob-integration.md`. Esta seção vale para o app Kotlin (a integração atual descrita é a do Expo).

| Regra | Detalhe |
|---|---|
| A1 | **Nunca** anúncio no fluxo de chamada, no `CallerIdScreeningService`, em notificações, overlays, widgets ou tela de chamada recebida |
| A2 | Banner somente em **Histórico** (rodapé, fora da área de toque da lista) e **Ajustes**. Nunca em Home, Onboarding, detalhe de decisão, Verificar |
| A3 | Intersticial somente na aba **Verificar**, **depois** que a ação do usuário terminou e o resultado já foi exibido; nunca ao abrir o app, nunca ao sair, nunca durante bloqueio ou desbloqueio |
| A4 | Frequência do intersticial: no máximo 1 a cada 3 verificações, intervalo mínimo de 3 min, máximo 3 por hora, nunca na primeira verificação da sessão (valores já usados no Expo) |
| A5 | Consentimento **UMP** antes de qualquer request de anúncio; o SDK de anúncios só inicializa depois de `canRequestAds()`. Recusa ou indisponível: nenhum anúncio e o app segue normal |
| A6 | Acesso às "Opções de privacidade" (formulário UMP) em Ajustes, quando exigido |
| A7 | IDs de teste em debug e preview; IDs reais só no build de produção; nunca clicar em anúncio real |
| A8 | Anúncios nunca imitam botões do app ou alertas do sistema; rotulados como anúncio pelo SDK |
| A9 | Se o público-alvo ou a classificação exigirem tratamento específico, o proprietário decide antes da publicação com anúncios ativos |
| A10 | Falha de carregamento de anúncio nunca bloqueia nem atrasa tela; o espaço colapsa |

Critérios de aceite:
```
Cenário: Sem consentimento
  Dado que o usuário recusou o consentimento (ou o formulário ainda não foi respondido)
  Quando abro Histórico ou Ajustes
  Então não há requisição de anúncio e o layout não deixa espaço vazio

Cenário: Chamada recebida
  Dado qualquer estado de consentimento
  Quando uma chamada chega e o serviço decide
  Então nenhum SDK de anúncio é invocado nesse caminho

Cenário: Intersticial raro
  Dado que fiz 3 verificações seguidas dentro de 3 minutos
  Então no máximo um intersticial foi exibido, após o resultado, e nunca na primeira verificação
```
Estado de verificação: o AdMob do app Kotlin ainda **não foi implementado**; IDs de produção e conta AdMob **não foram verificados**.

## 8. Matriz de permissões

Hoje o manifesto declara 13 permissões. Proposta para o MVP:

| Permissão | Hoje | MVP | Justificativa / ação |
|---|---|---|---|
| `BIND_SCREENING_SERVICE` | `uses-permission` | **Remover como uses-permission**; manter só em `android:permission` do `<service>` | É protegida por assinatura de sistema; declarar como uses-permission é incorreto |
| Papel `ROLE_CALL_SCREENING` | via RoleManager (intent) | Manter | Funcionalidade central: analisar chamadas recebidas |
| `READ_PHONE_STATE` | sim | Remover, se nenhum código a usar | Triagem não a exige; confirmar uso antes de remover |
| `READ_CALL_LOG` | sim | **Remover** | Política da Play restringe; `CallScreeningService` dispensa (competitive-analysis 4.1) |
| `ANSWER_PHONE_CALLS` | sim | **Remover** | Bloqueio é feito pela resposta do serviço de triagem |
| `MANAGE_OWN_CALLS` | sim | **Remover** | É para apps VoIP/Self-managed |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL` e `foregroundServiceType=phoneCall` | sim | **Remover** | O serviço de triagem é vinculado pelo sistema; sem foreground service |
| `POST_NOTIFICATIONS` | sim | Manter, pedida em contexto | Aviso "chamada bloqueada"; recusar não afeta bloqueio |
| `INTERNET`, `ACCESS_NETWORK_STATE` | sim | Manter | Atualização de dataset e anúncios; nunca no caminho da chamada |
| `WAKE_LOCK` | sim | Remover se só o WorkManager usa (ele declara a sua) | |
| `RECEIVE_BOOT_COMPLETED` | sim | Manter só se `BootReceiver` reagenda sync; WorkManager já persiste | Avaliar |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | sim | **Remover** | Permissão de uso restrito na Play; desnecessária |
| `READ_CONTACTS` | não | **Não incluir no MVP** | Sistema não envia contatos ao serviço de triagem; entra em RF-CONT-01 com divulgação em destaque |

Resultado: nenhuma permissão restrita de Call Log/SMS; **sem Permissions Declaration Form obrigatório**.

### 8.1 Texto-base para a declaração da Play (caso o proprietário opte por manter READ_CALL_LOG ou queira registrar a justificativa)

Rascunho, a revisar pelo proprietário. Não recomendado, pois o app tem método alternativo:

> O AntiSpam BR é um app de identificação e bloqueio de chamadas indesejadas. A funcionalidade central é analisar, no próprio aparelho, chamadas recebidas de números fora da agenda por meio do papel de triagem de chamadas do Android (CallScreeningService), e rejeitar ou silenciar as que o usuário configurou como indesejadas. O app não usa o registro de chamadas do sistema: o histórico mostrado é criado pelo próprio app a partir das decisões que ele toma. O app não é o aplicativo de telefone padrão, não lê SMS, não grava chamadas e não envia agenda nem registro de chamadas a servidores.

### 8.2 Divulgação em destaque (prominent disclosure) para o onboarding

> "Para bloquear chamadas indesejadas, o AntiSpam BR precisa ser definido como seu app de triagem de chamadas. Ele analisa, somente no seu aparelho, o número de quem liga quando esse número não está na sua agenda. Não lemos sua agenda, não gravamos chamadas e não enviamos seus números para servidores. Você pode desativar a qualquer momento nas configurações do Android."

## 9. Plano de testes em aparelho/emulador

Pré-condições: build debug com IDs de teste de anúncio; aparelho físico Android 10+ (idealmente 12+) com chip ou, no emulador, `adb emu gsm call <numero>`. Dataset seed instalado com ao menos 3 números sintéticos de spam (ex.: faixa reservada de teste). Papel de triagem concedido.

| Id | Cenário | Passos | Resultado esperado | Evidência |
|---|---|---|---|---|
| T1 | Chamada de spam conhecido | Inserir número sintético no dataset (score alto); simular chamada | Rejeitada sem tocar; histórico BLOQUEADA com explicação; notificação silenciosa | Captura do Histórico, `adb logcat` do serviço |
| T2 | Emergência | Simular chamada de 190 com modo Agressivo e 190 na lista bloqueada | Permitida; razão "Número de emergência" | Captura |
| T3 | Desconhecido limpo | Número aleatório sem reputação | Toca; histórico PERMITIDA "Nenhum sinal" | Captura |
| T4 | Silenciar | Número com score na faixa de silenciar | Chamada aceita pelo sistema mas sem toque; histórico SILENCIADA | Observação em aparelho |
| T5 | Número da agenda | Salvar número como contato; simular chamada com regra de prefixo que o cobre | Toca; (verificar se o serviço sequer é invocado para contatos) | `logcat` |
| T6 | Denúncia | Chamada desconhecida PERMITIDA; denunciar no Histórico como "Golpe"; nova chamada do mesmo número | Número vai à lista bloqueada; segunda chamada rejeitada; aviso "salva neste aparelho" | Captura + logcat |
| T7 | Desbloqueio | Abrir entrada BLOQUEADA; "Desbloquear"; nova chamada | Número em permitidos; toca | Captura |
| T8 | Regra por prefixo | Criar regra de prefixo; chamar número com e sem o prefixo | Só o com prefixo é afetado; regra citada na explicação | Captura |
| T9 | Ocultos | Regra de ocultos; simular chamada restrita | Rejeitada | logcat |
| T10 | Offline total | Modo avião; repetir T1 | Mesmo resultado; nenhuma requisição de rede (verificar com `adb shell dumpsys netstats` ou Network Profiler) | Log de rede vazio no caminho da chamada |
| T11 | Persistência de ajustes | Mudar modo e 0303; matar o app; reabrir; chamada de teste | Valores mantidos; decisão usa os novos | Captura |
| T12 | Papel recusado/perdido | Recusar no onboarding; depois trocar o app de triagem nas configurações | App abre; "Proteção inativa"; Verificar funciona | Captura |
| T13 | Fail-safe | Forçar exceção (banco corrompido em build de teste) | Chamada permitida; sem travar | logcat |
| T14 | Tempo de resposta | Medir 20 chamadas | p95 < 100 ms (log do serviço), nunca perto de 5 s | Relatório de tempos |
| T15 | Anúncios sem consentimento | Instalar limpo; recusar UMP; abrir Histórico e Ajustes | Nenhuma requisição de anúncio | Network log |
| T16 | Anúncios com consentimento (IDs de teste) | Aceitar UMP; abrir Histórico, Ajustes, Verificar ×3 | Banner nas duas telas; no máx. 1 intersticial, após resultado | Captura |
| T17 | Chamada com anúncio ativo | Com consentimento aceito, simular T1 | Nenhum SDK de anúncio no caminho; sem overlay | logcat |
| T18 | Histórico real | Instalar limpo; abrir Histórico | Estado vazio, sem dados de exemplo; após T1–T3, 3 linhas | Captura |
| T19 | Apagar dados | "Apagar todos os meus dados" | Banco vazio; onboarding volta ou estado inicial | Captura |
| T20 | Upgrade de banco | Instalar versão antiga, atualizar | Migração sem perda das listas | Teste de migração Room |

Também executar: `./gradlew` de testes unitários do `DecisionEngine` e de migração, mas **esta especificação não roda gradle**. Paridade com o core Rust: reaproveitar vetores de teste do `antispam-core` onde existirem.

Critérios de saída para submeter à Play: T1–T14 e T18–T19 passando em ao menos um aparelho físico e um emulador; T15–T17 só se AdMob estiver ativo no build.

## 10. Corte de MVP

### Entra na primeira versão da Play
1. Onboarding com divulgação em destaque e pedido do papel de triagem (RF-PROT-01).
2. Motor de decisão ligado ao serviço com SILENCE via `setSilenceCall` (RF-PROT-03).
3. Emergência nunca bloqueada (RF-PROT-02).
4. Listas bloqueada e permitida editáveis (RF-LIST-01/02/03).
5. Regras por prefixo, DDD, ocultos, internacionais e 0303 (RF-REG-01/02).
6. Denúncia local com categoria (RF-DENUN-01).
7. Histórico real com explicação e ações (RF-HIST-01/02).
8. Verificar com motor real (RF-VER-01) e Ajustes persistentes (RF-AJ-01).
9. Dataset seed empacotado, funcionamento 100% offline (RF-OFF-01).
10. Tela de privacidade e apagar dados (RF-PRIV-01).
11. Manifesto reduzido, sem READ_CALL_LOG nem READ_CONTACTS (seção 8).
12. AdMob (A1 a A10) **somente se** o proprietário já tiver conta, IDs reais e declaração de anúncios; senão a primeira versão sai **sem anúncios**.

### Fica para depois
Nome do contato e `READ_CONTACTS` (RF-CONT-01), denúncia compartilhada (RF-DENUN-02), backup (RF-BKP-01), regras por horário/regex na UI, atualização delta assinada via rede, retenção configurável, notificação diária, nomes comerciais, notas, widget, IA, SMS, gravação.

## 11. Decisões em aberto (para o proprietário)

1. Publicar a v1 com ou sem anúncios.
2. Denúncia compartilhada é desejada? Se sim, exige servidor, política, Data safety e moderação (não existe hoje).
3. Fonte e licença do dataset seed brasileiro (`docs/BRAZIL_DATA_SOURCES.md`); sem dataset, a v1 funciona só com listas, regras e padrões, e deve dizer isso na ficha.
4. URL pública da política de privacidade.
5. Confirmar em aparelho se o serviço é invocado para números da agenda (T5); se for, ligar `READ_CONTACTS` volta ao escopo.
