---
maestru: "0.4"
type: work-item
id: sc-01-foundation
title: "Fundação: projeto Android, Hilt, Room, navegação e domínio monetário"
created: 2026-10-18
owner: developer
priority: critical
status: done
completed: 2026-10-18
track: vcc-smartshopcart-mvp
specs:
  - sc-01-foundation-spec
---

# sc-01-foundation: Fundação: projeto Android, Hilt, Room, navegação e domínio monetário

Sprint 1 do [documento de implementação](../../../Documents/Implementacao_MVP_App_Compras.md) (§12).

## Âmbito

- Projeto Android Kotlin + Compose (`pt.vcc.smartshopcart`, app `VCCSmartShopcart`).
- Catálogo de versões Gradle, Hilt, Room, Navigation Compose e DataStore.
- Entidades, DAOs, conversores e esquema Room exportado.
- Domínio monetário (cêntimos `Long`, `BigDecimal`, `HALF_UP`) com testes.
- Navegação inferior (Início, Listas, Histórico, Definições) com ecrãs base.

## Saída verificável

App inicia e persiste dados; testes unitários do domínio monetário e testes Room passam.
