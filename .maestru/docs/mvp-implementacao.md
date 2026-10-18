---
maestru: "0.4"
type: doc
id: mvp-implementacao
title: "Documento de implementação do MVP"
description: "Registo e resumo do documento de implementação do MVP VCCSmartShopcart (Documents/Implementacao_MVP_App_Compras.md)."
created: 2026-10-18
updated: 2026-10-18
owner: developer
tags: [mvp, requisitos, arquitetura]
---

# Documento de implementação do MVP

<!-- maestru:summary -->
Resumo e registo do documento de implementação do MVP VCCSmartShopcart: âmbito, stack, plano de sprints e critérios de aceitação.
<!-- /maestru:summary -->

Documento completo: [`Documents/Implementacao_MVP_App_Compras.md`](../../Documents/Implementacao_MVP_App_Compras.md)
(v1.0, 17/10/2026). As decisões que completam ou corrigem esse documento estão em
[mvp-decisoes-tecnicas](mvp-decisoes-tecnicas.md) e prevalecem em caso de conflito.

## Resumo

Aplicação Android nativa **VCCSmartShopcart**, offline-first, em português de Portugal e EUR, com dois modos:

1. **Calculadora rápida** — registar preços manualmente ou por fotografia, sem lista prévia.
2. **Lista planeada** — criar lista, iniciar sessão de compras e registar preços efetivos.

Inclui scanner com código de barras (ML Kit) e preço por OCR, identificação de produtos via
Open Food Facts (sem preços), orçamento, histórico local de compras e de preços por supermercado.

Fora do MVP: Open Prices e APIs de preços, comparação entre supermercados, talões, contas,
nuvem, partilha, promoções complexas e reconhecimento de produto por imagem.

## Stack

Kotlin · Jetpack Compose + Material 3 · Clean Architecture + MVVM · Hilt · Room · DataStore ·
Coroutines/Flow · Navigation Compose · CameraX · ML Kit Barcode e Text Recognition · Retrofit + OkHttp +
kotlinx.serialization · JUnit, MockK, testes Room e Compose.

## Plano

| Sprint | Work-item |
|---|---|
| 1 — Fundação | `sc-01-foundation` |
| 2 — Calculadora | `sc-02-calculator` |
| 3 — Listas | `sc-03-lists` |
| 4 — Barcode e catálogo | `sc-04-barcode-catalog` |
| 5 — OCR e captura combinada | `sc-05-ocr-capture` |
| 6 — Histórico e estabilização | `sc-06-history-stabilization` |

## Critérios de aceitação (§11.3)

1. Adicionar 10 preços manualmente sem Internet.
2. Total igual à soma das linhas, incluindo quantidades.
3. Criar lista e iniciar sessão associada.
4. Código e preço na mesma imagem quando legíveis.
5. Duas capturas sucessivas em qualquer ordem.
6. Preço OCR sempre confirmado antes de entrar no carrinho.
7. Open Food Facts preenche dados sem impedir registo manual em falha.
8. Sessão ativa retomada após fechar e reabrir a app.
9. Sessão concluída no histórico com total e itens.
10. Orçamento mostra valor restante e aviso quando ultrapassado.
