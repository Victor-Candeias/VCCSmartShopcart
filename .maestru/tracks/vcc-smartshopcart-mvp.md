---
maestru: "0.4"
type: work-track
id: vcc-smartshopcart-mvp
title: VCCSmartShopcart — MVP Android
created: 2026-10-18
description: "Entregar o MVP Android offline-first do VCCSmartShopcart: calculadora rápida, listas planeadas, scanner (barcode + OCR), Open Food Facts, histórico e orçamento."
owner: developer
status: active
---

# vcc-smartshopcart-mvp: VCCSmartShopcart — MVP Android

## Summary

<!-- maestru:work-items-list -->
| ID | Title | Status | Created | Owner | Priority | Completed | Template | Blocked By | Spec |
|---|---|---|---|---|---|---|---|---|---|
| sc-01-foundation | Fundação: projeto Android, Hilt, Room, navegação e domínio monetário | done | 2026-10-18 | developer | critical | 2026-10-18 |  |  | [sc-01-foundation](../specs/vcc-smartshopcart-mvp/sc-01-foundation-spec.md) |
| sc-02-calculator | Calculadora rápida: sessões, teclado, linhas, totais e orçamento | done | 2026-10-18 | developer | high | 2026-10-18 |  | sc-01-foundation | [sc-02-calculator](../specs/vcc-smartshopcart-mvp/sc-02-calculator-spec.md) |
| sc-03-lists | Listas planeadas: CRUD, itens e sessão ligada à lista | done | 2026-10-18 | developer | high | 2026-10-18 |  | sc-02-calculator | [sc-03-lists](../specs/vcc-smartshopcart-mvp/sc-03-lists-spec.md) |
| sc-04-barcode-catalog | Scanner de código de barras e catálogo Open Food Facts | backlog | 2026-10-18 | developer | high |  |  | sc-01-foundation |  |
| sc-05-ocr-capture | OCR de preços, parser e captura combinada com confirmação | backlog | 2026-10-18 | developer | critical |  |  | sc-04-barcode-catalog |  |
| sc-06-history-stabilization | Histórico, definições, acessibilidade e APK assinado | backlog | 2026-10-18 | developer | high |  |  | sc-03-lists, sc-05-ocr-capture |  |
<!-- /maestru:work-items-list -->
