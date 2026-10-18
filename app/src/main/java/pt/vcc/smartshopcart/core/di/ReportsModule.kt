package pt.vcc.smartshopcart.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import pt.vcc.smartshopcart.data.reports.export.CsvReportExporter
import pt.vcc.smartshopcart.data.reports.export.JsonReportExporter
import pt.vcc.smartshopcart.data.reports.export.ReportExporter

/** Exportadores de relatórios; acrescentar um formato é só mais um `@IntoSet`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReportsModule {
    @Binds @IntoSet abstract fun bindCsvExporter(impl: CsvReportExporter): ReportExporter

    @Binds @IntoSet abstract fun bindJsonExporter(impl: JsonReportExporter): ReportExporter
}
