# Análise competitiva — AntiSpam BR (Android nativo Kotlin)

Data da pesquisa: 2026-10-28. Autor: análise de produto (SDD). Sem alteração de código.

## 0. Como ler este documento e limites da pesquisa

- Fontes oficiais foram preferidas. Várias páginas da Play Store não retornaram conteúdo utilizável (truncadas ou 404 na ferramenta de busca), então parte das features de Whoscall, CallApp, Mr. Number, Hiya e Should I Answer? vem de **listagens secundárias** (App Store, APKMirror, agregadores) ou de FAQ do fornecedor. Cada afirmação traz o nível de confiança.
- Níveis: **[O]** fonte oficial do fornecedor/Google lida nesta pesquisa; **[S]** fonte secundária (listagem em loja espelhada, agregador, review); **[NC]** não confirmado.
- Não há números de usuários nem preços confirmados em fonte oficial. Preços citados abaixo são de terceiros, datados, e **não devem ser usados** como referência de produto sem checar no app.
- Tudo foi resumido com palavras próprias; citações são curtas e indicadas.

## 1. Os seis concorrentes em uma linha cada

| App | Posicionamento | Modelo de dados | Monetização |
|---|---|---|---|
| Whoscall (Gogolook) | Identificador com base comunitária grande e **base offline** para países selecionados, incluindo Brasil [S] | Usa permissões de telefone, registro de chamadas e contatos para identificar e bloquear; política própria linkada na loja [S]. Conteúdo da política **não lido** [NC] | Freemium: Premium com atualização automática da base offline, bloqueio automático e assistente de SMS [S]; um review de terceiro (Guru99) cita US$ 1,65–1,99/mês, **sem data e não confirmado em fonte oficial** |
| Telefone do Google | Spam e Caller ID nativos no discador do Android/Pixel | Google diz que **não coleta números da sua agenda**, só o número remoto de quem não é contato; não compartilha com parceiros de negócio; dá para desativar a qualquer momento [O] | Sem cobrança direta; recurso do sistema |
| CallApp | Identificador antes de atender, com foto/nome/categoria, bloqueio, toque por contato, identificação em mensageiros [O parcial] | Política **não lida** [NC] | Descrito como gratuito no site [O]; anúncios/assinatura **não confirmados** [NC] |
| Mr. Number (Hiya) | Bloqueio por número, prefixo/código de área, país, categorias (spam, fraude, ocultos) [S] | FAQ oficial existe, mas retornou 403 na leitura [NC]; política da Hiya vale como proxy (ver Hiya) | Gratuito com anúncios e limite de consultas gratuitas (20 números) segundo fonte secundária antiga [S] |
| Hiya | Detecção em tempo real, foco em fraude; recursos de IA e triagem restritos a EUA e Canadá [S] | Política oficial: coleta registros de chamadas e mensagens, contatos (se o usuário permitir), IP, país e **dados de terceiros que ligam**, mesmo não usuários; compartilha com afiliadas, clientes empresariais, prestadores e, de forma agregada/desidentificada, para marketing/pesquisa; retenção sem prazo definido [O] | Freemium; Premium (triagem, proteção de voicemail e número falsificado) [S]; preço **não confirmado** |
| Should I Answer? | Privacidade e controle: alerta ou bloqueio configurável, listas próprias, ocultos/estrangeiros/tarifados, funciona offline, discador próprio [S] | Notas de usuários alimentam base moderada; política **não lida** [NC] | Não confirmado [NC] |

## 2. Matriz de features (app x feature)

Legenda: S = sim confirmado; s = sim em fonte secundária; N = não encontrado/aparentemente não; ? = não confirmado. A última coluna é o estado do AntiSpam BR Kotlin hoje (código lido nesta tarefa).

