---
maestru: "0.4"
type: doc
id: relatorios-decisoes
title: "Decisões do módulo Relatórios, gráficos e exportações"
description: "Decisões que concretizam o documento Documents/CestoSmart_Relatorios_Graficos_Exportacoes_Implementacao.md no código existente (RP1–RP12)."
created: 2026-10-18
updated: 2026-10-18
owner: developer
tags: [relatorios, exportacoes, graficos, decisoes, arquitetura]
---

# Decisões do módulo Relatórios, gráficos e exportações

<!-- maestru:summary -->
Decisões RP1–RP12 do MVP de relatórios: modelo normalizado `ReportRow`, snapshots Room v4, dashboard Compose com gráficos Canvas, exportações CSV/JSON/PDF no dispositivo, SAF/partilha e testes de aceitação.
<!-- /maestru:summary -->

Documento de origem: [`Documents/CestoSmart_Relatorios_Graficos_Exportacoes_Implementacao.md`](../../Documents/CestoSmart_Relatorios_Graficos_Exportacoes_Implementacao.md).
Work-items: [`vcc-relatorios`](../tracks/vcc-relatorios.md) (rp-01 … rp-05 no MVP; rp-06 … rp-09 em backlog para a Fase 2).

| # | Decisão |
|---|---------|
| RP1 | Os três modos (calculadora, lista planeada concluída, despesa rápida) são normalizados por `ReportsDao` numa `ReportRow` por artigo. Só contam sessões `COMPLETED` e despesas; transações identificam-se por `(modo, id)` para não colidirem entre tabelas. |
| RP2 | Migração Room 3→4 acrescenta snapshots (`categoryIdSnapshot`/`categoryNameSnapshot` em `expense_items`, `productNameSnapshot`/`categorySnapshot` em `shopping_session_items`) e o índice `(status, completedAt)`: alterar o catálogo não reescreve o histórico. |
| RP3 | `ReportFilter` usa datas locais inclusivas convertidas em `[início, fim)` epoch no fuso do dispositivo (correto em mudanças de hora). «Este mês» termina hoje e compara com os mesmos dias do mês anterior. |
| RP4 | `GenerateReportUseCase` produz um único `ReportData` (KPIs, orçamento vs. realizado, avisos de qualidade, comparação, opções de filtro); dashboard e todos os exportadores consomem o mesmo objeto, garantindo totais iguais. |
| RP5 | Agregações (`ReportSeries`) são puras: categorias, estabelecimentos, modos, produtos, linha temporal contínua (dia/semana/mês consoante o intervalo) e agrupamento «Outros». |
| RP6 | Dashboard em Compose com gráficos desenhados em `Canvas` (sem bibliotecas externas), cada um com descrição textual e tabela alternativa para acessibilidade; valores em pt-PT. Filtros guardados em `SavedStateHandle`; o ecrã de detalhe partilha o `ReportsViewModel` do destino Relatórios. |
| RP7 | Exportadores registados por multibinding Hilt (`@IntoSet` em `ReportsModule`); `ExportCoordinator` escolhe pelo `ExportFormat`. XLSX/HTML/TXT estão declarados mas sem exportador (Fase 2). |
| RP8 | CSV para Excel pt-PT: UTF-8 com BOM, `;`, decimal `,`, `\r\n`, texto entre aspas e prefixo `'` em células que começam por `= + - @`/tab/CR (injeção de fórmulas). Granularidade por artigo ou por transação. |
| RP9 | JSON versionado (`schemaVersion` 1) com `kotlinx.serialization` em *streaming*: preços em cêntimos inteiros, quantidades em `String`, datas ISO-8601 e fuso explícito. |
| RP10 | PDF A4 com `android.graphics.pdf.PdfDocument`: `PdfReportContentBuilder` converte o `ReportData` e `PdfLayoutEngine` (puro Kotlin, testável) calcula quebras de linha/página, cabeçalho de tabela repetido e rodapé «Página X de N»; gráficos vetoriais com as mesmas séries do dashboard. O PDF ignora a granularidade. |
| RP11 | Guardar via SAF (`ACTION_CREATE_DOCUMENT`, sem permissões de armazenamento) ou partilhar via `FileProvider` a partir de `cache/exports`. Em erro/cancelamento o ficheiro parcial é apagado e é mostrada mensagem; temporários são apagados antes de cada partilha e após 24 h. Nada é enviado para servidores. |
| RP12 | Aceitação (`ReportsAcceptanceTest`, Room em memória): total = soma das transações concluídas na BD; CSV, JSON e PDF com o mesmo total e número de transações; relatório vazio válido; falha de escrita sem falso sucesso. O PDF real é validado por teste instrumentado (`PdfReportExporterTest`), pois o Robolectric não suporta `PdfDocument`. |
