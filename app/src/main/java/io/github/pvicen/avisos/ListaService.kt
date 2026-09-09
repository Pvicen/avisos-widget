package io.github.pvicen.avisos

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat

class ListaService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ListaFactory(applicationContext)
}

class ListaFactory(private val contexto: Context) : RemoteViewsService.RemoteViewsFactory {

    private var datos: List<Aviso> = emptyList()

    override fun onCreate() {
        datos = Cache.leerAvisos(contexto)
    }

    override fun onDataSetChanged() {
        datos = Cache.leerAvisos(contexto)
    }

    override fun onDestroy() {
        datos = emptyList()
    }

    override fun getCount(): Int = datos.size

    override fun getViewAt(position: Int): RemoteViews {
        val vistas = RemoteViews(contexto.packageName, R.layout.widget_item)
        val aviso = datos.getOrNull(position) ?: return vistas

        vistas.setTextViewText(R.id.texto, aviso.texto)
        vistas.setViewVisibility(R.id.prioridad, if (aviso.prioridad) View.VISIBLE else View.GONE)

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
            val color = when (etiqueta.second) {
                Fechas.VENCIDO -> R.color.rojo
                Fechas.PRONTO -> R.color.ambar
                else -> R.color.suave
            }
            vistas.setTextColor(R.id.vence, ContextCompat.getColor(contexto, color))
        }

        vistas.setOnClickFillInIntent(R.id.fila, Intent())
        return vistas
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        datos.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
