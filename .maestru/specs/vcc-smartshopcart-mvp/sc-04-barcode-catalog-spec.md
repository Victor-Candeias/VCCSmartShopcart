---
maestru: "0.4"
type: work-spec
id: sc-04-barcode-catalog-spec
title: "Especificação do scanner de código de barras e catálogo"
template: implementation-plan-v1
work-item: vcc-smartshopcart-mvp/sc-04-barcode-catalog
owner: developer
created: 2026-10-18
---

# Especificação do scanner de código de barras e catálogo

## Overview

Identifica produtos por código de barras (§5, §6): leitura contínua com CameraX + ML Kit
(modelo incluído, D3), validação EAN-8/EAN-13/UPC-A (D21), códigos internos de loja nunca
enviados para a Open Food Facts (D7), catálogo Room primeiro e API v3 só com rede, falhas não
bloqueantes. A confirmação é um passo dentro do ecrã do scanner (D25) com sugestão do último
preço no mesmo supermercado (D17). Leituras repetidas do mesmo código em menos de 3 s são
ignoradas (D22). O OCR de preços fica para `sc-05-ocr-capture`.

## Implementation

### Phase 1: Domínio, dados e rede (TDD)

#### Step 1.1: Testes

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/.../core/barcode/BarcodesTest.kt` | Dígito de controlo, UPC-A → EAN-13, códigos internos 20–29, entradas inválidas |
| Create | `app/src/test/.../data/remote/OpenFoodFactsDataSourceTest.kt` | MockWebServer: produto encontrado (mapeamento e `User-Agent`), 404, resposta inválida/500 |
| Create | `app/src/test/.../domain/usecase/ProductUseCasesTest.kt` | Room primeiro; sem rede não chama a API; código interno não chama a API; produto remoto guardado; sugestão de preço por loja; adicionar item lido cria produto manual, fonte `HISTORY` só com preço sugerido inalterado, liga item planeado |

#### Step 1.2: Implementação

| Action | File | Details |
|---|---|---|
| Create | `core/barcode/Barcodes.kt` | Normalização e validação |
| Create | `core/network/*` | `NetworkMonitor` (ConnectivityManager) |
| Create | `data/remote/*` | Retrofit `OpenFoodFactsApi`, DTOs, `OpenFoodFactsDataSource` com `User-Agent` (D0), timeouts e uma nova tentativa |
| Create | `domain/repository/ProductRepository.kt`, `data/repository/RoomProductRepository.kt` | Catálogo local e último preço |
| Create | `domain/usecase/ProductUseCases.kt` | `ResolveProductUseCase`, `AddScannedItemUseCase` |
| Create | `core/di/NetworkModule.kt` | OkHttp, Retrofit e bindings |
| Modify | `app/build.gradle.kts`, `AndroidManifest.xml` | CameraX, ML Kit, Retrofit; permissões `CAMERA`, `INTERNET`, `ACCESS_NETWORK_STATE` |

- [x] Testes de barcode, Open Food Facts e casos de utilização passam

### Phase 2: Scanner e confirmação

| Action | File | Details |
|---|---|---|
| Create | `feature/scanner/BarcodeAnalyzer.kt` | `ImageAnalysis` com ML Kit, fecha sempre o `ImageProxy`, limita a frequência; `ScanDebouncer` (D22) |
| Create | `app/src/test/.../feature/scanner/ScanDebouncerTest.kt` | Janela de 3 s para o mesmo código |
| Create | `feature/scanner/ScannerViewModel.kt` | Passos Ler → A identificar → Confirmar; rascunho em `SavedStateHandle`; D22 |
| Create | `feature/scanner/ScannerScreen.kt` | Permissão pedida ao abrir, pré-visualização, vários códigos → pedir novo enquadramento, introdução manual do código, confirmação |
| Modify | `core/navigation/*`, `feature/calculator/*`, `feature/session/*` | `ScannerRoute(sessionId, listItemId?)`; botões de scanner |
| Modify | `core/ui/components/Dialogs.kt` | Campos reutilizáveis |

- [x] Fluxo no emulador: permissão, pré-visualização, código manual com e sem Internet, confirmação e item no carrinho

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/build.gradle.kts` | Modify | Dependências |
| `app/src/main/AndroidManifest.xml` | Modify | Permissões |
| `app/src/main/java/pt/vcc/smartshopcart/core/barcode/*` | Create | Barcodes |
| `app/src/main/java/pt/vcc/smartshopcart/core/network/*` | Create | Estado da rede |
| `app/src/main/java/pt/vcc/smartshopcart/data/remote/*` | Create | Open Food Facts |
| `app/src/main/java/pt/vcc/smartshopcart/data/repository/RoomProductRepository.kt` | Create | Catálogo local |
| `app/src/main/java/pt/vcc/smartshopcart/domain/*` | Create | Contratos e casos de utilização |
| `app/src/main/java/pt/vcc/smartshopcart/core/di/*` | Create/Modify | DI |
| `app/src/main/java/pt/vcc/smartshopcart/feature/scanner/*` | Create | Scanner |
| `app/src/main/java/pt/vcc/smartshopcart/core/navigation/*` | Modify | Rotas |
| `app/src/main/res/*` | Modify | Textos e ícones |
| `app/src/test/java/pt/vcc/smartshopcart/*` | Create | Testes |