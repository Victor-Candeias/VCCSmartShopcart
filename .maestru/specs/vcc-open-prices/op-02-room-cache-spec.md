---
maestru: "0.4"
type: work-spec
id: op-02-room-cache-spec
title: "Especificação: cache Room e migração 1→2"
template: implementation-plan-v1
work-item: vcc-open-prices/op-02-room-cache
owner: developer
created: 2026-10-18
---

# Especificação: cache Room e migração 1→2

## Overview

Cache local de preços comunitários separada do histórico de preços confirmados (OP9), com estado por EAN que regista consultas vazias, paginação incompleta e limites 429 (OP10–OP12). A base de dados passa à versão 2 com migração explícita que não toca nos dados existentes.

## Implementation

### Fase 1 — Entidades e DAO

- `ReferenceSyncStatus { SUCCESS, EMPTY, RATE_LIMITED, FAILED }` no domínio.
- `ExternalPriceEntity` (`external_prices`, PK `externalId`, `barcode` TEXT, `observedAt` ISO, índice `barcode+observedAt`).
- `ExternalPriceSyncEntity` (`external_price_sync`, PK `barcode`, `lastAttemptAt`, `lastSuccessAt`, `status`, `totalAvailable`, `incomplete`, `retryAfter`).
- `ExternalPriceDao`: observar/ler preços e estado, `replacePrices` transacional, `purgeOlderThan`.

### Fase 2 — Migração

- `ShoppingDatabase` versão 2; esquema exportado `schemas/.../2.json`.
- `MIGRATION_1_2` com o SQL gerado pelo Room; registada em `DatabaseModule` via `ALL_MIGRATIONS`.

### Fase 3 — Testes

- `ExternalPriceDaoTest`: zeros à esquerda, sem duplicados, ordenação, substituição transacional, resultado vazio, purga.
- `MigrationTest`: cria v1 a partir de `1.json`, migra e deixa o Room validar o esquema v2.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `domain/model/ReferencePrice.kt` | modify | `ReferenceSyncStatus` |
| `core/database/entity/ExternalPriceEntities.kt` | create | Entidades Room |
| `core/database/dao/ExternalPriceDao.kt` | create | DAO |
| `core/database/ShoppingDatabase.kt` | modify | Versão 2 e DAO |
| `core/database/Migrations.kt` | create | `MIGRATION_1_2` |
| `core/di/DatabaseModule.kt` | modify | Migrações e provider do DAO |
| `app/schemas/.../2.json` | create | Esquema exportado |
| `test/.../ExternalPriceDaoTest.kt`, `MigrationTest.kt` | create | Testes |
