---
maestru: "0.4"
type: work-item
id: sc-03-lists
title: "Listas planeadas: CRUD, itens e sessão ligada à lista"
created: 2026-10-18
owner: developer
priority: high
status: backlog
track: vcc-smartshopcart-mvp
blocked-by:
  - sc-02-calculator
---

# sc-03-lists: Listas planeadas: CRUD, itens e sessão ligada à lista

Sprint 3 do [documento de implementação](../../../Documents/Implementacao_MVP_App_Compras.md) (§4.2).

## Âmbito

- Criar, editar, duplicar e arquivar listas com orçamento opcional.
- Itens planeados com quantidade, unidade e preço estimado.
- Sessão `PLANNED` ligada à lista: progresso, itens ignorados (`SKIPPED`) e extras.
- Orçamento copiado da lista no início da sessão.

## Saída verificável

Lista completa funcional: criar lista, iniciar sessão, registar preços e concluir.
