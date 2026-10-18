---
maestru: "0.4"
type: work-item
id: rp-02-dashboard
title: "Dashboard de relatórios: navegação, filtros rápidos, KPIs, gráficos Compose e ecrãs de detalhe"
created: 2026-10-18
owner: developer
priority: high
specs:
  - rp-02-dashboard-spec
status: backlog
track: vcc-relatorios
blocked-by:
  - rp-01-data-model
---

# rp-02-dashboard: Dashboard de relatórios: navegação, filtros rápidos, KPIs, gráficos Compose e ecrãs de detalhe

Fonte: §2, §6 (séries), §7 e Sprint 2 do §16.

## Âmbito

- Destino «Relatórios» na barra de navegação e entrada no Início.
- `ReportsViewModel` (estado `Loading`/`Ready`/`Error`, filtros em `SavedStateHandle`, atualização ao mudar filtros ou dados).
- Filtros rápidos Hoje, 7 dias, Este mês, Mês anterior, Este ano, Personalizado; visão global ou por modo; folha de filtros por categoria, estabelecimento e produto.
- KPIs: total, transações, média, artigos e variação face ao período equivalente anterior (assinalando período em curso); aviso de dados incompletos.
- Gráficos em `Canvas` Compose, desacoplados do modelo: linha (evolução), donut (categorias), barras horizontais (top 10) e barras agrupadas (orçamento vs. realizado), com descrição textual e tabela alternativa.
- Ecrãs de detalhe: categorias, evolução temporal, estabelecimentos, produtos e orçamento.
- Estados vazio, erro e carregamento.

## Saída verificável

Ecrã de relatórios navegável no APK debug; testes de ViewModel/filtros verdes.
