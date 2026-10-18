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

@Serializable data class PlannedSessionRoute(val sessionId: String)
