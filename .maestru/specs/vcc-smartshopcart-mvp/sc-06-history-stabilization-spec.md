---
maestru: "0.4"
type: work-spec
id: sc-06-history-stabilization-spec
title: "Especificação do histórico, definições e estabilização"
template: implementation-plan-v1
work-item: vcc-smartshopcart-mvp/sc-06-history-stabilization
owner: developer
created: 2026-10-18
---

# Especificação do histórico, definições e estabilização

## Overview

Fecha o MVP (§9–§11): ecrã Histórico com as compras concluídas (total, itens, supermercado, lista),
detalhe só de leitura (D14) com eliminação da compra (D23) e histórico de preços do produto por
supermercado (§1, D17). As Definições ganham o alerta de orçamento, privacidade e "Sobre" com a
atribuição Open Food Facts/ODbL (D24) e licenças. Revisão de acessibilidade (descrições, alvos
grandes, texto ampliado), mensagens de erro de gravação e APK de release assinado com uma
keystore local fora do Git.

## Implementation

### Phase 1: Domínio e dados (TDD)

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/.../domain/usecase/HistoryUseCasesTest.kt` | Compra concluída aparece com total e itens; eliminar só compras concluídas e manter o histórico de preços; resumo por supermercado (último, mínimo, máximo) |
| Modify | `domain/repository/ProductRepository.kt`, `data/repository/RoomProductRepository.kt` | `observePriceHistory(barcode)` |
| Create | `domain/usecase/HistoryUseCases.kt` | `DeletePurchaseUseCase`, `PriceHistorySummary.byStore` |

- [x] Testes de histórico passam

### Phase 2: Interface

| Action | File | Details |
|---|---|---|
| Create | `feature/history/HistoryViewModel.kt`, `HistoryDetailViewModel.kt`, `HistoryDetailScreen.kt` | Lista, detalhe, eliminação com confirmação, histórico de preços do produto |
| Modify | `feature/history/HistoryScreen.kt`, `core/navigation/*` | `HistoryDetailRoute(sessionId)` |
| Modify | `feature/settings/SettingsScreen.kt` | Alerta de orçamento, privacidade, Sobre (ODbL), licenças |
| Modify | `feature/scanner/ScannerViewModel.kt` | Falha de armazenamento ao gravar mostra mensagem e mantém o rascunho |

- [x] Fluxos verificados no emulador, incluindo texto ampliado

### Phase 3: Release

| Action | File | Details |
|---|---|---|
| Modify | `app/build.gradle.kts` | `signingConfigs.release` lido de `keystore.properties` (ignorado pelo Git) |
| Modify | `README.md` | Como gerar a keystore e o APK assinado |

- [x] `assembleRelease` assinado (`apksigner verify`) instalado e critérios §11.3 verificados

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/smartshopcart/domain/*` | Create/Modify | Casos de utilização de histórico |
| `app/src/main/java/pt/vcc/smartshopcart/data/repository/*` | Modify | Histórico de preços |
| `app/src/main/java/pt/vcc/smartshopcart/feature/history/*` | Create/Modify | Histórico e detalhe |
| `app/src/main/java/pt/vcc/smartshopcart/feature/settings/*` | Modify | Definições e Sobre |
| `app/src/main/java/pt/vcc/smartshopcart/core/navigation/*` | Modify | Rota do detalhe |
| `app/src/main/res/*` | Modify | Textos |
| `app/build.gradle.kts`, `README.md` | Modify | Assinatura local |
| `app/src/test/java/pt/vcc/smartshopcart/*` | Create | Testes |