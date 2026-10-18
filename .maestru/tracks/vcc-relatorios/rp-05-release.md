---
maestru: "0.4"
type: work-item
id: rp-05-release
title: "Testes de aceitação, acessibilidade, documentação e APK release 0.5.0 assinado"
created: 2026-10-18
owner: developer
priority: medium
specs:
  - rp-05-release-spec
status: backlog
track: vcc-relatorios
blocked-by:
  - rp-02-dashboard
  - rp-04-pdf-robustness
---

# rp-05-release: Testes de aceitação, acessibilidade, documentação e APK release 0.5.0 assinado

Fonte: §15 e §17.

## Âmbito

- Testes de aceitação do MVP (Room real, sem rede): total do dashboard = soma das transações concluídas, filtros, relatórios vazios, comparação de totais entre CSV/JSON/PDF.
- Revisão de acessibilidade (descrições dos gráficos, tabelas alternativas, formatação pt-PT).
- README e `.maestru/docs/relatorios-decisoes.md`.
- Versão 0.5.0 (`versionCode` 7) e APK release assinado `VCCSmartShopcart-0.5.0-release.apk`, verificado com `apksigner`.

## Saída verificável

APK assinado e versionado em `app\build\outputs\apk\release\`.
