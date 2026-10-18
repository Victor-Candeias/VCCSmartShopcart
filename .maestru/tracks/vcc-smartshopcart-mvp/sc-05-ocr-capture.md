---
maestru: "0.4"
type: work-item
id: sc-05-ocr-capture
title: "OCR de preços, parser e captura combinada com confirmação"
created: 2026-10-18
owner: developer
priority: critical
status: done
completed: 2026-10-18
specs:
  - sc-05-ocr-capture-spec
track: vcc-smartshopcart-mvp
blocked-by:
  - sc-04-barcode-catalog
---

# sc-05-ocr-capture: OCR de preços, parser e captura combinada com confirmação

Sprint 5 do [documento de implementação](../../../Documents/Implementacao_MVP_App_Compras.md) (§5.1–§5.4, §8).

## Âmbito

- ML Kit Text Recognition (Latin, incluído na app) sobre fotografia `ImageCapture`.
- `PriceCandidateParser` com formatos portugueses e classificação (venda, antigo, /kg, /L, desconto).
- `ProductCaptureDraft` + redutor para fotografia única e capturas sucessivas em qualquer ordem.
- Confirmação obrigatória e `ConfirmCaptureUseCase` transacional (item + `PriceCapture`).

## Saída verificável

Scanner inteligente funcional: barcode e preço na mesma imagem ou em duas capturas, sem duplicar itens.
