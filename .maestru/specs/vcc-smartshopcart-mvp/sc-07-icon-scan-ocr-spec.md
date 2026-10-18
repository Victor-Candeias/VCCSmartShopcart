---
maestru: "0.4"
type: work-spec
id: sc-07-icon-scan-ocr-spec
title: "Especificação: ícone, botão Scan e OCR de nome e preço"
template: implementation-plan-v1
work-item: vcc-smartshopcart-mvp/sc-07-icon-scan-ocr
owner: developer
created: 2026-10-18
---

# Especificação: ícone, botão Scan e OCR de nome e preço

## Overview

Pedido do utilizador após testes no telemóvel com a versão 0.1.0:

1. Ícone da app passa a ser `Documents/VCCShopCart.png` (ícone adaptativo).
2. O botão de scan na barra de topo da compra (calculadora e compra planeada) mostra o ícone de código
   de barras **e** o texto “Scan” (`Documents/MudarScanButton.jpg`).
3. Sem nome do produto, a confirmação permite **fotografar o nome** e preencher a caixa de texto.
4. A fotografia do preço reconhecia mal os valores: o leitor contínuo de códigos de barras e a leitura de
   códigos na fotografia continuavam ativos e interferiam (vários códigos → erro, resolução da fotografia
   limitada pelo `ImageAnalysis`). Ao fotografar o preço, o botão passa de “Introduzir código” para
   “Introduzir preço” e aceita valores decimais.

Decisões:

- Novo `CaptureTarget` (`PRODUCT`, `PRICE`, `NAME`). Em `PRICE`/`NAME` não há `ImageAnalysis`, a
  fotografia usa `CAPTURE_MODE_MAXIMIZE_QUALITY`, só lê texto (sem códigos) e só dentro da moldura
  (`FrameCrop`, com 30 % de folga). `PRODUCT` mantém o comportamento de sc-05.
- `PriceCandidateParser`: junta euros grandes e cêntimos em expoente lidos em linhas separadas (posição das
  caixas), lê `149€` no maior texto como 1,49, aceita `1, 49` e corrige `O`→`0`, `I`/`l`→`1`, `E`/`C`→`€`.
- `ProductNameExtractor`: candidatos a nome por tamanho do texto, sem preços/códigos/pesos; maiúsculas
  passam a frase; o primeiro preenche o nome e os restantes aparecem como chips.
- Preço manual (`ManualPriceDialog`) usa `MoneyParser` (`1,49`, `1.49`, `2,5`).
- Versão 0.2.0 (`versionCode` 2).

## Implementation

### Phase 1: OCR e rascunho (TDD)

| Action | File | Details |
|---|---|---|
| Modify | `core/ocr/PriceCandidateParser.kt` | `OcrLine` com caixa; expoente; `149€`; trocas do OCR |
| Create | `core/ocr/ProductNameExtractor.kt` | Candidatos a nome |
| Modify | `feature/scanner/CaptureDraftReducer.kt` | `nameCandidates`, `withNames`, `withManualPrice` |
| Create | `feature/scanner/FrameCrop.kt` | Zona da moldura na imagem do sensor (com rotação) |
| Create/Modify | `app/src/test/.../PriceCandidateParserTest.kt`, `ProductNameExtractorTest.kt`, `CaptureDraftReducerTest.kt`, `FrameCropTest.kt` | Testes |

- [x] Testes unitários passam

### Phase 2: Câmara e interface

| Action | File | Details |
|---|---|---|
| Modify | `feature/scanner/PhotoRecognizer.kt` | `readBarcodes`, recorte da moldura fora da thread principal |
| Modify | `feature/scanner/ScannerViewModel.kt` | `CaptureTarget`, `onCapturePrice/Name/Code`, `onManualPrice` |
| Modify | `feature/scanner/ScannerScreen.kt` | Câmara sem leitor de códigos em PRICE/NAME, “Introduzir preço”, “Fotografar nome”, chips de nomes |
| Modify | `core/ui/components/Dialogs.kt` | `NameField` com `trailingIcon` |
| Modify | `feature/calculator/CalculatorScreen.kt`, `feature/session/PlannedSessionScreen.kt` | Botão “Scan” com ícone e texto |
| Create | `res/mipmap-*/ic_launcher_foreground.png`, `ic_launcher_background.png` | Ícone a partir de `Documents/VCCShopCart.png` |
| Modify | `res/mipmap-anydpi/ic_launcher.xml` | Camadas PNG; monocromático = vetor antigo |

- [x] APK release assinado 0.2.0

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/build.gradle.kts` | Modify | Versão 0.2.0 |
| `app/src/main/java/pt/vcc/smartshopcart/core/ocr/*` | Create/Modify | Parser de preços e nomes |
| `app/src/main/java/pt/vcc/smartshopcart/feature/scanner/*` | Create/Modify | Modos de captura, recorte, interface |
| `app/src/main/java/pt/vcc/smartshopcart/feature/calculator/CalculatorScreen.kt` | Modify | Botão Scan |
| `app/src/main/java/pt/vcc/smartshopcart/feature/session/PlannedSessionScreen.kt` | Modify | Botão Scan |
| `app/src/main/res/*` | Create/Modify | Ícone, textos, ícone de câmara |
| `app/src/test/java/pt/vcc/smartshopcart/*` | Create/Modify | Testes |