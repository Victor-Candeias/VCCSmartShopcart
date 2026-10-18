package pt.vcc.smartshopcart.data.reports.export

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.vcc.smartshopcart.domain.reports.GenerateReportUseCase
import pt.vcc.smartshopcart.domain.reports.ReportData
import pt.vcc.smartshopcart.domain.reports.ReportFilter
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportRow
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/** Gera um PDF real com [android.graphics.pdf.PdfDocument] e confirma que abre com várias páginas A4. */
@RunWith(AndroidJUnit4::class)
class PdfReportExporterTest {

    @Test
    fun milLinhasGeramPdfMultipaginaValido() = runBlocking {
        val zone = ZoneId.of("Europe/Lisbon")
        val start = LocalDate.of(2026, 4, 1)
        val rows = (0 until 1000).map { index ->
            ReportRow(
                transactionId = "t${index / 4}", itemId = "i$index",
                occurredAt = start.plusDays((index % 28).toLong()).atTime(9, 30).atZone(zone).toInstant(),
                mode = ReportMode.EXPENSE, establishmentId = null, establishmentName = "Mercearia São João",
                categoryId = "c${index % 5}", categoryName = "Categoria ${index % 5} — açúcar e pão",
                productId = null, productName = "Artigo nº $index com acentuação", quantity = BigDecimal.ONE, unit = "un",
                unitPriceCents = 100L + index, discountCents = 0, totalCents = 100L + index,
            )
        }
        val report = ReportData(
            filter = ReportFilter(start, LocalDate.of(2026, 4, 30)),
            rows = rows,
            summary = GenerateReportUseCase.summarize(rows),
            generatedAt = start.atStartOfDay(zone).toInstant(),
            zoneId = zone,
        )
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "teste.pdf")
        file.outputStream().use { PdfReportExporter().write(report, it) }

        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        try {
            assertTrue("páginas: ${renderer.pageCount}", renderer.pageCount > 20)
            val page = renderer.openPage(0)
            assertEquals(595, page.width)
            assertEquals(842, page.height)
            page.close()
        } finally {
            renderer.close()
            file.delete()
        }
    }
}
