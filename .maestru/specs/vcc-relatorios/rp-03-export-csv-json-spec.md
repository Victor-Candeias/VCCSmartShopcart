---
maestru: "0.4"
type: work-spec
id: rp-03-export-csv-json-spec
title: "Especificação: Exportação CSV e JSON"
template: implementation-plan-v1
work-item: vcc-relatorios/rp-03-export-csv-json
owner: developer
created: 2026-10-18
---

# Especificação: Exportação CSV e JSON

## Overview

Contrato extensível de exportação (§8) com CSV pt-PT seguro (§9) e JSON versionado (§10), guardados via Storage Access Framework ou partilhados via `FileProvider` (§13).

## Implementation

### Phase 1: Contrato e exportadores

| Action | File | Details |
|--------|------|---------|
| Create | `data/reports/export/ExportFormat.kt` | Formatos (CSV/JSON/PDF no MVP; XLSX/HTML/TXT preparados), granularidade, opções |
| Create | `data/reports/export/ReportExporter.kt` | `write(report, output, options)` |
| Create | `data/reports/export/CsvReportExporter.kt` | `;`, BOM, decimal `,`, datas locais, *escaping* e proteção de fórmulas apenas em texto |
| Create | `data/reports/export/JsonReportExporter.kt` | DTOs próprios, `schemaVersion` 1, preços inteiros, quantidades texto |
| Create | `data/reports/export/ExportCoordinator.kt` | Mapa formato → exportador (`@IntoSet`), IO, erros/cancelamento, URI SAF, ficheiros temporários de partilha |

### Phase 2: UI

| Action | File | Details |
|--------|------|---------|
| Create | `feature/reports/ExportReportSheet.kt` | Formato, granularidade, nome, «Guardar em…» (`ACTION_CREATE_DOCUMENT`) e «Partilhar» (`ACTION_SEND`) |
| Modify | `AndroidManifest.xml`, `res/xml/file_paths.xml` | `FileProvider` em `cache/exports` |

- [x] `CsvReportExporterTest`, `JsonReportExporterTest`, `ExportCoordinatorTest` verdes

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `data/reports/export/*` | Create | Contrato, CSV, JSON, coordenador |
| `di/ReportsModule.kt` | Modify | Exportadores `@IntoSet` |
| `feature/reports/ExportReportSheet.kt`, `ReportsViewModel.kt` | Create/Modify | UI e eventos de exportação |
| `AndroidManifest.xml`, `res/xml/file_paths.xml` | Modify/Create | `FileProvider` |
| `test/.../export/*Test.kt` | Create | Testes |
