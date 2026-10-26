# Critérios de aceite

O projeto só é considerado concluído com evidência verificável de cada item. Status atual em cada linha.

| # | Critério | Evidência esperada | Status |
|---|---|---|---|
| 1 | Inventário dos projetos gerado | `docs/sdd/current-state.md` e fleet | Feito (parcial: 5 apps Expo e o Kotlin não verificados em detalhe) |
| 2 | `tsc --noEmit` passa | saída do comando, exit 0 | **Feito** (após limpeza de `tsconfig.json`) |
| 3 | Testes automatizados passam | `jest --ci`, 10 suítes, 116 testes | **Feito** |
| 4 | `expo export` Android gera bundle | `Exported:` e bundle `.hbc` de 4,1 MB (2026-10-22, local) | **Feito** |
| 5 | AAB de produção gerado | caminho e SHA-256 do arquivo | Pendente (exige `eas build`, autorização) |
| 6 | Assinatura do AAB confere com a esperada | saída de verificação | Pendente |
| 7 | Package e versionCode validados | `scripts/publish/validate.sh` exit 0 (package = fleet.yml, guards AdMob) | **Feito** (sem `--min-version-code`: último publicado não consultado) |
| 8 | Integração AdMob compila com IDs de teste | build `preview` | Pendente |
| 9 | Consentimento UMP validado nos cenários | relatório por cenário | Pendente |
| 10 | Metadados e declarações com checklist | checklist preenchido e confirmado | Pendente |
| 11 | Upload confirmado pela API | ID da edição | Pendente (requer credencial) |
| 12 | Publicação confirmada pelo Google | estado no Console | Pendente (requer aprovação) |

## Regras de aceite

- Item marcado como feito precisa de comando e saída registrados, não de suposição.
- Testes não podem ser desativados nem excluídos para passar.
- Build verde com checagem removida não conta como aceite.
