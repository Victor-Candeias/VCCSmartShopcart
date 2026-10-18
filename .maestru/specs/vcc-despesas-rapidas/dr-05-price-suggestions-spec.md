---
maestru: "0.4"
type: work-spec
id: dr-05-price-suggestions-spec
title: "Especificação: Sugestão de preços por estabelecimento, último consumo e catálogo"
template: implementation-plan-v1
work-item: vcc-despesas-rapidas/dr-05-price-suggestions
owner: developer
created: 2026-10-18
---

# Especificação: Sugestão de preços por estabelecimento, último consumo e catálogo

## Overview

Ao selecionar um produto sugere-se um preço (§7) pela ordem: último preço pago no mesmo estabelecimento → último preço pago em qualquer estabelecimento → preço habitual do catálogo → vazio. A sugestão nunca é tratada como preço garantido: fica marcada como sugestão até o utilizador a confirmar ou alterar.

## Implementation

- `ExpenseDao.lastPriceAt(productId, establishmentName)` e `lastPrice(productId)` (`expense_items` ⨝ `expenses` ordenado por `occurredAt DESC`).
- `ExpenseRepository.lastPaidPrice(productId, establishmentName?)`.
- `SuggestProductPriceUseCase` → `PriceSuggestion(cents?, source?)` com `PriceSuggestionSource { SAME_ESTABLISHMENT, LAST_PAID, CATALOG }` (`PriceSuggestion.NONE` = campo vazio) e `suggestionsFor(draft)` para recalcular linhas.
- `ExpenseEditorLine.priceSource` (nulo = preço confirmado pelo utilizador) e `ExpenseEditorDraft.applySuggestions` (só altera linhas com preço sugerido ou vazio). Alterar o preço no teclado confirma-o; guardar a despesa confirma os preços sugeridos visíveis.
- Editor: ao adicionar um produto usa a sugestão; ao mudar o estabelecimento recalcula apenas as linhas com preço ainda sugerido; mostra a origem ("Último preço aqui", "Último preço pago", "Preço habitual").
- Repetição usa a sugestão com recurso ao preço original.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `core/database/dao/ExpenseDao.kt` | modify | Consultas de último preço |
| `domain/usecase/expense/SuggestProductPriceUseCase.kt` | create | Prioridade de sugestão |
| `domain/model/expense/ExpenseEditorDraft.kt` | modify | Origem da sugestão por linha |
| `domain/usecase/expense/ExpenseUseCases.kt` | modify | Nova despesa e repetição com sugestões |
| `feature/expenses/editor/*` | modify | Integração |
| `test/.../SuggestProductPriceUseCaseTest.kt` | create | Testes de prioridade |
