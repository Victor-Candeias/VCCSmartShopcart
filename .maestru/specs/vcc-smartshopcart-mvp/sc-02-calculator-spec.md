---
maestru: "0.4"
type: work-spec
id: sc-02-calculator-spec
title: "Especificação da calculadora rápida"
template: implementation-plan-v1
work-item: vcc-smartshopcart-mvp/sc-02-calculator
owner: developer
created: 2026-10-18
---

# Especificação da calculadora rápida

## Overview

Implementa o modo **Calculadora rápida** (§4.1): sessões `CALCULATOR` persistidas em Room,
teclado numérico grande, linhas editáveis com quantidade, unidade e desconto, desfazer
remoções, total e orçamento sempre atualizados, escolha de supermercado e retoma após
reabrir a app. Motivado por `sc-02-calculator` e pelas decisões D11, D13–D15, D18–D20.

## Implementation

### Phase 1: Repositórios e casos de utilização (TDD)

#### Step 1.1: Testes

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/.../domain/usecase/SessionUseCasesTest.kt` | Room em memória (Robolectric): só uma sessão `ACTIVE` (D15); 10 preços manuais somados; editar recalcula; remover e desfazer; concluir grava `finalTotalCents` e histórico só de itens identificáveis, sem duplicar; sessão concluída rejeita alterações |
| Create | `app/src/test/.../domain/usecase/BudgetStatusTest.kt` | Sem orçamento, dentro, aviso a partir do limiar e ultrapassado |

#### Step 1.2: Implementação

| Action | File | Details |
|---|---|---|
| Create | `domain/repository/ShoppingSessionRepository.kt`, `StoreRepository.kt` | Interfaces de sessão e supermercados |
| Create | `data/mapper/Mappers.kt` | Entidade ↔ domínio (`quantity` texto ↔ `BigDecimal`) |
| Create | `data/repository/RoomShoppingSessionRepository.kt`, `RoomStoreRepository.kt` | Implementações Room; conclusão numa transação |
| Create | `domain/usecase/SessionUseCases.kt` | `StartShoppingSession`, `AddSessionItem`, `UpdateSessionItem`, `RemoveSessionItem`, `RestoreSessionItem`, `CompleteShoppingSession`, `CancelShoppingSession` |
| Create | `domain/usecase/BudgetStatus.kt` | `CalculateSessionTotal` e `CalculateRemainingBudget` puros |
| Create | `core/di/RepositoryModule.kt` | Bindings Hilt |

- [x] Testes de casos de utilização passam

### Phase 2: UI

| Action | File | Details |
|---|---|---|
| Create | `core/ui/components/NumericKeypad.kt` | Teclado grande 0–9, vírgula e apagar; máximo 2 decimais |
| Create | `core/ui/components/BudgetBar.kt` | Total, restante, barra e aviso (cores e texto, não só cor) |
| Create | `core/ui/components/SessionItemRow.kt` | Linha com nome, `quantidade × preço`, total e remover |
| Create | `core/ui/components/StartSessionDialog.kt` | Supermercado (existente ou novo) e orçamento opcional |
| Create | `feature/home/HomeViewModel.kt` | Sessão ativa, supermercados, início da calculadora |
| Create | `feature/calculator/CalculatorViewModel.kt`, `CalculatorScreen.kt` | Entrada persistida em `SavedStateHandle`, adicionar, desfazer, concluir, cancelar |
| Create | `feature/product/ItemEditorViewModel.kt`, `ItemEditorScreen.kt` | Nome, preço, quantidade decimal, unidade (UN/KG/G/L/ML) e desconto |
| Modify | `core/navigation/*` | Rotas `CalculatorRoute(sessionId)` e `ItemEditRoute(itemId)` |

- [x] Fluxo manual no emulador: iniciar, adicionar, editar, remover/desfazer, concluir
- [x] Sessão ativa retomada após terminar o processo

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/smartshopcart/domain/repository/ShoppingSessionRepository.kt` | Create | Contrato de sessões |
| `app/src/main/java/pt/vcc/smartshopcart/domain/repository/StoreRepository.kt` | Create | Contrato de supermercados |
| `app/src/main/java/pt/vcc/smartshopcart/data/mapper/Mappers.kt` | Create | Conversões |
| `app/src/main/java/pt/vcc/smartshopcart/data/repository/*` | Create | Implementações Room |
| `app/src/main/java/pt/vcc/smartshopcart/domain/usecase/*` | Create | Casos de utilização |
| `app/src/main/java/pt/vcc/smartshopcart/core/di/RepositoryModule.kt` | Create | DI |
| `app/src/main/java/pt/vcc/smartshopcart/core/ui/components/*` | Create | Componentes reutilizáveis |
| `app/src/main/java/pt/vcc/smartshopcart/feature/home/*` | Modify | Início com retoma e arranque |
| `app/src/main/java/pt/vcc/smartshopcart/feature/calculator/*` | Create | Calculadora |
| `app/src/main/java/pt/vcc/smartshopcart/feature/product/*` | Create | Editor de linha |
| `app/src/main/java/pt/vcc/smartshopcart/core/navigation/*` | Modify | Rotas |
| `app/src/main/res/values/strings.xml` | Modify | Textos pt-PT |
| `app/src/test/java/pt/vcc/smartshopcart/domain/usecase/*` | Create | Testes |
