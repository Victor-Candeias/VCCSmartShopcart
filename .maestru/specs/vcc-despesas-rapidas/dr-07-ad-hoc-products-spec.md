---
maestru: "0.4"
type: work-spec
id: dr-07-ad-hoc-products-spec
title: "Especificação: Produtos temporários no editor de despesas"
template: implementation-plan-v1
work-item: vcc-despesas-rapidas/dr-07-ad-hoc-products
owner: developer
created: 2026-10-21
---

# Especificação: Produtos temporários no editor de despesas

## Overview

O editor de despesas rápidas deve suportar produtos ocasionais sem obrigar à sua gravação no catálogo. A opção temporária cria uma linha com `productId = null`, preserva o nome e o preço no snapshot da despesa e só persiste o item quando a despesa é guardada.

## Implementation

- Adicionar ao `ExpenseEditorDraft` uma operação para criar uma linha personalizada sem produto de catálogo.
- Alterar o diálogo de novo produto no editor para distinguir “usar apenas nesta despesa” de “guardar no catálogo”.
- Manter a criação no catálogo disponível, mas garantir que a ação temporária não chama `SaveExpenseProductUseCase`.
- Cobrir o fluxo temporário e o cancelamento com testes de domínio/repositório.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/smartshopcart/domain/model/expense/ExpenseEditorDraft.kt` | modify | Criar linhas ad hoc com `productId = null`. |
| `app/src/main/java/pt/vcc/smartshopcart/feature/expenses/editor/ExpenseEditorViewModel.kt` | modify | Encaminhar produto temporário para o rascunho sem o guardar no catálogo. |
| `app/src/main/java/pt/vcc/smartshopcart/feature/expenses/editor/ExpenseEditorScreen.kt` | modify | Apresentar a escolha entre produto temporário e produto do catálogo. |
| `app/src/main/res/values/strings.xml` | modify | Textos das ações temporária e persistente. |
| `app/src/test/java/pt/vcc/smartshopcart/domain/usecase/expense/ExpenseUseCasesTest.kt` | modify | Testar persistência do item temporário e ausência de produto no catálogo. |
