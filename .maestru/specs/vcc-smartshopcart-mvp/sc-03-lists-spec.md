---
maestru: "0.4"
type: work-spec
id: sc-03-lists-spec
title: "Especificação das listas planeadas"
template: implementation-plan-v1
work-item: vcc-smartshopcart-mvp/sc-03-lists
owner: developer
created: 2026-10-18
---

# Especificação das listas planeadas

## Overview

Implementa o modo **Lista planeada** (§4.2): CRUD de listas com orçamento opcional,
duplicação e arquivo; itens planeados com quantidade, unidade e preço estimado; sessão
`PLANNED` ligada à lista com progresso, itens ignorados e extras. Motivado por
`sc-03-lists` e pelas decisões D11 (orçamento copiado), D12 (só `SKIPPED` persistido),
D14 e D15.

## Implementation

### Phase 1: Domínio e dados (TDD)

#### Step 1.1: Testes

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/.../domain/usecase/ShoppingListUseCasesTest.kt` | Room em memória: criar/editar, duplicar copia itens com novos IDs, arquivar; sessão planeada copia o orçamento (alterar a lista depois não afeta); adicionar item planeado → `ADDED`; ignorar → `SKIPPED`; adicionar um item ignorado limpa o estado; extras |
| Create | `app/src/test/.../domain/usecase/PlannedProgressTest.kt` | Derivação pura de `PENDING/ADDED/SKIPPED`, extras e progresso |

#### Step 1.2: Implementação

| Action | File | Details |
|---|---|---|
| Create | `domain/repository/ShoppingListRepository.kt` | Listas e itens |
| Create | `data/repository/RoomShoppingListRepository.kt` | Implementação Room; duplicação numa transação |
| Create | `domain/usecase/ShoppingListUseCases.kt` | Criar, editar, duplicar, arquivar, eliminar listas; guardar/remover itens; iniciar sessão planeada |
| Create | `domain/usecase/PlannedProgress.kt` | `DerivePlannedProgress` puro e `SetPlannedItemSkipped` |
| Modify | `core/di/RepositoryModule.kt` | Binding do repositório |

- [x] Testes de listas e progresso passam

### Phase 2: UI

| Action | File | Details |
|---|---|---|
| Modify | `feature/shoppinglist/ShoppingListsScreen.kt` | Listas ativas/arquivadas, criar lista |
| Create | `feature/shoppinglist/ShoppingListsViewModel.kt` | Estado e ações das listas |
| Create | `feature/shoppinglist/ListDetailScreen.kt`, `ListDetailViewModel.kt` | Itens, estimativa, editar, duplicar, arquivar, eliminar, iniciar compra |
| Create | `core/ui/components/Dialogs.kt` (ListDialog, ListItemDialog, AddPriceDialog), `UnitSelector.kt` | Formulários reutilizáveis |
| Create | `feature/session/PlannedSessionScreen.kt`, `PlannedSessionViewModel.kt` | Progresso, por comprar, no carrinho, ignorados, extras, concluir/cancelar |
| Modify | `core/navigation/*`, `feature/home/*` | Rotas `ListDetailRoute(listId)` e `PlannedSessionRoute(sessionId)`; retoma pelo modo |

- [x] Fluxo no emulador: criar lista, itens, iniciar compra, registar preços, ignorar, extra, concluir

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/smartshopcart/domain/repository/ShoppingListRepository.kt` | Create | Contrato de listas |
| `app/src/main/java/pt/vcc/smartshopcart/data/repository/RoomShoppingListRepository.kt` | Create | Implementação Room |
| `app/src/main/java/pt/vcc/smartshopcart/domain/usecase/*` | Create | Casos de utilização e progresso |
| `app/src/main/java/pt/vcc/smartshopcart/core/di/RepositoryModule.kt` | Modify | DI |
| `app/src/main/java/pt/vcc/smartshopcart/core/ui/components/*` | Create | Diálogos |
| `app/src/main/java/pt/vcc/smartshopcart/feature/shoppinglist/*` | Create/Modify | Listas |
| `app/src/main/java/pt/vcc/smartshopcart/feature/session/*` | Create | Sessão planeada |
| `app/src/main/java/pt/vcc/smartshopcart/core/navigation/*` | Modify | Rotas |
| `app/src/main/res/values/strings.xml` | Modify | Textos pt-PT |
| `app/src/test/java/pt/vcc/smartshopcart/domain/usecase/*` | Create | Testes |
