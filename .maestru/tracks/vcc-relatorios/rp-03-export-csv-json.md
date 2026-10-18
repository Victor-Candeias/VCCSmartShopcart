---
maestru: "0.4"
type: work-item
id: rp-03-export-csv-json
title: "Exportação: contrato ReportExporter, ExportCoordinator, CSV seguro, JSON versionado, SAF e partilha"
created: 2026-10-18
owner: developer
priority: high
specs:
  - rp-03-export-csv-json-spec
status: backlog
track: vcc-relatorios
blocked-by:
  - rp-01-data-model
---

# rp-03-export-csv-json: Exportação: contrato ReportExporter, ExportCoordinator, CSV seguro, JSON versionado, SAF e partilha

Fonte: §8, §9, §10, §13 e Sprint 3 do §16.

## Âmbito

- `ExportFormat` (CSV, JSON, PDF, XLSX, HTML, TXT), `ReportExporter`, `ExportCoordinator` (mapa formato → exportador via Hilt, `Dispatchers.IO`, erros e cancelamento).
- CSV pt-PT: `;`, decimal `,`, UTF-8 com BOM, datas locais, células de texto com *escaping* e proteção contra fórmulas; colunas numéricas tipadas.
- JSON versionado (`schemaVersion`, preços inteiros, quantidades em texto, ISO-8601, EUR) com DTOs próprios.
- Granularidade (itens ou transações), nome do ficheiro e destino: guardar via Storage Access Framework (`ACTION_CREATE_DOCUMENT`) ou partilhar via `FileProvider` (`ACTION_SEND`, sem `file://`).
- `ExportReportSheet` no dashboard.
- Testes: `;`/aspas/nova linha, `=SUM(...)`, acentos, relatório vazio, JSON e totais coerentes.

## Saída verificável

Exportação CSV/JSON do relatório filtrado; testes de exportadores verdes.
