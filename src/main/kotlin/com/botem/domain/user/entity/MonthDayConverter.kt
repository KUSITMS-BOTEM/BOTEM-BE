package com.botem.domain.user.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.MonthDay
import java.time.format.DateTimeFormatter

@Converter
class MonthDayConverter : AttributeConverter<MonthDay, String> {

    override fun convertToDatabaseColumn(attribute: MonthDay?): String? = attribute?.format(FORMATTER)

    override fun convertToEntityAttribute(dbData: String?): MonthDay? = dbData?.let { MonthDay.parse(it, FORMATTER) }

    companion object {
        private val FORMATTER = DateTimeFormatter.ofPattern("MM-dd")
    }
}
