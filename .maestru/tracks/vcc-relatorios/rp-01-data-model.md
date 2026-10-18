---
maestru: "0.4"
type: work-item
id: rp-01-data-model
title: "Dados e agregações: snapshots (migração 3→4), modelo de relatório, ReportsDao, repositório e GenerateReportUseCase"
created: 2026-10-18
owner: developer
priority: high
specs:
  - rp-01-data-model-spec
status: done
completed: 2026-10-19
track: vcc-relatorios
---

# rp-01-data-model: Dados e agregações: snapshots (migração 3→4), modelo de relatório, ReportsDao, repositório e GenerateReportUseCase

Fonte: §1, §4, §5, §6 e Sprint 1 do §16.

## Âmbito

- Inventário das entidades Room dos três modos e snapshots em falta: categoria (id/nome) em `expense_items` e nome/categoria do produto em `shopping_session_items`, preenchidos ao gravar/concluir; migração 3→4 com *backfill* e índice `shopping_sessions(status, completedAt)`.
- Modelo `ReportMode`, `ReportFilter`, `ReportRow`, `ReportSummary`, `ReportData` (cêntimos `Long`, quantidades `BigDecimal`).
- `ReportsDao` que normaliza despesas e sessões `COMPLETED` (`CALCULATOR`/`PLANNED_LIST`) com intervalo local `[início, fim)`; orçamento vs. realizado e indicadores de dados incompletos.
- `ReportsRepository`, `GenerateReportUseCase` (transações por `(modo, id)`), séries por categoria/dia/semana/mês (com zeros), estabelecimento, produto e comparação com período equivalente.
- Testes: mesma despesa com 2 linhas, lista não concluída, sessão cancelada, agregação sem duplicação, filtro por categoria, mudança de hora, snapshot após alteração do catálogo, relatório vazio, migração 3→4.

## Saída verificável

`gradlew testDebugUnitTest` verde com os novos testes de relatórios e de migração.
