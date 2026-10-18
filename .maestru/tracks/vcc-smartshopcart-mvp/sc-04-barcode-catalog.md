---
maestru: "0.4"
type: work-item
id: sc-04-barcode-catalog
title: Scanner de código de barras e catálogo Open Food Facts
created: 2026-10-18
owner: developer
priority: high
status: done
completed: 2026-10-18
specs:
  - sc-04-barcode-catalog-spec
track: vcc-smartshopcart-mvp
blocked-by:
  - sc-01-foundation
---

# sc-04-barcode-catalog: Scanner de código de barras e catálogo Open Food Facts

Sprint 4 do [documento de implementação](../../../Documents/Implementacao_MVP_App_Compras.md) (§5, §6).

## Âmbito

- CameraX + ML Kit Barcode (modelo incluído na app) para EAN-8, EAN-13 e UPC-A.
- Validação do dígito de controlo e deteção de códigos internos (prefixo 2x).
- Cliente Open Food Facts v3 com `User-Agent` próprio, timeout e cache Room.
- `ProductResolver`: Room primeiro, API só com rede, falhas não bloqueantes.
- Permissão de câmara pedida apenas ao abrir o scanner.

## Saída verificável

Identificação de produtos por código de barras, com e sem Internet.
