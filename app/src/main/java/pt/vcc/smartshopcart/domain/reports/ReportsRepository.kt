package pt.vcc.smartshopcart.domain.reports

import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/**
 * Fonte dos dados de relatório. As consultas aplicam apenas o período e os modos do filtro; os
 * filtros de dimensão são aplicados por [GenerateReportUseCase] (para também calcular as opções).
 */
interface ReportsRepository {
    suspend fun loadRows(filter: ReportFilter, zone: ZoneId): List<ReportRow>

    suspend fun loadBudgets(filter: ReportFilter, zone: ZoneId): List<ReportBudgetRow>

    suspend fun loadDataQuality(filter: ReportFilter, zone: ZoneId): ReportDataQuality

    /** Emite sempre que compras ou despesas mudam (inclui a emissão inicial). */
    fun observeChanges(): Flow<Unit>
}
