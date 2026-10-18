---
maestru: "0.4"
type: work-spec
id: dr-06-release-spec
title: "Especificação: Testes de aceitação, acessibilidade, documentação e APK release 0.4.0 assinado"
template: implementation-plan-v1
work-item: vcc-despesas-rapidas/dr-06-release
owner: developer
created: 2026-10-18
---

# Especificação: Testes de aceitação, acessibilidade, documentação e APK release 0.4.0 assinado

## Overview

Fecho do MVP de Despesas Rápidas (§10 etapa 6, §11): cobertura dos critérios de aceitação, revisão de acessibilidade, documentação e APK release assinado e versionado.

## Implementation

- `ExpenseAcceptanceTest` (Robolectric + Room em memória) com os critérios do §11 ainda não cobertos.
- Revisão de `contentDescription`/`semantics` nos controlos −/+, favoritos, ordenação e preço; formatação `MoneyFormatter` pt-PT.
- README (modos, Despesas Rápidas) e `.maestru/docs` (decisões).
- `versionName` 0.4.0, `versionCode` 6; `gradlew testDebugUnitTest assembleRelease` com JDK 17; `apksigner verify`.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `test/.../ExpenseAcceptanceTest.kt` | create | Critérios do §11 |
| `app/build.gradle.kts` | modify | Versão |
| `README.md`, `.maestru/docs/despesas-rapidas-decisoes.md` | modify/create | Documentação |
