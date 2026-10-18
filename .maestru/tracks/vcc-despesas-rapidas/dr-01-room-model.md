---
maestru: "0.4"
type: work-item
id: dr-01-room-model
title: "Despesas: modelo de domínio, entidades Room, DAO e migração 2→3"
created: 2026-10-18
owner: developer
priority: high
status: done
completed: 2026-10-18
specs:
  - dr-01-room-model-spec
track: vcc-despesas-rapidas
---

# dr-01-room-model: Despesas: modelo de domínio, entidades Room, DAO e migração 2→3

Fonte: §5, §6 e §10 (etapa 1) de `Documents/CestoSmart_Despesas_Rapidas_Especificacao.md`.

## Âmbito

- Modelos de domínio `ExpenseCategory`, `ExpenseProduct`, `Expense`, `ExpenseItem`, `Establishment`, `ExpenseDraft`/`ExpenseDraftItem` (cêntimos `Long`, quantidades inteiras, `Math.multiplyExact`/`addExact`).
- Entidades Room `expense_categories`, `expense_products`, `expenses`, `expense_items`, `establishments` com FKs (itens `CASCADE`, produto e estabelecimento `SET NULL`) e índices.
- `ExpenseDao` e `ExpenseRepository` (`RoomExpenseRepository`) com `saveExpense` numa única `withTransaction` e total recalculado a partir dos itens.
- Base de dados versão 3 com `MIGRATION_2_3` explícita e esquema `3.json` exportado.
- Testes: DAO/repositório (transação, snapshots, cascata) e migração 2→3.

## Saída verificável

Migração 2→3 testada, esquema `3.json` versionado e testes de repositório a passar.
