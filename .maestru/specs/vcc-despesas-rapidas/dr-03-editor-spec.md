---
maestru: "0.4"
type: work-spec
id: dr-03-editor-spec
title: "Especificação: Editor de despesa: modo no Início, seleção rápida, quantidades, total e rascunho"
template: implementation-plan-v1
work-item: vcc-despesas-rapidas/dr-03-editor
owner: developer
created: 2026-10-18
---

# Especificação: Editor de despesa: modo no Início, seleção rápida, quantidades, total e rascunho

## Overview

Fluxo principal do §2.2: Início → Despesas rápidas → categoria → produtos → preço → quantidades → estabelecimento opcional → guardar. O rascunho é persistido localmente em DataStore (JSON) a cada alteração e só é limpo ao guardar ou cancelar, permitindo retomar uma despesa interrompida (§2.4).

## Implementation

### Fase 1 — Domínio

- `CalculateExpenseTotalUseCase` e `ExpenseValidation` (pelo menos um item, quantidade ≥ 1, preço definido e ≥ 0).
- `SaveExpenseUseCase` único para criar e atualizar (valida com `ExpenseValidation`, grava via `ExpenseRepository.saveExpense(draft, editingExpenseId)` e limpa o rascunho) e `StartExpenseDraftUseCase` (nova despesa à hora atual, com categoria/produto opcionais).
- `ExpenseEditorDraft` serializável (preço anulável enquanto não confirmado, `editingExpenseId?`, `selectedCategoryId?`) e `ExpenseDraftStore` (chave `expense_draft` no DataStore de `ExpensePreferences`).

### Fase 2 — Navegação e Início

- Rotas `ExpensesHomeRoute`, `ExpenseEditorRoute(expenseId?, categoryId?, productId?, repeatFromId?)`, `ExpenseCatalogRoute`, `ExpenseHistoryRoute`, `ExpenseDetailRoute`.
- Terceira entrada no `HomeScreen`; `DespesasHomeScreen` com categorias, favoritos, Nova despesa, Histórico, Gerir catálogo e cartão "Continuar despesa".

### Fase 3 — Editor

- `ExpenseEditorScreen` + `ExpenseEditorViewModel`: estabelecimento com sugestões, data/hora (DatePicker/TimePicker), linhas com −/+ e preço (diálogo com `NumericKeypad`), subtotal, total fixo no fundo, painel de produtos (chips de categoria + pesquisa), "Novo produto" (cria no catálogo e adiciona ao rascunho), Guardar/Cancelar e conflito com rascunho existente (continuar ou descartar).

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `domain/usecase/expense/ExpenseUseCases.kt` | create | Criar, total, validação |
| `domain/model/expense/ExpenseEditorDraft.kt` | create | Rascunho do editor |
| `core/datastore/ExpensePreferences.kt` | modify | Persistência do rascunho (implementa `ExpenseDraftStore`) |
| `core/navigation/Routes.kt`, `AppNavHost.kt` | modify | Rotas e grafo |
| `feature/home/HomeScreen.kt` | modify | Terceiro modo |
| `feature/expenses/home/*`, `feature/expenses/editor/*` | create | Ecrãs e ViewModels |
| `res/values/strings.xml` | modify | Textos |
| `test/.../ExpenseUseCasesTest.kt` | create | Testes |
