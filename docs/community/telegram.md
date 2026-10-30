# Telegram — plano

Estrutura: **um canal de avisos (somente leitura)** + **um grupo de discussão vinculado** ao canal.

## Canal de avisos

- **Nome:** `AntiSpam BR — Avisos`
- **Tipo:** público, com nome de usuário reservado (ex.: @antispambr, sujeito a disponibilidade).
- **Descrição:** Avisos oficiais do AntiSpam BR: novas versões e golpes em circulação. Não pedimos senha, código SMS nem Pix. Suporte: [EMAIL_SUPORTE]
- Só administradores publicam. Ativar assinatura de mensagens desligada (posts saem como o canal).

## Grupo de discussão

- **Nome:** `AntiSpam BR — Conversa`
- **Descrição:** Suporte entre usuários, denúncias de golpes e sugestões. Leia as regras fixadas. A equipe não faz suporte por DM.
- Vincular ao canal: Canal > Editar > Chat de discussão > escolher o grupo.
- Configurações do grupo: novos membros não veem histórico anterior ao entrar, permissões de membro sem enviar links, sem convidar usuários; modo lento de 30 s; aprovação de novos membros ligada (se disponível).

## Regras (fixar no grupo)

1. Respeito acima de tudo.
2. Sem dados pessoais de terceiros; mascare números: `(11) 9XXXX-1234`.
3. Sem links encurtados, divulgação ou convites.
4. Sem pedir ou oferecer pagamento, Pix, venda ou doação.
5. Nunca poste senha, código SMS ou dados de cartão.
6. A equipe não pede esses dados e não faz suporte por DM.
7. Denúncia de golpe: use o modelo de `mensagens-prontas.md`.

## Mensagens fixadas

- **Canal:** anúncio de lançamento ou, enquanto não houver, o aviso anti-golpe de suporte falso.
- **Grupo:** regras resumidas + aviso anti-golpe + modelo de denúncia. Textos em `mensagens-prontas.md`.

## Política anti-golpe

A comunidade será alvo de golpistas fingindo ser suporte. Princípios:

- A equipe **NUNCA** pede senha, código SMS (inclusive o do WhatsApp/Telegram) ou Pix.
- **DM não é suporte oficial.** Quem chamar no privado dizendo ser da equipe é golpista: denunciar e bloquear.
- Administradores reais ficam identificados com título de admin visível ("Equipe AntiSpam BR"); lista pública de admins fixada.
- Perfis que imitam a equipe (mesma foto/nome parecido): banir na hora e avisar o grupo.
- Suporte oficial: apenas no grupo ou por [EMAIL_SUPORTE].

## Moderação

- Moderadores humanos com permissões mínimas (apagar mensagens, restringir/banir); só o dono fica como proprietário.
- **Bot de moderação: opcional.** Só adicione se o volume de spam justificar. Critérios para escolher: código aberto ou mantenedor conhecido, permissões mínimas de admin, sem acesso a dados além do necessário, sem enviar dados a terceiros, boa documentação em pt-BR. Não recomendamos um bot específico sem testá-lo antes em grupo de teste. Funções úteis: captcha de entrada, apagar links, banir contas novas que enviam link.
- Revisar admins e bots a cada trimestre.

## Checklist de cliques do dono

- [ ] Ativar verificação em duas etapas na conta Telegram (Configurações > Privacidade e segurança).
- [ ] Novo canal > nome, descrição, foto > Público > reservar o nome de usuário.
- [ ] Novo grupo > nome, descrição, foto; definir permissões como acima.
- [ ] Vincular o grupo ao canal (Canal > Editar > Chat de discussão).
- [ ] Publicar e fixar as mensagens de `mensagens-prontas.md`.
- [ ] Adicionar moderadores como administradores com poucas permissões; definir título "Equipe AntiSpam BR".
- [ ] (Opcional) Adicionar bot de moderação após os critérios acima.
- [ ] Copiar o link do canal como `[LINK_TELEGRAM]` e substituir no app, na ficha da Play e nos textos.
