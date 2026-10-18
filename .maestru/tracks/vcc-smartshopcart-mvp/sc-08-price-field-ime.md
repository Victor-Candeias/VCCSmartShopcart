---
maestru: "0.4"
type: work-item
id: sc-08-price-field-ime
title: Campo de preço escondido pelo teclado no ecrã Ler produto e preço
created: 2026-10-18
priority: high
completed: 2026-10-18
status: done
track: vcc-smartshopcart-mvp
---

# sc-08-price-field-ime: Campo de preço escondido pelo teclado no ecrã Ler produto e preço

## Problema

No ecrã de confirmação do scanner ("Ler produto e preço"), ao tocar em "Preço unitário" o teclado numérico tapava o campo e não se via o valor inserido (ver `documents/Screenshot_20261018-221032.png`).

## Causa

A `MainActivity` usa `enableEdgeToEdge()`; nesse modo o `adjustResize` do manifesto deixa de encolher a janela e o `Scaffold` só aplica os insets das barras de sistema, não os do teclado (IME). A coluna com scroll mantinha a altura total e o campo focado ficava por baixo do teclado.

## Correção

`ConfirmContent` em `feature/scanner/ScannerScreen.kt` aplica `Modifier.imePadding()` antes do `verticalScroll`, reduzindo a área visível quando o teclado abre; o Compose mantém o campo focado visível dentro do scroll.

## Release

Versão 0.3.1 (`versionCode` 5), APK release assinado `VCCSmartShopcart-0.3.1-release.apk`.
