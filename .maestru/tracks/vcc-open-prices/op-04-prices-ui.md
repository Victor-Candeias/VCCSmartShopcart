---
maestru: "0.4"
type: work-item
id: op-04-prices-ui
title: Ecrã de preços em Portugal integrado no scanner e confirmação explícita
created: 2026-10-18
owner: developer
priority: high
status: backlog
specs:
  - op-04-prices-ui-spec
track: vcc-open-prices
blocked-by:
  - op-03-repository-sync
---

# op-04-prices-ui: Ecrã de preços em Portugal integrado no scanner e confirmação explícita

Fonte: §11, §12 e §13 do documento de integração.

## Âmbito

- `ProductPricesViewModel` + `ProductPricesScreen` (folha inferior no passo de confirmação do scanner).
- Mostra supermercado, cidade, data, unidade, promoção e origem; etiqueta de preço histórico; aviso de resultados incompletos; estado vazio e erro discreto.
- "Usar como referência" só preenche o campo de preço: o utilizador continua a ter de confirmar com "Adicionar".
- `PriceSource.OPEN_PRICES` quando o preço confirmado é o de referência sem alteração.
- Preços por kg não são misturados com preços por unidade.
- Formatação monetária portuguesa (`MoneyFormatter`).

## Saída verificável

Ler um EAN no scanner, abrir "Preços em Portugal", escolher um preço e confirmá-lo explicitamente.