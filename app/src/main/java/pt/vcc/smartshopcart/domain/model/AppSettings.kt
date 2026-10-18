package pt.vcc.smartshopcart.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Preferências da app. A moeda é fixa em EUR (D16). */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Percentagem do orçamento a partir da qual se mostra o aviso de aproximação. */
    val budgetWarningPercent: Int = DEFAULT_BUDGET_WARNING_PERCENT,
) {
    companion object {
        const val DEFAULT_BUDGET_WARNING_PERCENT = 90
    }
}
