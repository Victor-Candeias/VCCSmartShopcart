---
maestru: "0.4"
type: work-spec
id: rp-04-pdf-robustness-spec
title: "Especificação: PDF e robustez"
template: implementation-plan-v1
work-item: vcc-relatorios/rp-04-pdf-robustness
owner: developer
created: 2026-10-18
---

# Especificação: PDF e robustez

## Overview

PDF gerado no dispositivo (§11) com paginação, cabeçalhos repetidos, rodapé numerado e gráficos vetoriais, e revisão de robustez, privacidade e erros (§14).

## Implementation

### Phase 1: Motor de paginação

| Action | File | Details |
|--------|------|---------|
| Create | `data/reports/export/pdf/PdfLayoutEngine.kt` | A4 (595×842 pt), margens, quebras de linha por medição de texto, quebras de página, cabeçalho de tabela repetido, rodapé «Página X de N»; puro Kotlin |
| Create | `data/reports/export/PdfReportExporter.kt` | Desenha os elementos em `PdfDocument` (texto, linhas, barras e linha temporal) |

### Phase 2: Robustez

- Escrita para URI SAF: em erro/cancelamento o documento parcial é apagado e é mostrado erro (sem falso sucesso).
- Ficheiros de partilha em `cache/exports`, apagados antes de cada nova partilha, após 24 h e a pedido.
- Nenhum envio para servidores; exportações em *streaming* (CSV linha a linha, JSON `encodeToStream`).

- [x] `PdfLayoutEngineTest` (0, 1, 50, 1000 linhas, acentos, margens, cabeçalho repetido, «Página X de N») verde
- [x] Limpeza automática de `cache/exports` (> 24 h) ao abrir Relatórios, testada em `ExportCoordinatorTest`
- [x] Teste instrumentado `PdfReportExporterTest` (1000 linhas → PDF A4 multipágina via `PdfRenderer`); o Robolectric não suporta `PdfDocument`

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `data/reports/export/pdf/PdfLayoutEngine.kt` | Create | Paginação testável |
| `data/reports/export/PdfReportExporter.kt` | Create | Renderização |
| `data/reports/export/ExportCoordinator.kt`, `feature/reports/*` | Modify | Erros, limpeza, mensagens |
| `data/reports/export/pdf/PdfReportContentBuilder.kt` | Create | `ReportData` → conteúdo do PDF (mesmas séries do dashboard) |
| `test/.../PdfLayoutEngineTest.kt` | Create | Testes |
| `androidTest/.../PdfReportExporterTest.kt` | Create | PDF real no dispositivo |
