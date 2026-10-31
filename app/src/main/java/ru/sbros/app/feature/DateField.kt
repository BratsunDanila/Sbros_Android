package ru.sbros.app.feature

import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

private val russianDateFormat=DateTimeFormatter.ofPattern("dd.MM.uuuu",Locale.forLanguageTag("ru")).withResolverStyle(ResolverStyle.STRICT)

internal fun displayDate(value: String): String=runCatching {LocalDate.parse(value).format(russianDateFormat)}.getOrDefault(value)

// Invalid/incomplete input stays editable; only complete dates become the storage format.
private fun storageDate(value: String): String {
    val input=value.trim()
    if(!input.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}")))return input
    return runCatching {LocalDate.parse(input,russianDateFormat).toString()}.getOrDefault(input)
}

@Composable internal fun DateField(value: String,change: (String)->Unit,label: String="Дата",tag: String=label,placeholder: String="ДД.ММ.ГГГГ") {
    ReferenceField(displayDate(value),{change(storageDate(it.take(10)))},"$label · ДД.ММ.ГГГГ",tag,placeholder=placeholder)
}
