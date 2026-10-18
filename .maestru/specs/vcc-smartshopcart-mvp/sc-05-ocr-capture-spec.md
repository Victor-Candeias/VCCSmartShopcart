---
maestru: "0.4"
type: work-spec
id: sc-05-ocr-capture-spec
title: "Especificação do OCR de preços e da captura combinada"
template: implementation-plan-v1
work-item: vcc-smartshopcart-mvp/sc-05-ocr-capture
owner: developer
created: 2026-10-18
---

# Especificação do OCR de preços e da captura combinada

## Overview

Acrescenta ao scanner a fotografia (`ImageCapture`) analisada em simultâneo pelo leitor de códigos
e pelo ML Kit Text Recognition (latino, incluído na app, D3) — estratégia A de §5.1 — e capturas
sucessivas em qualquer ordem que alimentam um único rascunho (estratégia B). O
`PriceCandidateParser` produz `PriceCandidate(cents, kind, confidence, rawText)` (D8) com os
formatos de D26; a interface mostra todos os candidatos, destaca a sugestão e exige confirmação.
`ConfirmCaptureUseCase` cria o item e regista a `PriceCapture` na mesma transação. As fotografias
nunca são guardadas (§10).

## Implementation

### Phase 1: Parser, rascunho e confirmação (TDD)

#### Step 1.1: Testes

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/.../core/ocr/PriceCandidateParserTest.kt` | Formatos D26; `1,49 €` vs `1,49 €/kg`; `€/L`; antes/promo; descontos e percentagens; sem confiança → sem sugestão |
| Create | `app/src/test/.../feature/scanner/CaptureDraftReducerTest.kt` | Barcode→preço, preço→barcode e ambos na mesma fotografia geram um só rascunho; seleção de candidatos /kg muda a unidade |
| Modify | `app/src/test/.../domain/usecase/ProductUseCasesTest.kt` | `ConfirmCaptureUseCase`: item + `PriceCapture` na mesma transação (falha anula tudo); fonte `OCR`/`HISTORY`/`MANUAL`; sem código não cria produto; sessão inativa não grava nada |

#### Step 1.2: Implementação

| Action | File | Details |
|---|---|---|
| Create | `domain/model/PriceCandidate.kt` | `PriceKind`, `PriceCandidate`, `PriceCapture` |
| Create | `core/ocr/PriceCandidateParser.kt` | Parser puro sobre linhas de texto com altura relativa |
| Create | `domain/repository/PriceCaptureRepository.kt`, `domain/repository/TransactionRunner.kt` | Contratos |
| Create | `data/repository/RoomPriceCaptureRepository.kt`, `data/repository/RoomTransactionRunner.kt` | Candidatos em JSON |
| Modify | `domain/usecase/ProductUseCases.kt` | `AddScannedItemUseCase` → `ConfirmCaptureUseCase` (barcode opcional, dados OCR) |
| Create | `feature/scanner/CaptureDraftReducer.kt` | Junta produto e preço no mesmo `ScanDraft` |

- [x] Testes do parser, do redutor e da confirmação passam

### Phase 2: Fotografia e interface

| Action | File | Details |
|---|---|---|
| Modify | `app/build.gradle.kts` | `com.google.mlkit:text-recognition` |
| Create | `feature/scanner/PhotoRecognizer.kt` | Barcode + texto sobre o mesmo `InputImage` |
| Modify | `feature/scanner/ScannerViewModel.kt`, `ScannerScreen.kt` | Botão Fotografar, estado da captura parcial, candidatos de preço, “Ler código”/“Fotografar preço” na confirmação |
| Create | `app/src/androidTest/.../feature/scanner/PhotoRecognizerTest.kt` | Etiqueta sintética (EAN-13 + preços) reconhecida no dispositivo |

- [x] Teste instrumentado no emulador e fluxo de fotografia na interface

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/build.gradle.kts` | Modify | ML Kit Text Recognition |
| `app/src/main/java/pt/vcc/smartshopcart/core/ocr/*` | Create | Parser |
| `app/src/main/java/pt/vcc/smartshopcart/domain/*` | Create/Modify | Modelo, contratos e casos de utilização |
| `app/src/main/java/pt/vcc/smartshopcart/data/repository/*` | Create | Persistência das capturas |
| `app/src/main/java/pt/vcc/smartshopcart/core/di/*` | Modify | Bindings |
| `app/src/main/java/pt/vcc/smartshopcart/feature/scanner/*` | Create/Modify | Fotografia, rascunho e confirmação |
| `app/src/main/res/*` | Modify | Textos |
| `app/src/test/java/pt/vcc/smartshopcart/*`, `app/src/androidTest/java/pt/vcc/smartshopcart/*` | Create | Testes |