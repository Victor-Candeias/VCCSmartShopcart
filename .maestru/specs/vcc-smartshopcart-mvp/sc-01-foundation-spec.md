---
maestru: "0.4"
type: work-spec
id: sc-01-foundation-spec
title: "Especificação da fundação Android"
template: implementation-plan-v1
work-item: vcc-smartshopcart-mvp/sc-01-foundation
owner: developer
created: 2026-10-18
---

# Especificação da fundação Android

## Overview

Cria o projeto Android do VCCSmartShopcart com a toolchain, a injeção de dependências, a
persistência Room completa (todas as entidades do MVP, versão 1 do esquema), o domínio
monetário e a navegação base. Os work-items seguintes passam a só acrescentar repositórios,
casos de utilização e ecrãs. Motivado por `sc-01-foundation` e pelas decisões D0–D13 de
[mvp-decisoes-tecnicas](../../docs/mvp-decisoes-tecnicas.md).

## Implementation

### Phase 1: Toolchain e Gradle

| Componente | Versão |
|---|---|
| AGP / Gradle | 9.4.1 / 9.8.0 |
| Kotlin (integrado no AGP 9) / KSP | 2.4.20 / 2.3.12 |
| Compose BOM | 2026.09.00 |
| Hilt / hilt-lifecycle-viewmodel-compose | 2.60.1 / 1.4.0 |
| Room / DataStore / Navigation | 2.8.5 / 1.2.1 / 2.10.2 |
| compileSdk, targetSdk / minSdk | 37 / 26 |

#### Step 1.1: Ficheiros de build

| Action | File | Details |
|---|---|---|
| Create | `settings.gradle.kts` | Repositórios `google()`, `mavenCentral()`, `gradlePluginPortal()`; `rootProject.name = "VCCSmartShopcart"` |
| Create | `build.gradle.kts` | Plugins com `apply false` |
| Create | `gradle/libs.versions.toml` | Catálogo único, sem versões dinâmicas |
| Create | `gradle.properties` | AndroidX, heap, cache |
| Create | `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Wrapper Gradle 9.8.0 |
| Create | `app/build.gradle.kts` | Compose, Hilt (KSP), Room com `room.schemaLocation`, serialization, testes JVM com Robolectric |

- [x] `gradlew :app:assembleDebug` compila com JDK 17

### Phase 2: Domínio monetário (TDD)

#### Step 2.1: Testes

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/.../core/money/MoneyTest.kt` | Formatação `1,34 €`, parsing com vírgula/ponto, `1,79 €/kg × 0,750 kg = 1,34 €`, `HALF_UP`, desconto nunca torna o total negativo, conversão g→kg e ml→L |

#### Step 2.2: Implementação

| Action | File | Details |
|---|---|---|
| Create | `core/money/Money.kt` | `MoneyFormatter`, `MoneyParser`, `QuantityParser`, `LineTotalCalculator` |
| Create | `domain/model/Enums.kt` | `SessionMode`, `SessionStatus`, `PriceSource`, `ProductSource`, `CaptureStatus`, `MeasureUnit`, `PlannedItemStatus` |

```kotlin
// D19 + D20: quantidade em G/ML convertida para KG/L; desconto absoluto; total >= 0
fun lineTotalCents(unitPriceCents: Long, quantity: BigDecimal, unit: MeasureUnit, discountCents: Long): Long {
    val base = unit.toBaseQuantity(quantity)
    val subtotal = BigDecimal(unitPriceCents).multiply(base).setScale(0, RoundingMode.HALF_UP).longValueExact()
    return (subtotal - discountCents).coerceAtLeast(0)
}
```

- [x] `MoneyTest` passa

### Phase 3: Persistência Room

| Action | File | Details |
|---|---|---|
| Create | `core/database/entity/*.kt` | `ShoppingList`, `ShoppingListItem`, `ShoppingSession`, `ShoppingSessionItem`, `Product`, `Store`, `ProductPriceHistory`, `PriceCapture`, `SessionPlannedItemState` com FKs e índices do documento §7.1 e decisões D7, D12, D13 |
| Create | `core/database/dao/*.kt` | DAOs com `Flow` para observação e `suspend` para escrita |
| Create | `core/database/ShoppingDatabase.kt` | Versão 1, `exportSchema = true`, `Converters` |
| Create | `app/schemas/` | Esquema exportado pelo KSP |
| Create | `app/src/test/.../core/database/ShoppingDatabaseTest.kt` | Robolectric: inserir lista + itens, sessão + itens, cascata ao eliminar, índice único de histórico |

- [x] Testes Room passam na JVM

### Phase 4: Aplicação, DI e navegação

| Action | File | Details |
|---|---|---|
| Create | `ShoppingApplication.kt`, `MainActivity.kt` | `@HiltAndroidApp`, `@AndroidEntryPoint`, edge-to-edge |
| Create | `core/di/DatabaseModule.kt`, `core/di/DispatchersModule.kt` | Base de dados, DAOs e `@IoDispatcher` |
| Create | `core/datastore/SettingsDataStore.kt` | Tema e limiar de alerta de orçamento (D16: sem moeda) |
| Create | `core/navigation/Routes.kt`, `AppNavHost.kt` | Rotas tipadas (D4) e barra inferior Início, Listas, Histórico, Definições |
| Create | `core/ui/theme/*` | Tema Material 3 com cor dinâmica e modo escuro |
| Create | `feature/*/...Screen.kt` | Ecrãs base de Início, Listas, Histórico e Definições |

- [x] App instala e arranca no emulador; a navegação inferior funciona

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` | Create | Configuração Gradle |
| `gradle/libs.versions.toml` | Create | Catálogo de versões |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Create | Wrapper |
| `app/build.gradle.kts`, `app/proguard-rules.pro` | Create | Módulo `app` |
| `app/src/main/AndroidManifest.xml`, `app/src/main/res/**` | Create | Manifesto, recursos e ícone |
| `app/src/main/java/pt/vcc/smartshopcart/core/money/Money.kt` | Create | Domínio monetário |
| `app/src/main/java/pt/vcc/smartshopcart/domain/model/*` | Create | Modelos e enums de domínio |
| `app/src/main/java/pt/vcc/smartshopcart/core/database/**` | Create | Room |
| `app/src/main/java/pt/vcc/smartshopcart/core/di/*` | Create | Módulos Hilt |
| `app/src/main/java/pt/vcc/smartshopcart/core/datastore/*` | Create | Preferências |
| `app/src/main/java/pt/vcc/smartshopcart/core/navigation/*` | Create | Navegação |
| `app/src/main/java/pt/vcc/smartshopcart/core/ui/**` | Create | Tema e componentes |
| `app/src/main/java/pt/vcc/smartshopcart/feature/**` | Create | Ecrãs base |
| `app/src/test/**` | Create | Testes unitários e Room |
| `app/schemas/**` | Create | Esquema Room exportado |
