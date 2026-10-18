---
maestru: "0.4"
type: work-spec
id: rp-02-dashboard-spec
title: "Especificação: Dashboard de relatórios"
template: implementation-plan-v1
work-item: vcc-relatorios/rp-02-dashboard
owner: developer
created: 2026-10-18
---

# Especificação: Dashboard de relatórios

## Overview

Quarto módulo «Relatórios» acessível pela barra de navegação: filtros rápidos e detalhados, KPIs, gráficos e ecrãs de detalhe, todos alimentados pelo mesmo `ReportData` (§2, §7).

## Implementation

### Phase 1: Estado e filtros

| Action | File | Details |
|--------|------|---------|
| Create | `domain/reports/ReportPeriod.kt` | Hoje, 7 dias, Este mês, Mês anterior, Este ano, Personalizado e período equivalente anterior |
| Create | `feature/reports/ReportsViewModel.kt` | `Loading`/`Ready`/`Error`; filtros em `SavedStateHandle`; recalcula ao mudar filtros ou dados (`InvalidationTracker`) |

### Phase 2: Ecrãs e gráficos

| Action | File | Details |
|--------|------|---------|
| Create | `feature/reports/ReportsHomeScreen.kt` | KPIs, variação, aviso de dados incompletos, secções com gráficos |
| Create | `feature/reports/ReportsFiltersSheet.kt` | Categoria, estabelecimento, produto e intervalo personalizado |
| Create | `feature/reports/ReportDetailScreen.kt` | Categorias, evolução, estabelecimentos, produtos, orçamento (gráfico + tabela alternativa) |
| Create | `feature/reports/charts/*` | Linha, barras horizontais, donut e barras agrupadas em `Canvas`, desacoplados do modelo, com descrição textual |
| Modify | `core/navigation/*`, `feature/home/HomeScreen.kt` | Destino e entrada |

Decisão: gráficos em `Canvas` Compose (permitido pelo §2) para não acrescentar dependências.

- [x] Testes de períodos (`ReportSeriesTest`, rp-01) e `ReportsViewModelTest` verdes; APK debug compila

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `domain/reports/ReportPeriod.kt` | Create | Períodos |
| `feature/reports/*` | Create | ViewModel, ecrãs, folha de filtros e gráficos |
| `core/navigation/Routes.kt`, `AppNavHost.kt` | Modify | Rotas `ReportsRoute`/`ReportDetailRoute` |
| `feature/home/HomeScreen.kt` | Modify | Entrada «Relatórios» |
| `res/drawable/ic_reports.xml`, `res/values/strings.xml` | Create/Modify | Ícone e textos |
| `test/.../ReportsViewModelTest.kt` | Create | Testes (períodos já cobertos em `ReportSeriesTest`) |
