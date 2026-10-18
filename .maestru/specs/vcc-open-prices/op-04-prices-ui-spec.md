---
maestru: "0.4"
type: work-spec
id: op-04-prices-ui-spec
title: "Especificação: ecrã de preços em Portugal no scanner"
template: implementation-plan-v1
work-item: vcc-open-prices/op-04-prices-ui
owner: developer
created: 2026-10-18
---

# Especificação: ecrã de preços em Portugal no scanner

## Overview

No passo de confirmação do scanner, um botão "Preços em Portugal" (apenas para EAN/UPC válidos, nunca códigos internos de loja) abre uma bottom sheet com os preços de referência Open Prices. A sheet mostra primeiro a cache local e atualiza em segundo plano; sem rede, com 429 ou erro mantém a cache e mostra um aviso discreto. O utilizador pode escolher "Usar como referência", o que preenche o preço no rascunho; a captura só é gravada após confirmação explícita, com origem `OPEN_PRICES` se o valor não for alterado (caso contrário `MANUAL`).

## Implementation

### Phase 1 — Domínio
1. `PriceSource.OPEN_PRICES` (persistido por nome, sem migração).
2. `CaptureConfirmation.referencePriceCents` e precedência OCR → HISTORY → OPEN_PRICES → MANUAL em `ConfirmCaptureUseCase`.

### Phase 2 — Scanner
1. `ScanDraft.referencePriceCents` + `CaptureDraftReducer.withReferencePrice` (ajusta unidade UN/KG pela base do preço).
2. `ScannerViewModel.onUseReferencePrice` e passagem do valor de referência na confirmação.
3. `ScannerScreen.ConfirmContent`: botão, dica "valor de referência" e abertura da sheet.

### Phase 3 — Ecrã de preços
1. `ProductPricesViewModel`: observa a cache, `refresh(force=false)` ao abrir, `retry()` força; evita pedidos duplicados para o mesmo EAN em curso; avisos OFFLINE/FAILED/RATE_LIMITED.
2. `ProductPricesSheet`/`ProductPricesScreen`: secções "Por embalagem" e "Por quilograma", loja · cidade, data, histórico, promoção/tipo, preço sem desconto, data de atualização, aviso de resultados incompletos e atribuição ODbL.

### Phase 4 — Testes
- `ProductPricesViewModelTest` (3), `CaptureDraftReducerTest` (+1), `ProductUseCasesTest` (+1).

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/smartshopcart/domain/model/Enums.kt` | Modify | `PriceSource.OPEN_PRICES` |
| `app/src/main/java/pt/vcc/smartshopcart/domain/usecase/ProductUseCases.kt` | Modify | Origem da captura com valor de referência |
| `app/src/main/java/pt/vcc/smartshopcart/feature/scanner/CaptureDraftReducer.kt` | Modify | Aplicar preço de referência ao rascunho |
| `app/src/main/java/pt/vcc/smartshopcart/feature/scanner/ScannerViewModel.kt` | Modify | Ação "usar referência" |
| `app/src/main/java/pt/vcc/smartshopcart/feature/scanner/ScannerScreen.kt` | Modify | Botão e sheet de preços |
| `app/src/main/java/pt/vcc/smartshopcart/feature/prices/ProductPricesViewModel.kt` | Create | Estado do ecrã de preços |
| `app/src/main/java/pt/vcc/smartshopcart/feature/prices/ProductPricesScreen.kt` | Create | UI Compose da sheet |
| `app/src/main/java/pt/vcc/smartshopcart/core/common/DateTimeText.kt` | Modify | Formatação de `LocalDate` |
| `app/src/main/res/values/strings.xml` | Modify | Textos `prices_*` e `scanner_reference_*` |
| `app/src/test/java/pt/vcc/smartshopcart/feature/prices/ProductPricesViewModelTest.kt` | Create | Testes do ViewModel |
| `app/src/test/java/pt/vcc/smartshopcart/feature/scanner/CaptureDraftReducerTest.kt` | Modify | Teste do preço de referência |
| `app/src/test/java/pt/vcc/smartshopcart/domain/usecase/ProductUseCasesTest.kt` | Modify | Teste da origem OPEN_PRICES |
