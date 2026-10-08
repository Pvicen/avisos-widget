package io.github.pvicen.avisos

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

object Fechas {
    const val NORMAL = 0
    const val PRONTO = 1
    const val HOY = 2
    /** Ya venció, o es de hoy y su hora ya pasó (en la app, la etiqueta rellena). */
    const val VENCIDO = 3

    private val MESES = arrayOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )

    /** Lunes a domingo, en el orden de java.time (1 = lunes). */
    private val DIAS = arrayOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    /**
     * Etiqueta de vencimiento y su nivel de urgencia (mismos textos que la app):
     * «Hoy · 18:00», «Mañana · 9:30»… La hora solo cuenta si hay fecha.
     */
    fun etiqueta(vence: String?, hora: String? = null): Pair<String, Int>? {
        if (vence.isNullOrBlank()) return null
        return try {
            val fecha = LocalDate.parse(vence)
            val hoy = LocalDate.now()
            val dias = ChronoUnit.DAYS.between(hoy, fecha)
            // "09:30:00" → 9:30, comparada al minuto como en la app
            val laHora = if (hora.isNullOrBlank()) null else LocalTime.parse(hora).truncatedTo(ChronoUnit.MINUTES)
            val corta = fecha.dayOfMonth.toString() + " " + MESES[fecha.monthValue - 1] +
                if (fecha.year != hoy.year) " " + fecha.year else ""
            val (texto, nivel) = when {
                dias < 0L -> "Venció el $corta" to VENCIDO
                dias == 0L -> {
                    val ahora = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
                    "Hoy" to if (laHora != null && !laHora.isAfter(ahora)) VENCIDO else HOY
                }
                dias == 1L -> "Mañana" to PRONTO
                dias < 7L -> {
                    val dia = DIAS[fecha.dayOfWeek.value - 1] + " " + fecha.dayOfMonth
                    dia to if (dias <= 3L) PRONTO else NORMAL
                }
                else -> corta to NORMAL
            }
            if (laHora == null) texto to nivel
            else "$texto · ${laHora.hour}:${"%02d".format(laHora.minute)}" to nivel
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
