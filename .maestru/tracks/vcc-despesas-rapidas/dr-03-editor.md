---
maestru: "0.4"
type: work-item
id: dr-03-editor
title: "Editor de despesa: modo no Início, seleção rápida, quantidades, total e rascunho"
created: 2026-10-18
owner: developer
priority: high
status: done
completed: 2026-10-18
specs:
  - dr-03-editor-spec
track: vcc-despesas-rapidas
blocked-by:
  - dr-02-catalog
---

# dr-03-editor: Editor de despesa: modo no Início, seleção rápida, quantidades, total e rascunho

Fonte: §2, §4.1, §4.2 e §10 (etapa 3).

## Âmbito

- Terceira opção **Despesas rápidas** no Início e `DespesasHomeScreen` (categorias, favoritos, Nova despesa, Histórico, Gerir catálogo, continuar rascunho).
- `ExpenseEditorScreen`: estabelecimento opcional com sugestões, data/hora editáveis, produtos com quantidade (−/+), preço unitário (teclado monetário reutilizado), subtotal e total sempre visível; pesquisa e adição de produtos; criação de produto personalizado sem sair do fluxo.
- Rascunho local persistente (DataStore, JSON) enquanto a edição estiver em curso; limpo ao guardar ou cancelar.
- `CreateExpenseUseCase`, `CalculateExpenseTotalUseCase` e validação (preço ≥ 0, quantidade ≥ 1, pelo menos um item).

## Saída verificável

Registar 1 café + 1 bola de Berlim (0,85 € + 1,50 € = 2,35 €), alterar quantidades e guardar sem estabelecimento.
