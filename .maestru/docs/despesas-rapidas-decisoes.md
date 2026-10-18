---
maestru: "0.4"
type: doc
id: despesas-rapidas-decisoes
title: "Decisões do modo Despesas Rápidas"
description: "Decisões que concretizam o documento Documents/CestoSmart_Despesas_Rapidas_Especificacao.md no código existente (DR1–DR10)."
created: 2026-10-18
updated: 2026-10-18
owner: developer
tags: [despesas-rapidas, decisoes, arquitetura]
---

# Decisões do modo Despesas Rápidas

<!-- maestru:summary -->
Decisões DR1–DR10 que adaptam a especificação Despesas Rápidas à app: modelo Room v3, seed versionado, rascunho em DataStore, gravação única, sugestão de preços e critérios de aceitação.
<!-- /maestru:summary -->

Documento de origem: [`Documents/CestoSmart_Despesas_Rapidas_Especificacao.md`](../../Documents/CestoSmart_Despesas_Rapidas_Especificacao.md).
Work-items: [`vcc-despesas-rapidas`](../tracks/vcc-despesas-rapidas.md) (dr-01 … dr-06).

| # | Decisão |
|---|---------|
| DR1 | Tabelas `expense_categories`, `expense_products`, `establishments`, `expenses`, `expense_items` na mesma `ShoppingDatabase` (migração 2→3). Valores em cêntimos (`Long`), quantidades inteiras 1–999, `nameSnapshot`/`unitPriceCents` preservam o histórico. |
| DR2 | `RoomExpenseRepository.saveExpense(draft, expenseId?)` cria ou substitui itens e recalcula o total numa transação; o estabelecimento é resolvido pelo nome (sem distinção de maiúsculas). |
| DR3 | Catálogo predefinido em `assets/expense_catalog.json` com IDs estáveis e versão; importação idempotente (`INSERT OR IGNORE`) disparada pelos ViewModels do modo, nunca em `Application.onCreate`. |
| DR4 | Produtos são ocultados (`isActive = false`) em vez de apagados; a ordenação manual salta produtos ocultos. |
| DR5 | Rascunho único em JSON na chave `expense_draft` do DataStore (`ExpensePreferences`), gravado a cada alteração e limpo apenas ao guardar ou cancelar; rascunho ilegível é ignorado. |
| DR6 | `SaveExpenseUseCase` único para criar e editar (`editingExpenseId`); `ExpenseValidation` rejeita despesas sem itens, com preço em falta ou valores inválidos antes de chegar à base de dados. |
| DR7 | Editar mantém data/hora originais; repetir cria um novo rascunho com a data atual, os mesmos produtos/quantidades e preços sugeridos (recurso ao preço original). |
| DR8 | Sugestão de preço (§7): último pago no mesmo estabelecimento → último pago → preço habitual → vazio, por `occurredAt DESC`. A linha guarda `priceSource`; alterar o preço confirma-o e mudar o estabelecimento só recalcula linhas ainda sugeridas. Guardar confirma os preços sugeridos visíveis. |
| DR9 | Histórico agrupado por dia com filtros por categoria e estabelecimento; ações Ver, Editar, Repetir e Eliminar (com confirmação). |
| DR10 | Critérios do §11 cobertos por `ExpenseAcceptanceTest` (Room + DataStore reais, sem rede) e pelos testes de repositório, seed, catálogo e sugestões. Só ícones de `material-icons-core`; controlos com `contentDescription` e valores em `pt-PT` via `MoneyFormatter`. |
