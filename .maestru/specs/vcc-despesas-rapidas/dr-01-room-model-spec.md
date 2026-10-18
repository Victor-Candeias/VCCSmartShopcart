---
maestru: "0.4"
type: work-spec
id: dr-01-room-model-spec
title: "Especificação: Despesas: modelo de domínio, entidades Room, DAO e migração 2→3"
template: implementation-plan-v1
work-item: vcc-despesas-rapidas/dr-01-room-model
owner: developer
created: 2026-10-18
---

# Especificação: Despesas: modelo de domínio, entidades Room, DAO e migração 2→3

## Overview

Persistência offline do modo Despesas Rápidas, separada do catálogo de produtos EAN/GTIN (`products`) e das sessões de compra (§8). Valores monetários em cêntimos `Long`, quantidades inteiras, instantes em epoch millis UTC (D10). Os itens guardam `nameSnapshot` e `unitPriceCents` para que alterações ao catálogo não afetem despesas antigas.

## Implementation

### Fase 1 — Domínio

- `domain/model/expense/ExpenseModels.kt`: `ExpenseCategory`, `ExpenseProduct` (com `icon?` opcional), `Establishment`, `Expense`, `ExpenseItem`, `ExpenseSummary` (com nome do estabelecimento, categorias e nº de itens), `ExpenseDetails`.
- `ExpenseDraft`/`ExpenseDraftItem` conforme §5.3 (+ `occurredAt` e `establishmentName`) com totais por `Math.multiplyExact`/`addExact`.
- `domain/repository/ExpenseRepository.kt` (contrato §6.1 alargado: produtos por ID, upsert de produto, estabelecimentos, último preço).

### Fase 2 — Room

- `ExpenseEntities.kt`: `expense_categories`, `expense_products` (FK categoria), `establishments`, `expenses` (FK estabelecimento `SET NULL`, índice `occurredAt`), `expense_items` (FK despesa `CASCADE`, produto `SET NULL`).
- `ExpenseDao` com observação de categorias/produtos/despesas, `insertOrIgnore` para o seed e consultas de último preço.
- `ShoppingDatabase` versão 3, `MIGRATION_2_3` com o SQL gerado pelo Room, `3.json` exportado.

### Fase 3 — Repositório e testes

- `RoomExpenseRepository.saveExpense(draft, expenseId?)`: resolve/cria o estabelecimento por nome, grava cabeçalho e itens e recalcula `totalCents` numa única `withTransaction`.
- `ExpenseRepositoryTest` e `MigrationTest` (2→3 e 1→3).

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `domain/model/expense/ExpenseModels.kt` | create | Modelos de domínio e rascunho |
| `domain/repository/ExpenseRepository.kt` | create | Contrato do repositório |
| `core/database/entity/ExpenseEntities.kt` | create | Entidades Room |
| `core/database/dao/ExpenseDao.kt` | create | DAO |
| `core/database/ShoppingDatabase.kt` | modify | Versão 3 e DAO |
| `core/database/Migrations.kt` | modify | `MIGRATION_2_3` |
| `core/di/DatabaseModule.kt`, `RepositoryModule.kt` | modify | Providers e binding |
| `data/repository/RoomExpenseRepository.kt` | create | Implementação transacional |
| `app/schemas/.../3.json` | create | Esquema exportado |
| `test/.../ExpenseRepositoryTest.kt`, `MigrationTest.kt` | create/modify | Testes |
