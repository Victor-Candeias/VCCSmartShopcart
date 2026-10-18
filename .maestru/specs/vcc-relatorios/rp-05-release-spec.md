---
maestru: "0.4"
type: work-spec
id: rp-05-release-spec
title: "Especificação: Aceitação e release 0.5.0"
template: implementation-plan-v1
work-item: vcc-relatorios/rp-05-release
owner: developer
created: 2026-10-18
---

# Especificação: Aceitação e release 0.5.0

## Overview

Fecho do MVP (§15, §17): testes de aceitação, acessibilidade, documentação e APK release assinado e versionado.

## Implementation

### Phase 1: Aceitação

| Action | File | Details |
|--------|------|---------|
| Create | `test/.../ReportsAcceptanceTest.kt` | Room real: total = soma das transações concluídas; filtros; vazio; CSV/JSON/PDF com o mesmo total e nº de transações; erro de escrita |

### Phase 2: Documentação e release

| Action | File | Details |
|--------|------|---------|
| Create | `.maestru/docs/relatorios-decisoes.md` | Decisões RP1–RPn |
| Modify | `README.md` | Módulo Relatórios |
| Modify | `app/build.gradle.kts` | 0.5.0 / `versionCode` 7 |
| Run | `gradlew testDebugUnitTest assembleRelease` + `apksigner verify` | JDK 17 |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `test/.../ReportsAcceptanceTest.kt` | Create | Critérios do §17 |
| `README.md`, `.maestru/docs/relatorios-decisoes.md` | Modify/Create | Documentação |
| `app/build.gradle.kts` | Modify | Versão |

## Verificação

- [x] `ReportsAcceptanceTest` (4 cenários) e suite completa (200 testes) verdes
- [x] `VCCSmartShopcart-0.5.0-release.apk` (versionCode 7) gerado e verificado com `apksigner` (esquema v2, CN=VCCSmartShopcart)
