---
maestru: "0.4"
type: work-spec
id: op-03-repository-sync-spec
title: "Especificação: repositório e sincronização de preços de referência"
template: implementation-plan-v1
work-item: vcc-open-prices/op-03-repository-sync
owner: developer
created: 2026-10-18
---

# Especificação: repositório e sincronização de preços de referência

## Overview

Repositório de preços de referência que combina a API Open Prices (op-01) com a cache Room (op-02), aplicando a política OP10–OP13: cache de 24 h por EAN (incluindo resultados vazios), paginação limitada, resolução de localizações em falta, `Retry-After` e serialização por EAN.

## Implementation

### Fase 1 — Contrato de domínio

- `ReferenceSyncState` e `ReferencePrices` (preços + estado) em `domain/model/ReferencePrice.kt`.
- `ReferencePriceRepository` com `observe(barcode)` e `refresh(barcode, force)`; `ReferenceRefreshResult` = `UpToDate | Updated(count, incomplete) | RateLimited(until) | Offline | Failed | Unsupported`.

### Fase 2 — `OpenPricesRepository`

- `Mutex` por EAN; códigos internos (prefixo 2) ou não numéricos devolvem `Unsupported` sem chamadas.
- Ordem: `retryAfter` ativo → `RateLimited`; cache < 24 h e sem `force` → `UpToDate`; sem rede → `Offline`.
- Até 3 páginas × 100 (`order_by=-date`); `incomplete = total > obtidos`.
- Localização em falta: `GET /locations/{id}` com cache em memória; falhas não se repetem na mesma atualização.
- Sucesso: `replacePrices` transacional (SUCCESS/EMPTY) e purga de registos com mais de 90 dias.
- 429: `Retry-After` em segundos ou data HTTP, 60 s por omissão, máximo 24 h; estado `RATE_LIMITED` sem apagar a cache.
- Erros de rede/HTTP: estado `FAILED`, cache preservada.
- Binding Hilt em `RepositoryModule`.

### Fase 3 — Testes

`OpenPricesRepositoryTest` (Robolectric + Room em memória + MockWebServer): filtro PT e cache 24 h, vazio em cache, expiração e `force`, paginação limitada, 429 e `Retry-After` (segundos, data HTTP, omissão, limite), erro de rede, offline/código interno, localização em falta, concorrência.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `domain/model/ReferencePrice.kt` | modify | `ReferenceSyncState`, `ReferencePrices` |
| `domain/repository/ReferencePriceRepository.kt` | create | Contrato e `ReferenceRefreshResult` |
| `data/repository/OpenPricesRepository.kt` | create | Implementação e política de cache |
| `core/di/RepositoryModule.kt` | modify | Binding Hilt |
| `test/.../data/repository/OpenPricesRepositoryTest.kt` | create | Testes |
