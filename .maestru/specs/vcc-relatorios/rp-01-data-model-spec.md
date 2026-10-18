---
maestru: "0.4"
type: work-spec
id: rp-01-data-model-spec
title: "Especificação: Dados e agregações"
template: implementation-plan-v1
work-item: vcc-relatorios/rp-01-data-model
owner: developer
created: 2026-10-18
---

# Especificação: Dados e agregações

## Overview

Normaliza os movimentos reais dos três modos (calculadora, lista planeada concluída e despesas) num único modelo `ReportRow`, base de todos os ecrãs e exportações (§1, §4–§6). Garante snapshots históricos, intervalo local `[início, fim)` e contagem de transações por `(modo, id)`.

## Implementation

### Phase 1: Snapshots e migração 3→4

| Action | File | Details |
|--------|------|---------|
| Modify | `core/database/entity/ExpenseEntities.kt` | `expense_items.categoryIdSnapshot`/`categoryNameSnapshot` |
| Modify | `core/database/entity/Entities.kt` | `shopping_session_items.productNameSnapshot`/`categorySnapshot`; índice `shopping_sessions(status, completedAt)` |
| Modify | `core/database/Migrations.kt` | `MIGRATION_3_4` com `ALTER TABLE`, *backfill* a partir do catálogo e índice |
| Modify | `data/repository/RoomExpenseRepository.kt` | Preenche a categoria ao gravar |
| Modify | `data/repository/RoomShoppingSessionRepository.kt` | Preenche nome/categoria do produto ao concluir |

- [ ] `MigrationTest` 3→4 valida o *backfill*

### Phase 2: Domínio e consulta normalizada

| Action | File | Details |
|--------|------|---------|
| Create | `domain/reports/ReportModels.kt` | `ReportMode`, `ReportFilter`, `ReportRow`, `ReportSummary`, `ReportData`, orçamento, qualidade de dados, opções de filtro |
| Create | `domain/reports/ReportsRepository.kt` | Contrato |
| Create | `core/database/dao/ReportsDao.kt` | Linhas de despesas e de sessões `COMPLETED` (modo `PLANNED` → `PLANNED_LIST`), orçamento, dados incompletos |
| Create | `data/reports/ReportsRepositoryImpl.kt`, `ReportRowMapper.kt` | Intervalo local, filtros por dimensão |
| Create | `domain/reports/GenerateReportUseCase.kt`, `ReportSeries.kt` | KPIs, opções de filtro, comparação, séries (categoria, dia/semana/mês com zeros, estabelecimento, produto) |

IDs de dimensões têm prefixo da origem (`store:`, `establishment:`, `expense-category:`, `product-category:`, `product:`, `expense-product:`) para não colidir entre tabelas; estabelecimentos com o mesmo nome agrupam-se por nome.

- [ ] `ReportsRepositoryTest` e `ReportSeriesTest` cobrem os cenários do §15 relativos a dados

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `core/database/entity/*.kt`, `Migrations.kt`, `ShoppingDatabase.kt` | Modify | Snapshots, índice, versão 4 |
| `app/schemas/.../4.json` | Create | Esquema exportado |
| `core/common/ZoneProvider.kt`, `core/di/DatabaseModule.kt` | Create/Modify | Fuso horário injetável |
| `core/database/dao/ReportsDao.kt` | Create | Consultas normalizadas |
| `domain/reports/*` | Create | Modelo, contrato, caso de uso e séries |
| `data/reports/*`, `di/ReportsModule.kt` | Create | Repositório e bindings |
| `test/.../ReportsRepositoryTest.kt`, `ReportSeriesTest.kt`, `MigrationTest.kt` | Create/Modify | Testes |
