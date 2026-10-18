---
maestru: "0.4"
type: work-item
id: op-01-api-client
title: "Cliente Open Prices: DTOs, API Retrofit, User-Agent e validação PT/EUR"
created: 2026-10-18
owner: developer
priority: high
status: done
completed: 2026-10-18
specs:
  - op-01-api-client-spec
track: vcc-open-prices
---

# op-01-api-client: Cliente Open Prices: DTOs, API Retrofit, User-Agent e validação PT/EUR

Fonte: [Integracao_Open_Prices_MVP_Android.md](../../../Documents/Integracao_Open_Prices_MVP_Android.md) §2, §4–§8. Decisões em [open-prices-decisoes](../../docs/open-prices-decisoes.md).

## Âmbito

- Contrato confirmado contra o OpenAPI real (`/api/schema`): resposta paginada `items/page/pages/size/total`, `location` embebida, `price_per ∈ {UNIT, KILOGRAM}`, `discount_type` enumerado.
- DTOs `kotlinx.serialization` tolerantes a campos ausentes (harmonizado com a stack existente; sem Gson).
- `OpenPricesApi` (Retrofit) com `GET prices` e `GET locations/{id}`, `order_by=-date`.
- Cliente OkHttp com o `User-Agent` da app (D0), `Accept: application/json` e timeouts.
- Modelo de domínio `ReferencePrice` (nome distinto do `ProductPrice` existente) e `PriceBasis`.
- `OpenPricesMapper`: só `PRODUCT`, `EUR`, país `PT` validado pela localização, valor finito > 0, data válida e não futura; conversão para cêntimos `HALF_UP` via `BigDecimal`.
- Testes: mapper (PT/ES/EUR sem país/datas/valores/unidades/zeros à esquerda) e MockWebServer.

## Saída verificável

`testDebugUnitTest` verde com os testes do cliente e do mapper.