| Feature | Whoscall | Tel. Google | CallApp | Mr. Number | Hiya | Should I Answer? | AntiSpam BR hoje |
|---|---|---|---|---|---|---|---|
| Identificar quem liga (nome/categoria) | s | S (empresas) | S | s | s | s | Parcial: só reputação local; sem dataset |
| Base consultável offline | s (Brasil incluído) | ? | ? | ? | ? | s | Motor offline sim; **sem dataset seed** |
| Bloqueio automático de spam | s (Premium) | S (filtro opcional) | S | s | s | s | Sim no motor; serviço existe |
| Lista bloqueada própria | s | S | S | s | s | s | Tabela existe; UI falsa |
| Lista permitida própria | ? | ? | ? | ? | ? | s | Tabela existe; UI falsa |
| Bloqueio por prefixo / código de área / DDD | ? | N | ? | s | ? | ? | Motor sim (`ByPrefix`, `ByDdd`); sem tabela nem UI |
| Bloquear ocultos / internacionais | ? | ? | ? | s (instável) | ? | s | Motor sim (`Hidden`, `International`) |
| Agenda tratada como permitida | ? | S | ? | N (bloqueia mesmo contatos em prefixo, segundo fonte antiga) | ? | s (secundária) | Não ligado |
| Denunciar como spam | s | S | S | s | s | s | **Ausente** |
| Corrigir falso positivo | ? | S | ? | ? | ? | s | Parcial (campo `falsePositives`) |
| Explicar a decisão | N | N | N | N | N | N | **Sim (diferencial)**: `explanation` por decisão |
| Gravação de chamadas | ? | N | s | N | N | N | Fora de escopo |
| Anotações sobre o número | ? | N | s (listagem) | ? | ? | ? | Fora do MVP |
| Bloqueio de SMS | s (assistente) | N | ? | N (FAQ diz não bloquear) | ? | N | Fora de escopo |
| Triagem por IA / Call Screen | N | S (no aparelho, sem enviar à nuvem) | N | N | s (só EUA/Canadá) | N | Fora de escopo |
| STIR/SHAKEN como sinal | ? | ? | ? | ? | ? | ? | **Sim** (API 30+) |
| Discador próprio / app de telefone padrão | N | S | s | ? | ? | s | Não (decisão: papel de triagem, não discador) |
| Anúncios | s (grátis) | N | ? | s | ? | ? | AdMob planejado |

Observação honesta: onde está "?" não houve fonte suficiente. A ausência de "explicação da decisão" nos seis é minha leitura das fontes lidas, não prova de que nenhum app a tenha.

## 3. Adotar, adaptar, evitar

### Adotar
1. **Lista bloqueada e lista permitida editáveis pelo usuário** (Should I Answer?, Mr. Number). Baixo risco, alto valor percebido.
2. **Bloqueio por prefixo/DDD, ocultos e internacionais** (Mr. Number, Should I Answer?). O motor já suporta; falta persistência e tela.
3. **Base consultável offline** (Whoscall, Should I Answer?), atualizada por Wi-Fi (Should I Answer? adia a atualização para Wi-Fi). Alinha com o requisito "modo offline total".
4. **Modo de rigor ajustável** (alertar vs bloquear), como Should I Answer?. Já existe (`ProtectionMode`).
5. **Denúncia de spam e correção de falso positivo** (todos). Obrigatório para app visto como funcional.
6. **Contatos fora do alcance do app** (Google): o `CallScreeningService` só é chamado para números **fora da agenda**, segundo a documentação do Android [O]. Portanto "agenda como permitida" pode ser obtida sem `READ_CONTACTS`.

### Adaptar
1. **Reputação comunitária**: Whoscall/Should I Answer? usam bases coletivas moderadas. Adaptar para dataset assinado publicado pela Conexus (já há `antispam-core` com assinatura), com denúncias locais agregadas só se o usuário optar por enviar. Sem upload automático.
2. **Privacidade do Google** (sem coletar agenda, desativável a qualquer momento): replicar como regra de produto e texto, não como "melhor esforço".
3. **Triagem por IA**: não construir agora. Registrar como pós-MVP e só no aparelho.
4. **Identificação de empresas** (Caller ID do Google): possível futuramente com dataset de números comerciais públicos brasileiros; depende de fonte e licença (ver `docs/BRAZIL_DATA_SOURCES.md`).

### Evitar
1. **Coletar agenda e registro de chamadas para alimentar base** (modelo Hiya/Whoscall, por inferência das políticas e permissões). Contraria o posicionamento de privacidade e dispara revisão da Play.
2. **Compartilhar dados agregados para marketing** (descrito na política da Hiya [O]).
3. **Gravação de chamadas**: a própria política da Play lista gravador de chamadas como uso inválido das permissões de Call Log/SMS [O].
4. **Bloqueio de SMS**: exigiria papel de SMS padrão; fora do foco.
5. **Anúncio agressivo em ferramenta de telefonia** (reviews de Mr. Number mencionam anúncios, [S]); manter regras da seção 5 do spec.
6. **Paywall nos recursos de segurança básicos**. Um review de Hiya reclama de recursos gratuitos que viraram Premium [S]; para confiança, bloqueio e listas ficam gratuitos.

## 4. Riscos de política da Google Play

Fontes oficiais lidas: política de permissões de SMS e Call Log (Ajuda do Console, answer 9047303), política de anúncios (answer 9857753), guia Android "Screen calls". Datas de acesso na seção 7.

