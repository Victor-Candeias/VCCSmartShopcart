---
maestru: "0.4"
type: work-spec
id: dr-08-inline-ad-hoc-product-spec
title: "Especificação: Produto inexistente a partir da pesquisa"
template: implementation-plan-v1
work-item: vcc-despesas-rapidas/dr-08-inline-ad-hoc-product
owner: developer
created: 2026-10-21
---

# Especificação: Produto inexistente a partir da pesquisa

## Overview

O DR-07 só oferecia o produto temporário dentro do diálogo "Novo produto". O fluxo esperado é: escrever o nome na pesquisa e, quando não existe um produto com esse nome (comparação sem acentos nem maiúsculas), escolher diretamente entre "só nesta despesa" e "guardar no catálogo".

## Implementation

- `ExpenseSearch.hasExactMatch(products, query)` indica se a pesquisa corresponde exatamente a um produto ativo do catálogo.
- `ExpenseEditorUiState.newProductName` contém o texto da pesquisa quando não há correspondência exata (nulo caso contrário).
- `ExpenseEditorViewModel.addQueryAsTemporary()` acrescenta uma linha com `productId = null` e preço vazio, limpa a pesquisa e devolve a chave da linha.
- No ecrã, com `newProductName` definido, o painel mostra os chips "«X» só nesta despesa" (abre o `PriceDialog` da nova linha) e "Guardar «X» no catálogo" (abre o `ExpenseProductDialog` pré-preenchido, sem a opção temporária). O chip "Novo produto" mantém-se quando a pesquisa está vazia ou tem correspondência exata.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/smartshopcart/domain/usecase/expense/ExpenseCatalogUseCases.kt` | modify | `ExpenseSearch.hasExactMatch`. |
| `app/src/main/java/pt/vcc/smartshopcart/feature/expenses/editor/ExpenseEditorViewModel.kt` | modify | `newProductName` no estado e `addQueryAsTemporary()`. |
| `app/src/main/java/pt/vcc/smartshopcart/feature/expenses/editor/ExpenseEditorScreen.kt` | modify | Chips de produto inexistente e abertura automática do preço. |
| `app/src/main/res/values/strings.xml` | modify | Textos dos novos chips. |
| `app/src/test/java/pt/vcc/smartshopcart/domain/usecase/expense/ExpenseCatalogUseCasesTest.kt` | modify | Testes de `hasExactMatch`. |
