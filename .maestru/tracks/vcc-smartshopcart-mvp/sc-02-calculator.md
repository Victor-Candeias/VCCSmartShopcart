---
maestru: "0.4"
type: work-item
id: sc-02-calculator
title: "Calculadora rápida: sessões, teclado, linhas, totais e orçamento"
created: 2026-10-18
owner: developer
priority: high
status: backlog
track: vcc-smartshopcart-mvp
blocked-by:
  - sc-01-foundation
---

# sc-02-calculator: Calculadora rápida: sessões, teclado, linhas, totais e orçamento

Sprint 2 do [documento de implementação](../../../Documents/Implementacao_MVP_App_Compras.md) (§4.1, §7.3).

## Âmbito

- Iniciar, retomar, concluir e cancelar sessões `CALCULATOR` (no máximo uma `ACTIVE`).
- Teclado numérico com vírgula decimal, quantidade, unidade, desconto por linha.
- Editar, remover e desfazer linhas; total e orçamento recalculados.
- Seleção de supermercado ao iniciar a sessão.

## Saída verificável

Compras rápidas offline: 10 preços manuais somados corretamente e sessão retomada após reabrir a app.
