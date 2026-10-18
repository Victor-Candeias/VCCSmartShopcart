---
maestru: "0.4"
type: work-spec
id: dr-04-history-repeat-spec
title: "Especificação: Histórico de despesas: ver, editar, eliminar e repetir"
template: implementation-plan-v1
work-item: vcc-despesas-rapidas/dr-04-history-repeat
owner: developer
created: 2026-10-18
---

# Especificação: Histórico de despesas: ver, editar, eliminar e repetir

## Overview

Histórico local das despesas guardadas (§4.4). Editar reabre o editor com o rascunho da despesa existente; guardar substitui os itens e recalcula o total na mesma transação, mantendo `createdAt`. Repetir cria um novo rascunho com a data atual, os mesmos produtos/quantidades e preços editáveis.

## Implementation

- `LoadExpenseDraftUseCase` (rascunho de edição com `editingExpenseId`; a gravação usa o `SaveExpenseUseCase` do dr-03), `DeleteExpenseUseCase`, `RepeatExpenseUseCase` (devolve `ExpenseEditorDraft` com a data atual) e `ExpenseHistoryFilter` (categoria e estabelecimento).
- `ExpenseHistoryScreen` + ViewModel: cartões com data/hora, estabelecimento, categorias, nº de itens e total, agrupados por dia; filtros por categoria e estabelecimento; menu por cartão com Ver, Editar, Repetir e Eliminar.
- `ExpenseDetailScreen` + ViewModel: itens com quantidade × preço = subtotal, total, ações Editar, Repetir e Eliminar (confirmação).
- Editor: modo edição (`expenseId`) e modo repetição (`repeatFromId`); um rascunho guardado da mesma edição é retomado sem perguntar.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `domain/usecase/expense/ExpenseUseCases.kt` | modify | Atualizar, eliminar, repetir |
| `feature/expenses/history/*` | create | Histórico e detalhe |
| `feature/expenses/editor/ExpenseEditorViewModel.kt` | modify | Edição e repetição |
| `core/navigation/AppNavHost.kt` | modify | Destinos |
| `res/values/strings.xml` | modify | Textos |
| `test/.../ExpenseUseCasesTest.kt` | modify | Testes |
