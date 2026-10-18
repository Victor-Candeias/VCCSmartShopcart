---
maestru: "0.4"
type: work-item
id: op-02-room-cache
title: "Room: preços externos, estado de sincronização e migração 1→2"
created: 2026-10-18
owner: developer
priority: high
status: done
completed: 2026-10-18
specs:
  - op-02-room-cache-spec
track: vcc-open-prices
---

# op-02-room-cache: Room: preços externos, estado de sincronização e migração 1→2

Fonte: §9 do documento de integração.

## Âmbito

- `ExternalPriceEntity` (`external_prices`) separada do histórico local de preços confirmados.
- `ExternalPriceSyncEntity` (`external_price_sync`) para registar consultas, incluindo as vazias, paginação incompleta e `Retry-After`.
- `ExternalPriceDao` com observação por EAN, `upsert`, substituição transacional de preços + estado e expiração.
- Base de dados versão 2 com `MIGRATION_1_2` explícita e esquema exportado em `app/schemas/`.
- Testes Room (sem duplicados, EAN como texto) e teste de migração 1→2.

## Saída verificável

Migração 1→2 testada e esquema `2.json` versionado.