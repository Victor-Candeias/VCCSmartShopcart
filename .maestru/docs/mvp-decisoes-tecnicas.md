---
maestru: "0.4"
type: doc
id: mvp-decisoes-tecnicas
title: "Decisões técnicas do MVP"
description: "Decisões que resolvem as inconsistências e lacunas encontradas na análise do documento de implementação do MVP."
created: 2026-10-18
updated: 2026-10-18
owner: developer
tags: [mvp, decisoes, arquitetura]
---

# Decisões técnicas do MVP

<!-- maestru:summary -->
Decisões D0–D26 que fixam o nome da app, a toolchain, o modelo de dados, as regras funcionais e o scanner, resolvendo as inconsistências e lacunas do documento de implementação.
<!-- /maestru:summary -->

Resultado da análise de [mvp-implementacao](mvp-implementacao.md). Cada decisão indica o ponto
do relatório que resolve (I = inconsistência, L = lacuna, R = risco). Estas decisões prevalecem
sobre o documento original.

## Identidade

| # | Decisão |
|---|---|
| D0 | Nome da aplicação: **VCCSmartShopcart**. Package/applicationId `pt.vcc.smartshopcart`. `User-Agent`: `VCCSmartShopcart/<versão> (https://github.com/Victor-Candeias/VCCSmartShopcart)`. |

## Toolchain

| # | Decisão |
|---|---|
| D1 | AGP 9.4.1 com Kotlin integrado 2.4.20, KSP 2.3.12, Gradle 9.8.0, JDK 17. |
| D2 | `compileSdk`/`targetSdk` 37, `minSdk` 26 (`java.time` nativo). |
| D3 | ML Kit com modelos **incluídos na app** (`barcode-scanning`, `text-recognition`) para funcionar offline no primeiro uso (R). |
| D4 | Rotas de navegação tipadas com `kotlinx.serialization`; argumentos opcionais como propriedades anuláveis (R). |
| D5 | Room com `exportSchema = true` e esquemas em `app/schemas/` versionados (R). |
| D6 | Imagens de produto não são mostradas no MVP; guarda-se apenas `imageUrl` (L8). |

## Modelo de dados

| # | Decisão |
|---|---|
| D7 | `Product.barcode` tem índice **não único**. A pesquisa devolve o registo mais recente. Códigos internos de loja (EAN-13 com prefixo 20–29) nunca são enviados para a Open Food Facts (I1, L2). |
| D8 | Candidatos de preço são `PriceCandidate(cents, kind, confidence, rawText)` com `kind ∈ {SALE, OLD, PER_KG, PER_L, DISCOUNT, UNKNOWN}`. `PriceCapture` guarda os candidatos em JSON (I2). |
| D9 | Domínio usa `Unit` enum e `BigDecimal`; Room guarda `quantity` como `TEXT` (I9). |
| D10 | Timestamps em epoch millis UTC (`Long`) (L11). |
| D11 | O orçamento da lista é **copiado** para a sessão ao iniciar; alterações posteriores à lista não afetam a sessão (I10). |
| D12 | Estado de item planeado por sessão: tabela `session_planned_item_states(sessionId, listItemId, status)` só para `SKIPPED`; `ADDED` deriva de existir item de sessão ligado; o resto é `PENDING` (I7). |
| D13 | `ProductPriceHistory.sessionItemId` é único, evitando duplicados (I4). |

## Regras funcionais

| # | Decisão |
|---|---|
| D14 | Sessões não são reabertas no MVP; sessões concluídas são só de leitura (I4, L10). |
| D15 | No máximo **uma** sessão `ACTIVE`. Iniciar outra exige concluir ou cancelar a atual; o Início mostra "Retomar" (L6). |
| D16 | Moeda fixa em EUR; não existe preferência de moeda (I5). |
| D17 | `PriceSource.HISTORY`: ao identificar produto, sugerir o último preço observado no mesmo supermercado; se o utilizador o aceitar sem alterar, a fonte é `HISTORY` (I6). |
| D18 | Supermercado escolhido (ou criado) ao iniciar a sessão; é opcional (L1). |
| D19 | Unidades de preço: `UN`, `KG`, `L`. A quantidade pode ser introduzida em `G`/`ML` e é convertida (÷1000) para `KG`/`L` antes do cálculo (L3). |
| D20 | Desconto absoluto por linha, em cêntimos; o total da linha nunca é negativo (L4). |
| D21 | Barcodes aceites: EAN-8, EAN-13 e UPC-A (normalizado para EAN-13 com `0` à esquerda), com dígito de controlo validado (L5). |
| D22 | Captura repetida: o mesmo código lido novamente em menos de 3 s no scanner é ignorado (R). |
| D23 | Compras concluídas podem ser eliminadas do histórico (L9). |
| D24 | Ecrã "Sobre" nas Definições com atribuição Open Food Facts (ODbL) (L7). |

## Scanner

| # | Decisão |
|---|---|
| D25 | A confirmação é um passo dentro do ecrã do scanner, com o mesmo ViewModel; o rascunho fica em `SavedStateHandle` e sobrevive à rotação e à morte do processo. A rota `capture/confirm/{draftId}` deixa de existir (I3, I8). |
| D26 | Parser reconhece `1,49`, `1.49`, `1,49 €`, `€ 1,49`, `1€49`, `1 49` (cêntimos separados/expoente) e marcadores `€/kg`, `€/l`, `antes`, `promo`, `%` (L12). |

## Requisitos não funcionais (metas)

- Leitura de barcode < 1 s em condições normais.
- Parser: preço correto como 1.º candidato em ≥ 90 % do conjunto de teste.
- Sem perda de dados após morte do processo durante sessão ativa.
