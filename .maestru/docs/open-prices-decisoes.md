---
maestru: "0.4"
type: doc
id: open-prices-decisoes
title: "Decisões da integração Open Prices"
description: "Decisões que concretizam o documento Documents/Integracao_Open_Prices_MVP_Android.md no código existente (OP1–OP14)."
created: 2026-10-18
updated: 2026-10-18
owner: developer
tags: [open-prices, decisoes, arquitetura]
---

# Decisões da integração Open Prices

<!-- maestru:summary -->
Decisões OP1–OP14 que adaptam o documento de integração Open Prices à app existente: contrato confirmado, nomes, validação PT/EUR, unidades, cache, 429 e confirmação explícita do preço.
<!-- /maestru:summary -->

Documento de origem: [`Documents/Integracao_Open_Prices_MVP_Android.md`](../../Documents/Integracao_Open_Prices_MVP_Android.md).
Contrato confirmado em 2026-10-18 contra `https://prices.openfoodfacts.org/api/schema` e respostas reais.
Estas decisões prevalecem sobre os exemplos do documento.

## Princípios

| # | Decisão |
|---|---|
| OP1 | Open Prices é uma fonte **opcional** de preços de referência. O preço do carrinho é sempre confirmado pelo utilizador (botão "Adicionar"); a falta de rede ou de preços nunca bloqueia a compra. |
| OP2 | Stack harmonizada com a app: Retrofit 3 + OkHttp 5 + **kotlinx.serialization** (sem Gson). `User-Agent` igual ao da Open Food Facts (D0). Package `pt.vcc.smartshopcart`. |
| OP3 | O modelo de domínio chama-se `ReferencePrice` (já existe `ProductPrice` para o histórico local) e o repositório `ReferencePriceRepository`. |

## Contrato e validação

| # | Decisão |
|---|---|
| OP4 | `GET /api/v1/prices?product_code=…&currency=EUR&type=PRODUCT&order_by=-date&page=…&size=100`. A resposta tem `items/page/pages/size/total` e a `location` vem embebida; `GET /api/v1/locations/{id}` devolve a mesma `Location` sem wrapper e só é usado quando a localização falta. |
| OP5 | A API não filtra por país: Portugal é validado **só** por `location.osm_address_country_code == "PT"`. Localizações `ONLINE` ou sem país são descartadas; `EUR` sozinho nunca implica Portugal. |
| OP6 | Valores nulos, ≤ 0, não finitos, datas inválidas ou futuras e registos com `duplicate_of` são ignorados. Euros → cêntimos com `BigDecimal` `HALF_UP`. EAN sempre `String`. |
| OP7 | `price_per`: `null`/vazio/`UNIT` → `PriceBasis.UNIT` (por embalagem); `KILOGRAM` → `PriceBasis.KILOGRAM`; valores desconhecidos são descartados. Preços por kg nunca são comparados com preços por unidade e, ao serem usados, mudam a unidade do rascunho para kg. |
| OP8 | Observações com mais de **90 dias** são mostradas como "preço histórico". Promoções mostram "Promoção registada" com o tipo (`discount_type`) e a data, nunca como promoção atual. |

## Cache e sincronização

| # | Decisão |
|---|---|
| OP9 | Preços externos (`external_prices`) ficam separados do histórico local (`product_price_history`). O estado de cada consulta fica em `external_price_sync` (incluindo consultas vazias). Base de dados passa à versão 2 com `MIGRATION_1_2` explícita. |
| OP10 | Cache de **24 h** por EAN, também para resultados vazios. Cache mais antiga é mostrada enquanto se atualiza; sem rede usa-se apenas Room. Cada atualização substitui, numa transação, os preços e o estado desse EAN. Registos não consultados há 90 dias são apagados. |
| OP11 | Paginação limitada a **3 páginas × 100** (mais recentes primeiro); se `total` exceder o obtido, o estado fica `incomplete` e a interface avisa. |
| OP12 | HTTP 429: respeita `Retry-After` (segundos ou data HTTP), ou 60 s por omissão; até lá não há chamadas para esse EAN. Erros de rede preservam a cache e são indicados discretamente. |
| OP13 | Atualizações do mesmo EAN são serializadas (`Mutex` por código) e a política de 24 h evita chamadas repetidas quando o scanner relê o código. Códigos internos de loja (D7) nunca são consultados. |

## Interface

| # | Decisão |
|---|---|
| OP14 | No passo de confirmação do scanner, com código público, aparece "Preços em Portugal", que abre uma folha inferior (`ProductPricesScreen`). "Usar como referência" apenas preenche o preço do rascunho; o utilizador tem de confirmar com "Adicionar". Se o preço confirmado for o de referência sem alteração, a fonte é `PriceSource.OPEN_PRICES`. O ecrã "Sobre" inclui a atribuição Open Prices (ODbL). |
