# Discord — blueprint do servidor

**Nome:** `AntiSpam BR — Comunidade`
**Descrição (Comunidade):** Suporte, denúncias de golpes e novidades do AntiSpam BR, o bloqueador de spam telefônico para Android.

## Papéis

| Papel | Quem | Permissões |
|---|---|---|
| Dono | Conta do dono | Tudo |
| Moderador | Pessoas de confiança | Gerenciar mensagens, silenciar (timeout), expulsar; sem gerenciar servidor |
| Contribuidor | Quem ajuda de forma consistente (denúncias úteis, testes) | Anexar imagens, usar #off-topic com mais liberdade; selo de destaque |
| Membro | Todos após verificação | Ler e escrever nos canais abertos |

`@everyone` (antes da verificação) só enxerga #boas-vindas e #regras.

## Categorias e canais

| Categoria | Canal | Quem escreve | Observação |
|---|---|---|---|
| INÍCIO | #boas-vindas | Ninguém (só leitura) | Mensagem de boas-vindas + botão/reação de verificação |
| INÍCIO | #regras | Ninguém (só leitura) | Regras e FAQ fixada |
| INÍCIO | #anuncios | Dono e Moderador | Canal de Anúncios (pode ser seguido); versões e lançamentos |
| AJUDA | #suporte | Membro | Use fórum ou threads: uma conversa por problema |
| AJUDA | #bugs | Membro | Modelo: aparelho, Android, versão do app, passos |
| ALERTAS | #denuncie-golpes | Membro | Só relatos mascarados, modelo fixado; slowmode 30 s |
| ALERTAS | #golpes-em-alta | Dono e Moderador (webhook também) | Campanhas ativas; Membro só reage |
| COMUNIDADE | #sugestoes | Membro | Fórum com tags (aprovada, planejada, feita) |
| COMUNIDADE | #off-topic | Membro | Conversa livre, mesmas regras |
| EQUIPE | #mod (privado) | Dono e Moderador | Triagem de denúncias e decisões |

## Verificação de entrada

- Nível de verificação do servidor: **Médio** (conta com e-mail verificado, +5 min no Discord).
- Filtro de conteúdo explícito: todos os membros.
- Em **Configurações > Integração > Triagem de membros**: exibir as regras e exigir aceite.
- Permissões padrão: sem @everyone/@here, sem anexos e sem incorporar links para quem acabou de entrar (liberado após o papel Membro).
- Atribuir papel Membro automaticamente após o aceite das regras (Onboarding da Comunidade).

## Regras (texto pronto)

1. Respeito: nada de ofensas, assédio ou discriminação.
2. Sem dados pessoais de terceiros. Mascare números: `(11) 9XXXX-1234`.
3. Sem links encurtados, convites de outros servidores ou divulgação.
4. Sem pedir ou oferecer pagamento, Pix, venda ou doação.
5. Nunca compartilhe senha, código SMS ou dados de cartão. A equipe jamais pede isso.
6. A equipe não faz suporte por DM. Desconfie de quem chamar no privado.
7. Um assunto por canal; use o modelo de denúncia ou de bug.
8. Decisões da moderação são finais. Dúvidas: [EMAIL_SUPORTE].

## Mensagem de boas-vindas e FAQ

Boas-vindas, regras resumidas e o aviso de suporte falso estão em `mensagens-prontas.md`. Fixar em #regras uma FAQ curta:

- **O app bloqueia 100% do spam?** Não; reduz bastante e melhora com denúncias.
- **O app coleta meus contatos ou ligações?** O app prioriza privacidade; veja a política na ficha da Play ([LINK_PLAY_STORE]).
- **Como denunciar um golpe?** Em #denuncie-golpes, com o modelo.
- **Encontrei um bug.** Use #bugs com o modelo.
- **Falaram comigo no privado em nome da equipe.** É golpe. Avise um Moderador.

## AutoMod (Configurações > AutoMod)

1. **Bloquear links**: palavras-chave `http://`, `https://`, `bit.ly`, `tinyurl`, `t.me/+`, `wa.me`, `cutt.ly`, `is.gd`. Isentar Moderador, Dono e Contribuidor. Ação: bloquear e alertar #mod.
2. **Convites**: bloquear `discord.gg/`, `discord.com/invite`, `discordapp.com/invite`, exceto o convite oficial.
3. **Palavras de golpe**: `pix`, `chave pix`, `senha`, `código sms`, `token`, `cartão`, `cvv`, `investimento garantido`, `renda extra`, `ganhe dinheiro`, `suporte oficial`, `me chama no privado`. Ação: bloquear, alertar #mod e silenciar 10 min.
4. **Spam**: ativar "Conteúdo de spam suspeito" e "Spam de menções" (limite 5).
Revisar o log semanalmente e ajustar falsos positivos (ex.: a palavra "senha" em #suporte pode exigir exceção para Moderador).

## Webhooks de aviso (API BFF)

O API BFF do projeto envia avisos ao Discord por **chave** em `DISCORD_WEBHOOKS` (mapa chave para webhook). Para ligar:

1. O **dono** cria o webhook no canal desejado (por exemplo #anuncios e #golpes-em-alta): Configurações do canal > Integrações > Webhooks > Novo webhook.
2. O dono salva a URL **como segredo** no ambiente do API BFF, na chave correspondente de `DISCORD_WEBHOOKS`. Ver a skill `discord-webhooks` para as chaves aceitas.
3. Nunca colar a URL ou token em chat, issue, commit ou documento. Se vazar, excluir o webhook e criar outro.

## Checklist de cliques do dono

- [ ] Discord > "+" > Criar o meu > Para uma comunidade > nome `AntiSpam BR — Comunidade` e ícone.
- [ ] Criar papéis Moderador, Contribuidor, Membro (ordem: Dono, Moderador, Contribuidor, Membro).
- [ ] Criar categorias e canais da tabela acima; ajustar permissões de cada um (ex.: leitura apenas em #boas-vindas/#regras/#anuncios/#golpes-em-alta para Membro).
- [ ] Colar regras e boas-vindas de `mensagens-prontas.md`; fixar a FAQ.
- [ ] Configurações > Ativar Comunidade (requer verificação e filtro de conteúdo ligados; definir #regras e um canal de atualizações).
- [ ] Configurar AutoMod e verificação conforme acima.
- [ ] Transformar #anuncios em canal de Anúncios.
- [ ] Criar o convite **permanente** (canal #boas-vindas > Convidar > Editar link > nunca expira, sem limite de usos) e registrar como `[LINK_DISCORD]`.
- [ ] Ativar autenticação em duas etapas na conta do dono e dos moderadores (exigida para moderar com 2FA).
- [ ] Criar webhooks e salvar como segredo (seção acima).
- [ ] Substituir `[LINK_DISCORD]` nos textos, app e ficha da Play.