### 4.1 READ_CALL_LOG
- A Play restringe a permissão a usos aprovados: padrão de SMS/Telefone/Assistente, ou **exceção** declarada no Permissions Declaration Form. A lista de exceções cita **"Caller ID, detecção e/ou bloqueio de spam"**, mas ela é temporária, sujeita à revisão e só vale quando **não existe método alternativo** [O].
- O guia Android afirma que, a partir do Android 10, `CallScreeningService` **dispensa READ_CALL_LOG** para triagem e caller ID [O].
- Conclusão: como o AntiSpam BR usa `CallScreeningService`, existe método alternativo, e o pedido de exceção tende a ser negado. **Decisão: remover `READ_CALL_LOG` do manifesto do MVP.** O histórico do app vem das decisões do próprio serviço (tabela `history_entries`), não do log do sistema. Risco residual: o campo da Play não exige a permissão se ela nem está no manifesto, o que elimina a declaração.

### 4.2 ANSWER_PHONE_CALLS e demais permissões de telefonia
- Não encontrei a política específica da Play para `ANSWER_PHONE_CALLS` nesta pesquisa **[NC]**. Tecnicamente, `setDisallowCall/setRejectCall` do `CallScreeningService` bloqueiam sem ela. **Decisão: remover.** Mesmo raciocínio para `MANAGE_OWN_CALLS` (é para apps VoIP), `FOREGROUND_SERVICE_PHONE_CALL` e o `foregroundServiceType="phoneCall"` no serviço de triagem (o serviço de screening é vinculado pelo sistema, não é foreground service). `BIND_SCREENING_SERVICE` não deve ser `uses-permission`; ela só vai no atributo `android:permission` do `<service>`.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` é permissão de uso restrito pela Play **[NC: regra exata não lida]**; como o serviço é do sistema, não é necessária. Remover do MVP.
- `READ_PHONE_STATE`: avaliar se é necessária; o screening não depende dela. Remover se não houver uso comprovado.

### 4.3 READ_CONTACTS
- É permissão runtime sensível, coberta pela política de Dados do Usuário (divulgação em destaque, consentimento, política de privacidade). **Não encontrei** regra específica de bloqueio como a de Call Log **[NC]**.
- Como o screening já pula contatos (Seção 3, Adotar 6), **o MVP não precisa de `READ_CONTACTS`**. Entra só em versão posterior, e apenas para mostrar o **nome** do contato no histórico ou para regras do tipo "só contatos tocam" em modo agressivo. Nesse caso, leitura local, nunca enviada.

### 4.4 Papel de call screening
- O usuário escolhe o app em Configurações; o app solicita via `RoleManager.createRequestRoleIntent(ROLE_CALL_SCREENING)`. Apenas um app tem o papel por vez. O serviço precisa responder em até ~5 s (comentário do framework, espelho do AOSP, [S]). O motor tem fail-safe para ALLOW.
- Risco de revisão: o app deve explicar na tela e na ficha por que precisa do papel e o que acontece ao negar. Sem o papel, o app deve continuar abrindo, mostrar estado "proteção inativa" e permitir o uso manual (Verificar).

### 4.5 Declaração de uso de permissões e Data safety
- Se nenhuma permissão restrita (Call Log/SMS) estiver no manifesto, **não há Permissions Declaration Form** a enviar. Isso é o caminho de menor risco.
- Data safety deve refletir: sem coleta de números de agenda; histórico só no aparelho; denúncia opcional envia o número denunciado (se for implementada para servidor); AdMob coleta ID de publicidade e dados de dispositivo conforme consentimento. O preenchimento final é do proprietário na Play Console; não foi verificado aqui.
- Política de privacidade pública (URL) é exigida; `docs/PRIVACY.md` existe, mas a URL pública não foi verificada (ver `current-state.md`).

### 4.6 Política de anúncios em apps de telefonia
- A política de anúncios proíbe anúncios inesperados que causem toque acidental, interstitiais que aparecem ao sair do app ou sem fechamento claro, anúncios que imitam notificações/alertas do sistema e anúncios fora do app (tela inicial/overlays) [O]. O texto lido **não trata especificamente de telefonia** [O]; trato como risco reputacional alto por analogia (tela de chamada recebida é onde o usuário toca por reflexo).
- Regras do projeto (confirmadas no spec): nenhum anúncio em notificação, overlay, tela de chamada ou fluxo do serviço; banner só em Histórico e Ajustes; intersticial raro e só após ação concluída em Verificar; consentimento UMP antes de qualquer request.
- Se o público incluir menores, regras adicionais valem. O app é de consumo geral; classificação e público-alvo são declarações do proprietário, não verificadas.

## 5. Lições de privacidade e confiança

1. **Diga o que não coleta.** O Google afirma com clareza que não pega números da agenda e permite desativar. Copiar essa clareza vale mais que qualquer feature.
2. **Terceiros também são pessoas.** A política da Hiya admite coletar dados de quem liga ou recebe ligação mesmo sem ser usuário. O AntiSpam BR deve manter tudo local por padrão e só enviar o que o usuário escolher denunciar.
3. **Sem venda nem compartilhamento para marketing**, nem de forma agregada. Dizer isso na tela de Ajustes e na ficha.
4. **Retenção explícita.** A política da Hiya não dá prazos [O]. Definir e mostrar: histórico local com retenção configurável (padrão 90 dias) e apagar tudo em um toque.
5. **Explicabilidade como confiança.** Nenhum dos seis, nas fontes lidas, mostra por que um número foi marcado. O campo `explanation` do motor permite isso e reduz reclamações de falso positivo.
6. **Falso positivo custa mais que falso negativo.** Emergência nunca bloqueada, contatos pulados pelo sistema, fail-safe para ALLOW, desbloqueio em dois toques.
7. **Paywall não cobre segurança básica.** Monetizar com anúncios discretos e, no futuro, itens opcionais, não bloqueio.
8. **Não prometer o que não dá.** Os próprios listings de Hiya e do Google admitem que nenhuma detecção pega tudo. Evitar "100% de proteção" na ficha.

## 6. Recomendações priorizadas para o produto

1. Reduzir o manifesto às permissões realmente usadas (Seção 4.2).
2. Entregar histórico real, listas editáveis, regras por prefixo e denúncia local antes de qualquer anúncio real.
3. Publicar dataset seed assinado para o Brasil, versionado e atualizável por Wi-Fi.
4. Escrever a ficha da Play destacando: local-first, sem acesso a agenda, sem gravação, anúncios apenas em duas telas.

## 7. Fontes e data de acesso

Todas acessadas em 2026-10-28.

| # | Fonte | URL | Nível | Uso |
|---|---|---|---|---|
| 1 | Google Phone: privacidade de Spam e Caller ID | https://support.google.com/phoneapp/answer/12027891 | O | Não coleta agenda; sem compartilhar com parceiros; desativável |
| 2 | Google Phone: Caller ID e proteção contra spam | https://support.google.com/phoneapp/answer/3459196 | O (via busca) | Filtro opcional de spam, denúncia |
| 3 | Google Phone: Call Screen | https://support.google.com/phoneapp/answer/9118387 | O | Triagem no aparelho, usa contatos |
| 4 | Android Developers: Screen calls | https://developer.android.com/develop/connectivity/telecom/dialer-app/screen-calls | O | `CallScreeningService`, sem READ_CALL_LOG, contatos fora do callback |
| 5 | Play Console: permissões de SMS e Call Log | https://support.google.com/googleplay/android-developer/answer/9047303 | O | Handler padrão, exceções, declaração |
| 6 | Play Console: política de anúncios | https://support.google.com/googleplay/android-developer/answer/9857753 | O | Anúncios disruptivos, overlays, intersticial |
| 7 | Hiya: política de dados | https://hiya.com/hiya-data-policy | O | Dados coletados, compartilhamento, retenção |
| 8 | Hiya: listagem na App Store | https://apps.apple.com/us/app/-/id986999874 | S | Recursos Premium, disponibilidade EUA/Canadá |
| 9 | Whoscall: listagem espelhada (App Store/APKMirror) | https://apps.apple.com/tj/app/id929968679?l=en-gb | S | Base offline, Premium, bloqueio com app padrão |
| 10 | Whoscall: site | https://web.whoscall.com/en-US | O | Só links; sem detalhes de features ou preço |
| 11 | CallApp: site | https://callapp.com/ | O | Identificador, bloqueio, gratuito; sem política lida |
| 12 | Mr. Number: FAQ Android (Hiya) | https://hiyahelp.zendesk.com/hc/en-us/articles/115015333847-Mr-Number-FAQ-Android | S (403 na leitura; conteúdo via resultado de busca) | Bloqueio de SMS não suportado |
| 13 | Mr. Number: APKMirror | https://www.apkmirror.com/apk/hiya/mr-number-block-calls-spam | S | Prefixo, área, país |
| 14 | Should I Answer?: listagem espelhada | https://apps.appfollow.io/android/should-i-answer/org.mistergroup.shouldianswer?country=pt | S | Listas, offline, bloqueio de ocultos |
| 15 | Páginas da Play Store dos seis apps | play.google.com (vários) | NC | Não carregaram na ferramenta; não usadas como fonte |

Preços, número de instalações e política de privacidade de Whoscall, CallApp, Mr. Number e Should I Answer? **não foram confirmados**.
