---
maestru: "0.4"
type: work-item
id: dr-05-price-suggestions
title: "Sugestão de preços por estabelecimento, último consumo e catálogo"
created: 2026-10-18
owner: developer
priority: medium
status: done
completed: 2026-10-18
specs:
  - dr-05-price-suggestions-spec
track: vcc-despesas-rapidas
blocked-by:
  - dr-03-editor
---

# dr-05-price-suggestions: Sugestão de preços por estabelecimento, último consumo e catálogo

Fonte: §7 e §10 (etapa 5).

## Âmbito

- `SuggestProductPriceUseCase` com a prioridade: último preço no mesmo estabelecimento → último preço pago → preço habitual do catálogo → vazio.
- Consultas `ExpenseItem` ⨝ `Expense` ordenadas por `occurredAt`.
- Integração no editor (ao adicionar produto e ao mudar de estabelecimento, sem sobrescrever preços já confirmados) e na repetição; indicação visual da origem da sugestão.

## Saída verificável

Testes da prioridade por estabelecimento e último consumo a passar.
