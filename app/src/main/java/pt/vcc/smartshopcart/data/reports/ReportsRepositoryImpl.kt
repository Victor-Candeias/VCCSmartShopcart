package pt.vcc.smartshopcart.data.reports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.core.database.dao.ReportsDao
import pt.vcc.smartshopcart.domain.model.SessionMode
import pt.vcc.smartshopcart.domain.reports.ReportBudgetRow
import pt.vcc.smartshopcart.domain.reports.ReportDataQuality
import pt.vcc.smartshopcart.domain.reports.ReportFilter
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportRow
import pt.vcc.smartshopcart.domain.reports.ReportsRepository
import pt.vcc.smartshopcart.domain.reports.toEpochRange
import java.time.ZoneId
import javax.inject.Inject

class ReportsRepositoryImpl @Inject constructor(
    private val db: ShoppingDatabase,
) : ReportsRepository {

    private val dao: ReportsDao get() = db.reportsDao()

    override suspend fun loadRows(filter: ReportFilter, zone: ZoneId): List<ReportRow> {
        val (from, until) = filter.toEpochRange(zone)
        val sessionModes = filter.sessionModes()
        val rows = buildList {
            if (sessionModes.isNotEmpty()) addAll(dao.getCompletedSessionRows(from, until, sessionModes))
            if (ReportMode.EXPENSE in filter.modes) addAll(dao.getExpenseRows(from, until))
        }
        return rows.map { it.toDomain() }.sortedByDescending { it.occurredAt }
    }

    override suspend fun loadBudgets(filter: ReportFilter, zone: ZoneId): List<ReportBudgetRow> {
        val sessionModes = filter.sessionModes()
        if (sessionModes.isEmpty()) return emptyList()
        val (from, until) = filter.toEpochRange(zone)
        return dao.getBudgets(from, until, sessionModes).map { it.toDomain() }
    }

    override suspend fun loadDataQuality(filter: ReportFilter, zone: ZoneId): ReportDataQuality {
        val (from, until) = filter.toEpochRange(zone)
        val sessionModes = filter.sessionModes()
        val expenses = ReportMode.EXPENSE in filter.modes
        var withoutItems = 0
        var mismatches = 0
        if (sessionModes.isNotEmpty()) {
            withoutItems += dao.countSessionsWithoutItems(from, until, sessionModes)
            mismatches += dao.countSessionTotalMismatches(from, until, sessionModes)
        }
        if (expenses) {
            withoutItems += dao.countExpensesWithoutItems(from, until)
            mismatches += dao.countExpenseTotalMismatches(from, until)
        }
        return ReportDataQuality(transactionsWithoutItems = withoutItems, totalMismatches = mismatches)
    }

    override fun observeChanges(): Flow<Unit> = db.invalidationTracker
        .createFlow(*OBSERVED_TABLES)
        .map { }

    private fun ReportFilter.sessionModes(): List<String> = buildList {
        if (ReportMode.CALCULATOR in modes) add(SessionMode.CALCULATOR.name)
        if (ReportMode.PLANNED_LIST in modes) add(SessionMode.PLANNED.name)
    }

    private companion object {
        val OBSERVED_TABLES = arrayOf(
            "shopping_sessions",
            "shopping_session_items",
            "expenses",
            "expense_items",
            "stores",
            "establishments",
            "products",
            "expense_products",
            "expense_categories",
        )
    }
}
