---
maestru: "0.4"
type: work-spec
id: op-05-release-spec
title: "Especificação: atribuição, documentação e release 0.3.0"
template: implementation-plan-v1
work-item: vcc-open-prices/op-05-release
owner: developer
created: 2026-10-18
---

# Especificação: atribuição, documentação e release 0.3.0

## Overview

Fecho da integração Open Prices: atribuição ODbL visível na app, documentação atualizada e APK release 0.3.0 (`versionCode` 4) assinado com a keystore local, verificado com `apksigner`. Distribuição apenas local.

## Implementation

### Phase 1 — Atribuição
1. Secção "Preços de referência" no ecrã "Sobre" com texto ODbL e ligação para prices.openfoodfacts.org.
2. Resumo do item "Sobre" nas definições passa a mencionar Open Prices.

### Phase 2 — Documentação
1. README: funcionalidade de preços de referência, cache Room v2 e ligações ao documento, decisões e track Maestru.

### Phase 3 — Release
1. `appVersionName = "0.3.0"`, `versionCode = 4`.
2. `:app:testDebugUnitTest` (114 testes) e `:app:assembleRelease` com JDK 17.
3. `apksigner verify` e `aapt2 dump badging` confirmam assinatura e versão.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/smartshopcart/feature/settings/AboutScreen.kt` | Modify | Atribuição Open Prices (ODbL) |
| `app/src/main/res/values/strings.xml` | Modify | Textos `about_prices_*` |
| `README.md` | Modify | Documentação da funcionalidade |
| `app/build.gradle.kts` | Modify | Versão 0.3.0 / versionCode 4 |
