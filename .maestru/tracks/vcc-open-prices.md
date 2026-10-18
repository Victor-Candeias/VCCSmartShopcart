---
maestru: "0.4"
type: work-track
id: vcc-open-prices
title: VCCSmartShopcart — Integração Open Prices
created: 2026-10-18
description: "Preços de referência comunitários (Open Prices) para produtos lidos no scanner: cliente REST, validação PT/EUR, cache Room offline, sincronização e ecrã de preços, sem nunca substituir o preço confirmado pelo utilizador."
owner: developer
status: active
---

# vcc-open-prices: VCCSmartShopcart — Integração Open Prices

## Summary

<!-- maestru:work-items-list -->
| ID | Title | Status | Created | Owner | Priority | Completed | Template | Blocked By | Spec |
|---|---|---|---|---|---|---|---|---|---|
| op-01-api-client | Cliente Open Prices: DTOs, API Retrofit, User-Agent e validação PT/EUR | done | 2026-10-18 | developer | high | 2026-10-18 |  |  | [op-01-api-client](../specs/vcc-open-prices/op-01-api-client-spec.md) |
| op-02-room-cache | Room: preços externos, estado de sincronização e migração 1→2 | done | 2026-10-18 | developer | high | 2026-10-18 |  |  | [op-02-room-cache](../specs/vcc-open-prices/op-02-room-cache-spec.md) |
| op-03-repository-sync | Repositório de preços de referência: paginação, localizações, política de cache e 429 | done | 2026-10-18 | developer | high | 2026-10-18 |  | op-01-api-client, op-02-room-cache | [op-03-repository-sync](../specs/vcc-open-prices/op-03-repository-sync-spec.md) |
| op-04-prices-ui | Ecrã de preços em Portugal integrado no scanner e confirmação explícita | done | 2026-10-18 | developer | high | 2026-10-18 |  | op-03-repository-sync | [op-04-prices-ui](../specs/vcc-open-prices/op-04-prices-ui-spec.md) |
| op-05-release | Atribuição ODbL, documentação e APK release 0.3.0 assinado | done | 2026-10-18 | developer | medium | 2026-10-18 |  | op-04-prices-ui | [op-05-release](../specs/vcc-open-prices/op-05-release-spec.md) |
<!-- /maestru:work-items-list -->
