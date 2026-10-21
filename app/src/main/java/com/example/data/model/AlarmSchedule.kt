package com.example.data.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Modelo de configuración de horarios para la protección perimetral de bodega.
 * Gestiona de forma precisa periodos normales y periodos que cruzan la medianoche.
 */
data class AlarmSchedule(
    val enabled: Boolean = true,
    val startHour: Int = 20,
    val startMinute: Int = 0,
    val endHour: Int = 6,
    val endMinute: Int = 0,
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // 1 = Lunes, 7 = Domingo
    val timezone: String = ZoneId.systemDefault().id,
    val manualOverride: Boolean = false,
    val isSyncedWithEsp32: Boolean = false
) {
    /**
     * Evalúa si un instante de tiempo dado cae dentro de la ventana de protección.
     */
    fun isTimeInProtectionWindow(now: ZonedDateTime = ZonedDateTime.now(ZoneId.of(timezone))): Boolean {
        if (!enabled || manualOverride) return false

        val currentDay = now.dayOfWeek.value
        val currentTime = now.toLocalTime()
        val startTime = LocalTime.of(startHour, startMinute)
        val endTime = LocalTime.of(endHour, endMinute)

        return if (startTime.isBefore(endTime)) {
            // Periodo dentro del mismo día calendario (ej: 08:00 a 18:00)
            if (!activeDays.contains(currentDay)) return false
            !currentTime.isBefore(startTime) && currentTime.isBefore(endTime)
        } else if (startTime == endTime) {
            // Protección 24 horas
            activeDays.contains(currentDay)
        } else {
            // Periodo que cruza la medianoche (ej: 20:00 a 06:00)
            // Caso A: Parte nocturna del día de inicio (desde startTime hasta medianoche)
            val isEveningOfActiveDay = activeDays.contains(currentDay) && !currentTime.isBefore(startTime)
            // Caso B: Parte matutina del día siguiente (desde medianoche hasta endTime)
            val previousDay = if (currentDay == 1) 7 else currentDay - 1
            val isMorningAfterActiveDay = activeDays.contains(previousDay) && currentTime.isBefore(endTime)

            isEveningOfActiveDay || isMorningAfterActiveDay
        }
    }

    fun formattedTimeRange(): String {
        return String.format("%02d:%02d a %02d:%02d", startHour, startMinute, endHour, endMinute)
    }
}
