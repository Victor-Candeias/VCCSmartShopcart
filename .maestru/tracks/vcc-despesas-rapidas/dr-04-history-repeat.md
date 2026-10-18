---
maestru: "0.4"
type: work-item
id: dr-04-history-repeat
title: "Histórico de despesas: ver, editar, eliminar e repetir"
created: 2026-10-18
owner: developer
priority: high
status: done
completed: 2026-10-18
specs:
  - dr-04-history-repeat-spec
track: vcc-despesas-rapidas
blocked-by:
  - dr-03-editor
---

# dr-04-history-repeat: Histórico de despesas: ver, editar, eliminar e repetir

Fonte: §4.4 e §10 (etapa 4).

## Âmbito

- `ExpenseHistoryScreen`: despesas por data, estabelecimento e categorias, total por registo e filtro por categoria.
- `ExpenseDetailScreen` com ações Editar, Eliminar (com confirmação) e Repetir despesa.
- `UpdateExpenseUseCase` (substitui itens e recalcula o total na mesma transação), `DeleteExpenseUseCase` e `RepeatExpenseUseCase` (novo rascunho com data atual e preços editáveis).

## Saída verificável

Registos podem ser consultados, editados, eliminados e repetidos; testes dos casos de utilização a passar.
