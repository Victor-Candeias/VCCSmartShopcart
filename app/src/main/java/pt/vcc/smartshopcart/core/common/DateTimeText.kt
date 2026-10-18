package pt.vcc.smartshopcart.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formata timestamps em epoch millis UTC (D10) na hora local do dispositivo, em pt-PT como o resto da app. */
object DateTimeText {
    private val portuguese: Locale = Locale.forLanguageTag("pt-PT")
    private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun dateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = portuguese): String =
        dateTime.withLocale(locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun date(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = portuguese): String =
        date.withLocale(locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun date(value: LocalDate, locale: Locale = portuguese): String = date.withLocale(locale).format(value)
}
