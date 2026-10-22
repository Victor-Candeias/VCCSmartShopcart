package pt.vcc.smartshopcart.core.navigation

import kotlinx.serialization.Serializable

// Rotas tipadas (D4). Argumentos opcionais são propriedades anuláveis.

@Serializable data object HomeRoute

@Serializable data object ListsRoute

@Serializable data object HistoryRoute

@Serializable data object SettingsRoute

@Serializable data class CalculatorRoute(val sessionId: String)

@Serializable data class ItemEditRoute(val itemId: String)

@Serializable data class ListDetailRoute(val listId: String)

@Serializable data class HistoryDetailRoute(val sessionId: String)

@Serializable data object AboutRoute

/** Criar, alterar ou desativar o PIN; [mode] é o nome de um `PinSetupMode`. */
@Serializable data class PinSetupRoute(val mode: String)

@Serializable data class PlannedSessionRoute(val sessionId: String)

/** Leitor de código de barras; [listItemId] liga o item lido a um artigo planeado. */
@Serializable data class ScannerRoute(val sessionId: String, val listItemId: String? = null)

// Despesas rápidas

@Serializable data object ExpensesHomeRoute

/**
 * Editor de despesa. Sem argumentos cria uma despesa nova; [categoryId]/[productId] pré-selecionam
 * o catálogo; [resumeDraft] retoma o rascunho guardado.
 */
@Serializable
data class ExpenseEditorRoute(
    val expenseId: String? = null,
    val categoryId: String? = null,
    val productId: String? = null,
    val repeatFromId: String? = null,
    val resumeDraft: Boolean = false,
)

@Serializable data object ExpenseCatalogRoute

@Serializable data object ExpenseHistoryRoute

@Serializable data class ExpenseDetailRoute(val expenseId: String)

// Relatórios

@Serializable data object ReportsRoute

/** Detalhe de uma secção do relatório; [kind] é o nome de um `ReportDetailKind`. */
@Serializable data class ReportDetailRoute(val kind: String)
