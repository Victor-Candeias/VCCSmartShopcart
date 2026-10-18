---
maestru: "0.4"
type: work-track
id: vcc-relatorios
title: "VCCSmartShopcart — Relatórios, Gráficos e Exportações"
created: 2026-10-18
description: "Quarto módulo offline que normaliza movimentos reais dos três modos (calculadora, lista planeada, despesas) em relatórios com filtros, KPIs, gráficos e exportação CSV/JSON/PDF (Documents/CestoSmart_Relatorios_Graficos_Exportacoes_Implementacao.md)."
owner: developer
status: active
---

# vcc-relatorios: VCCSmartShopcart — Relatórios, Gráficos e Exportações

MVP: rp-01 … rp-05 (Sprints 1–4 e critérios do §17). Fase 2 (rp-06 … rp-09) fica em backlog.

## Summary

<!-- maestru:work-items-list -->
| ID | Title | Status | Created | Owner | Priority | Completed | Template | Blocked By | Spec |
|---|---|---|---|---|---|---|---|---|---|
| rp-01-data-model | Dados e agregações: snapshots (migração 3→4), modelo de relatório, ReportsDao, repositório e GenerateReportUseCase | done | 2026-10-18 | developer | high | 2026-10-19 |  |  | [rp-01-data-model](../specs/vcc-relatorios/rp-01-data-model-spec.md) |
| rp-02-dashboard | Dashboard de relatórios: navegação, filtros rápidos, KPIs, gráficos Compose e ecrãs de detalhe | done | 2026-10-18 | developer | high | 2026-10-19 |  | rp-01-data-model | [rp-02-dashboard](../specs/vcc-relatorios/rp-02-dashboard-spec.md) |
| rp-03-export-csv-json | Exportação: contrato ReportExporter, ExportCoordinator, CSV seguro, JSON versionado, SAF e partilha | backlog | 2026-10-18 | developer | high |  |  | rp-01-data-model | [rp-03-export-csv-json](../specs/vcc-relatorios/rp-03-export-csv-json-spec.md) |
| rp-04-pdf-robustness | PDF multipágina com tabelas e gráficos, exportações grandes, privacidade e erros | backlog | 2026-10-18 | developer | medium |  |  | rp-03-export-csv-json | [rp-04-pdf-robustness](../specs/vcc-relatorios/rp-04-pdf-robustness-spec.md) |
| rp-05-release | Testes de aceitação, acessibilidade, documentação e APK release 0.5.0 assinado | backlog | 2026-10-18 | developer | medium |  |  | rp-02-dashboard, rp-04-pdf-robustness | [rp-05-release](../specs/vcc-relatorios/rp-05-release-spec.md) |
| rp-06-xlsx-html-txt | Fase 2: exportadores XLSX, HTML e TXT sobre o mesmo ReportData | backlog | 2026-10-18 | developer | low |  |  | rp-05-release |  |
| rp-07-price-history | Fase 2: histórico de preços próprios (separado dos externos) e comparação de períodos | backlog | 2026-10-18 | developer | low |  |  | rp-05-release |  |
| rp-08-recurring-reports | Fase 2: relatórios recorrentes com WorkManager, com consentimento do utilizador | backlog | 2026-10-18 | developer | low |  |  | rp-05-release |  |
| rp-09-json-import | Fase 2: importação/restauro de JSON versionado com validação de versão, duplicados e integridade | backlog | 2026-10-18 | developer | low |  |  | rp-03-export-csv-json |  |
<!-- /maestru:work-items-list -->
