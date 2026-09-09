package io.github.pvicen.avisos

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

object Fechas {
    const val NORMAL = 0
    const val PRONTO = 1
    const val VENCIDO = 2

    private val MESES = arrayOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )

    /** Devuelve la etiqueta de vencimiento y su nivel de urgencia. */
    fun etiqueta(vence: String?): Pair<String, Int>? {
        if (vence.isNullOrBlank()) return null
        return try {
            val fecha = LocalDate.parse(vence)
            val dias = ChronoUnit.DAYS.between(LocalDate.now(), fecha)
            when {
                dias < 0L -> "vencido" to VENCIDO
                dias == 0L -> "vence hoy" to VENCIDO
                dias == 1L -> "vence mañana" to PRONTO
                else -> {
                    val texto = "vence " + fecha.dayOfMonth + " " + MESES[fecha.monthValue - 1]
                    texto to if (dias <= 3L) PRONTO else NORMAL
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun hora(milisegundos: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(milisegundos))
}
