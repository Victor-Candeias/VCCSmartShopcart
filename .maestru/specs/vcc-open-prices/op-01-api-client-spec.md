---
maestru: "0.4"
type: work-spec
id: op-01-api-client-spec
title: "Especificação: cliente Open Prices e validação"
template: implementation-plan-v1
work-item: vcc-open-prices/op-01-api-client
owner: developer
created: 2026-10-18
---

# Especificação: cliente Open Prices e validação

## Overview

Cliente de leitura da API Open Prices e regras de validação dos preços portugueses (§2, §4–§8 do
[documento](../../../Documents/Integracao_Open_Prices_MVP_Android.md); decisões OP2–OP7 em
[open-prices-decisoes](../../docs/open-prices-decisoes.md)).

- Contrato confirmado contra `/api/schema` e respostas reais (resposta paginada, `location` embebida,
  `PricePerEnum = UNIT|KILOGRAM`, `DiscountTypeEnum`).
- DTOs `kotlinx.serialization` com valores por omissão (tolerantes a campos ausentes e desconhecidos).
- `OpenPricesApi` devolve `retrofit2.Response` para o repositório poder tratar 404/429/`Retry-After`.
- Mapper puro `OpenPriceDto.toReferencePrice(barcode, location, today)`: devolve `null` em tudo o que não é um
  preço português válido.

## Implementation

### Phase 1: Contrato e cliente

| Action | File | Details |
|---|---|---|
| Create | `data/remote/openprices/OpenPricesDtos.kt` | `OpenPricesPageDto`, `OpenPriceDto`, `OpenPriceLocationDto` |
| Create | `data/remote/openprices/OpenPricesApi.kt` | `GET prices`, `GET locations/{id}` |
| Create | `data/remote/openprices/OpenPricesClient.kt` | OkHttp (`User-Agent`, `Accept`, timeouts) + Retrofit |
| Modify | `core/di/NetworkModule.kt` | `provideOpenPricesApi` |

### Phase 2: Domínio e validação

| Action | File | Details |
|---|---|---|
| Create | `domain/model/ReferencePrice.kt` | `ReferencePrice`, `PriceBasis`, `isHistorical` |
| Create | `data/remote/openprices/OpenPricesMapper.kt` | PT/EUR/PRODUCT, valor, datas, unidade, promoção, duplicados, cêntimos |
| Create | `app/src/test/.../OpenPricesMapperTest.kt`, `OpenPricesApiTest.kt` | Testes |

- [x] Testes unitários passam

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/smartshopcart/data/remote/openprices/*` | Create | Cliente e mapper |
| `app/src/main/java/pt/vcc/smartshopcart/domain/model/ReferencePrice.kt` | Create | Modelo de domínio |
| `app/src/main/java/pt/vcc/smartshopcart/core/di/NetworkModule.kt` | Modify | Injeção da API |
| `app/src/test/java/pt/vcc/smartshopcart/data/remote/openprices/*` | Create | Testes |