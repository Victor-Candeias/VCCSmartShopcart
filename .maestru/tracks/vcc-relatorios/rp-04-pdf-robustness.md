---
maestru: "0.4"
type: work-item
id: rp-04-pdf-robustness
title: "PDF multipágina com tabelas e gráficos, exportações grandes, privacidade e erros"
created: 2026-10-18
owner: developer
priority: medium
specs:
  - rp-04-pdf-robustness-spec
status: backlog
track: vcc-relatorios
blocked-by:
  - rp-03-export-csv-json
---

# rp-04-pdf-robustness: PDF multipágina com tabelas e gráficos, exportações grandes, privacidade e erros

Fonte: §11, §14 e Sprint 4 do §16.

## Âmbito

- `PdfLayoutEngine` (A4, margens, cabeçalho, rodapé com numeração, quebras de linha e de página, cabeçalhos de tabela repetidos) testável sem Android.
- `PdfReportExporter` com `android.graphics.pdf.PdfDocument`: resumo, gráficos vetoriais (categorias e evolução) e tabela de movimentos.
- Validação com 0, 1, 50 e 1000 linhas e caracteres portugueses; desempenho de exportações grandes.
- Robustez: falha de escrita sem falso sucesso (remoção do documento incompleto), cancelamento, limpeza de ficheiros temporários, mensagens claras; privacidade (sem envio para servidores).

## Saída verificável

PDF multipágina exportável; testes do motor de paginação verdes.
