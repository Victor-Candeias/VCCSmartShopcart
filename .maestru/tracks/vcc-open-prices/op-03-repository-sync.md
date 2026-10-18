---
maestru: "0.4"
type: work-item
id: op-03-repository-sync
title: "Repositório de preços de referência: paginação, localizações, política de cache e 429"
created: 2026-10-18
owner: developer
priority: high
status: done
completed: 2026-10-18
specs:
  - op-03-repository-sync-spec
track: vcc-open-prices
blocked-by:
  - op-01-api-client
  - op-02-room-cache
---

# op-03-repository-sync: Repositório de preços de referência: paginação, localizações, política de cache e 429

Fonte: §10 e §13 do documento de integração.

## Âmbito

- `ReferencePriceRepository` (domínio) e `OpenPricesRepository` (dados) com Hilt.
- Paginação limitada (3 páginas × 100) com aviso de resultados incompletos.
- Resolução de localizações em falta via `/locations/{id}` com cache em memória.
- Política de cache: 24 h, resultado vazio também em cache, cache antiga mostrada enquanto atualiza, sem rede usa Room.
- HTTP 429: respeita `Retry-After` (ou recuo por omissão) sem consultar a API durante esse período.
- Proteção contra atualizações concorrentes do mesmo EAN (scanner repetido) e expiração de registos antigos.
- Testes do repositório com MockWebServer + Room em memória.

## Saída verificável

Testes da política de cache, 429, paginação e concorrência verdes.