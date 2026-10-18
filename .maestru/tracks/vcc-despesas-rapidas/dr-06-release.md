---
maestru: "0.4"
type: work-item
id: dr-06-release
title: "Testes de aceitação, acessibilidade, documentação e APK release 0.4.0 assinado"
created: 2026-10-18
owner: developer
priority: medium
status: done
completed: 2026-10-18
specs:
  - dr-06-release-spec
track: vcc-despesas-rapidas
blocked-by:
  - dr-04-history-repeat
  - dr-05-price-suggestions
---

# dr-06-release: Testes de aceitação, acessibilidade, documentação e APK release 0.4.0 assinado

Fonte: §10 (etapa 6) e §11.

## Âmbito

- Testes de aceitação do §11 (cálculo 2,35 €, produto personalizado sem perder o rascunho, sem estabelecimento, preço habitual sem alterar despesas antigas, repetição, persistência, seed sem duplicados, valores inválidos).
- Rótulos de acessibilidade e formatação pt-PT em euros.
- README e documentação Maestru atualizados.
- Versão 0.4.0 (`versionCode` 6) e APK release assinado `VCCSmartShopcart-0.4.0-release.apk`, verificado com `apksigner`.

## Saída verificável

APK assinado e versionado em `app\build\outputs\apk\release\`.
