---
maestru: "0.4"
type: work-item
id: sc-07-icon-scan-ocr
title: "Ícone da app, botão Scan com texto e OCR de nome e preço"
created: 2026-10-18
owner: developer
priority: high
completed: 2026-10-18
status: done
specs:
  - sc-07-icon-scan-ocr-spec
track: vcc-smartshopcart-mvp
blocked-by:
  - sc-06-history-stabilization
---

# sc-07-icon-scan-ocr: Ícone da app, botão Scan com texto e OCR de nome e preço

Melhorias pedidas após testes da versão 0.1.0 no telemóvel.

## Âmbito

- Ícone da app a partir de `Documents/VCCShopCart.png`.
- Botão “Scan” (ícone + texto) na barra de topo da calculadora e da compra planeada (`Documents/MudarScanButton.jpg`).
- Fotografar o nome do produto quando não existe nome e preencher a caixa de texto.
- Fotografia do preço sem leitor de códigos de barras (só texto, na moldura, qualidade máxima) e parser mais
  tolerante (cêntimos em expoente, trocas do OCR).
- Ao fotografar o preço, “Introduzir código” passa a “Introduzir preço”, com valores decimais.

## Saída verificável

APK release assinado 0.2.0 com o novo ícone; preço e nome fotografados preenchem o rascunho; testes unitários passam.