package io.github.pvicen.avisos

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat

class ListaService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ListaFactory(applicationContext)
}

class ListaFactory(private val contexto: Context) : RemoteViewsService.RemoteViewsFactory {

    /** Quién anotó un aviso, listo para pintar su inicial. */
    private data class Autor(val inicial: String, val nombre: String, val color: Int)

    private var datos: List<Aviso> = emptyList()
    private var personas: List<Persona> = emptyList()

    override fun onCreate() {
        leer()
    }

    override fun onDataSetChanged() {
        leer()
    }

    private fun leer() {
        datos = Cache.leerAvisos(contexto)
        personas = Cache.personas(contexto)
    }

    override fun onDestroy() {
        datos = emptyList()
        personas = emptyList()
    }

    override fun getCount(): Int = datos.size

    override fun getViewAt(position: Int): RemoteViews {
        val vistas = RemoteViews(contexto.packageName, R.layout.widget_item)
        val aviso = datos.getOrNull(position) ?: return vistas

        vistas.setTextViewText(R.id.texto, aviso.texto)
        // Los importantes llevan la franja coral a la izquierda
        vistas.setInt(
            R.id.tarjeta, "setBackgroundResource",
            if (aviso.prioridad) R.drawable.tarjeta_importante else R.drawable.tarjeta
        )

        if (aviso.nota.isNullOrBlank()) {
            vistas.setViewVisibility(R.id.nota, View.GONE)
        } else {
            vistas.setViewVisibility(R.id.nota, View.VISIBLE)
            vistas.setTextViewText(R.id.nota, aviso.nota)
        }

        val etiqueta = Fechas.etiqueta(aviso.vence)
        if (etiqueta == null) {
            vistas.setViewVisibility(R.id.vence, View.GONE)
        } else {
            vistas.setViewVisibility(R.id.vence, View.VISIBLE)
            vistas.setTextViewText(R.id.vence, etiqueta.first)
            val (fondo, color) = when (etiqueta.second) {
                Fechas.VENCIDO -> R.drawable.chip_hoy to R.color.chip_hoy_texto
                Fechas.PRONTO -> R.drawable.chip_pronto to R.color.chip_pronto_texto
                else -> R.drawable.chip_normal to R.color.chip_normal_texto
            }
            vistas.setInt(R.id.vence, "setBackgroundResource", fondo)
            colorDeTexto(vistas, R.id.vence, color)
        }

        // Inicial de quien lo anotó (solo si la lista la comparten varias personas)
        val autor = if (personas.size > 1) autorDe(aviso.creadoPor) else null
        if (autor == null) {
            vistas.setViewVisibility(R.id.avatar, View.GONE)
        } else {
            vistas.setViewVisibility(R.id.avatar, View.VISIBLE)
            vistas.setTextViewText(R.id.avatar, autor.inicial)
            vistas.setInt(R.id.avatar, "setBackgroundResource", FONDOS_AVATAR[autor.color])
            colorDeTexto(vistas, R.id.avatar, TEXTOS_AVATAR[autor.color])
            vistas.setContentDescription(R.id.avatar, "Lo anotó " + autor.nombre)
        }

        // Tocar el círculo marca el aviso como hecho; tocar el resto abre la app.
        val completar = Intent()
        completar.putExtra(AvisosWidget.EXTRA_ACCION, AvisosWidget.ACCION_COMPLETAR)
        completar.putExtra(AvisosWidget.EXTRA_ID, aviso.id)
        vistas.setOnClickFillInIntent(R.id.check, completar)
        vistas.setOnClickFillInIntent(R.id.fila, Intent())
        return vistas
    }

    /** Mismo criterio de colores que la app: el orden de la lista de personas. */
    private fun autorDe(correo: String?): Autor? {
        if (correo.isNullOrBlank()) return null
        val clave = correo.lowercase()
        val indice = personas.indexOfFirst { it.correo == clave }
        if (indice < 0) return Autor(inicial(correo), correo, SIN_PERSONA)
        val persona = personas[indice]
        return Autor(inicial(persona.nombre), persona.nombre, indice % COLORES)
    }

    private fun inicial(texto: String): String {
        val limpio = texto.trim()
        if (limpio.isEmpty()) return "?"
        return String(Character.toChars(limpio.codePointAt(0))).uppercase()
    }

    /**
     * En Android 12+ el color se resuelve al pintar, así respeta el modo oscuro
     * aunque cambie después; antes se fija con el modo del momento.
     */
    private fun colorDeTexto(vistas: RemoteViews, id: Int, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vistas.setColor(id, "setTextColor", color)
        } else {
            vistas.setTextColor(id, ContextCompat.getColor(contexto, color))
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        datos.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        const val COLORES = 4
        const val SIN_PERSONA = 4

        val FONDOS_AVATAR = intArrayOf(
            R.drawable.avatar_p0, R.drawable.avatar_p1, R.drawable.avatar_p2,
            R.drawable.avatar_p3, R.drawable.avatar_px
        )
        val TEXTOS_AVATAR = intArrayOf(
            R.color.p0_texto, R.color.p1_texto, R.color.p2_texto,
            R.color.p3_texto, R.color.px_texto
        )
    }
}
