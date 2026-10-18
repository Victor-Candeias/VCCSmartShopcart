---
maestru: "0.4"
type: work-spec
id: dr-02-catalog-spec
title: "Especificação: Catálogo de despesas: seed JSON sem duplicados e ecrã de gestão"
template: implementation-plan-v1
work-item: vcc-despesas-rapidas/dr-02-catalog
owner: developer
created: 2026-10-18
---

# Especificação: Catálogo de despesas: seed JSON sem duplicados e ecrã de gestão

## Overview

Catálogo editável e independente das listas (§3). O seed inicial vem de `assets/expense_catalog.json` com IDs estáveis (`seed-…`), importado com `INSERT OR IGNORE`: reimportar nunca duplica nem sobrescreve nomes, preços, ordem, favoritos ou ocultação definidos pelo utilizador. A versão do seed fica em DataStore para não reler o ficheiro em cada arranque.

## Implementation

### Fase 1 — Seed

- `app/src/main/assets/expense_catalog.json` (`version`, `categories[]`, `products[]`).
- `data/seed/ExpenseCatalogSeed.kt`: DTO `@Serializable` e parser.
- `ExpenseCatalogSeeder.seedIfNeeded()`: compara a versão com a guardada (`ExpensePreferences`, DataStore), insere com `IGNORE` numa transação e grava a versão; chamado pelos ViewModels ao abrir o modo (início, editor, catálogo), evitando trabalho em `Application.onCreate`.

### Fase 2 — Casos de utilização

- `GetExpenseCatalogUseCase` (categorias + produtos ativos, favoritos primeiro, filtro por texto sem acentos).
- `SaveExpenseProductUseCase` (criar/editar; nome obrigatório, preço habitual ≥ 0), `SetExpenseProductFavoriteUseCase`, `SetExpenseProductActiveUseCase`, `MoveExpenseProductUseCase` (troca `sortOrder` com o vizinho).

### Fase 3 — UI

- `feature/expenses/catalog/ExpenseCatalogScreen` + ViewModel: chips de categoria, pesquisa, mostrar ocultos, ações favorito/subir/descer/ocultar e diálogo `ExpenseProductDialog` (reutilizado no editor).

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `assets/expense_catalog.json` | create | Seed inicial |
| `data/seed/ExpenseCatalogSeeder.kt` | create | Importação idempotente |
| `domain/usecase/expense/ExpenseCatalogUseCases.kt` | create | Casos de utilização |
| `feature/expenses/catalog/*` | create | Ecrã e ViewModel |
| `feature/expenses/components/ExpenseProductDialog.kt` | create | Diálogo de produto |
| `core/datastore/ExpensePreferences.kt` | create | Versão do seed importada |
| `core/navigation/*` | modify | Rota `ExpenseCatalogRoute` |
| `res/values/strings.xml` | modify | Textos |
| `test/.../ExpenseCatalogSeederTest.kt`, `ExpenseCatalogUseCasesTest.kt` | create | Testes |
