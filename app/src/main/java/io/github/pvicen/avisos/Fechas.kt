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

    /** Lunes a domingo, en el orden de java.time (1 = lunes). */
    private val DIAS = arrayOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    /** Etiqueta de vencimiento y su nivel de urgencia (mismos textos que la app). */
    fun etiqueta(vence: String?): Pair<String, Int>? {
        if (vence.isNullOrBlank()) return null
        return try {
            val fecha = LocalDate.parse(vence)
            val hoy = LocalDate.now()
            val dias = ChronoUnit.DAYS.between(hoy, fecha)
            val corta = fecha.dayOfMonth.toString() + " " + MESES[fecha.monthValue - 1] +
                if (fecha.year != hoy.year) " " + fecha.year else ""
            when {
                dias < 0L -> "Venció el $corta" to VENCIDO
                dias == 0L -> "Hoy" to VENCIDO
                dias == 1L -> "Mañana" to PRONTO
                dias < 7L -> {
                    val texto = DIAS[fecha.dayOfWeek.value - 1] + " " + fecha.dayOfMonth
                    texto to if (dias <= 3L) PRONTO else NORMAL
                }
                else -> corta to NORMAL
            }
        } catch (e: Exception) {
            null
        }
    }

    fun pendientes(cantidad: Int): String = when (cantidad) {
        0 -> "Nada pendiente"
        1 -> "1 pendiente"
        else -> "$cantidad pendientes"
    }

    fun hora(milisegundos: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(milisegundos))
}
