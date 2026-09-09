package io.github.pvicen.avisos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class AvisosWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) pintar(context, appWidgetManager, id)
        ActualizarWorker.encolar(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACCION_REFRESCAR) {
            ActualizarWorker.encolar(context)
        }
    }

    companion object {
        const val ACCION_REFRESCAR = "io.github.pvicen.avisos.REFRESCAR"

        /** Extras que cada fila añade a la plantilla al tocarla. */
        const val EXTRA_ACCION = "accion"
        const val EXTRA_ID = "id"
        const val ACCION_COMPLETAR = "completar"

        /** Repinta el encabezado y pide a la lista que relea la copia local. */
        fun refrescar(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AvisosWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) pintar(context, manager, id)
            manager.notifyAppWidgetViewDataChanged(ids, R.id.lista)
        }

        fun pintar(context: Context, manager: AppWidgetManager, id: Int) {
            val vistas = RemoteViews(context.packageName, R.layout.widget)
            val haySesion = Sesion.hay(context)

            // Fuente de datos de la lista (un adaptador por widget colocado)
            val intentServicio = Intent(context, ListaService::class.java)
            intentServicio.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            intentServicio.data = Uri.parse(intentServicio.toUri(Intent.URI_INTENT_SCHEME))
            vistas.setRemoteAdapter(R.id.lista, intentServicio)
            vistas.setEmptyView(R.id.lista, R.id.vacio)

            // Encabezado
            val error = Cache.error(context)
            val actualizado = Cache.actualizado(context)
            val cantidad = Cache.leerAvisos(context).size
            val estado = when {
                !haySesion -> "toca para entrar"
                error != null -> error
                actualizado == 0L -> "actualizando…"
                else -> "$cantidad · " + Fechas.hora(actualizado)
            }
            vistas.setTextViewText(R.id.estado, estado)
            vistas.setTextViewText(
                R.id.vacio,
                if (haySesion) "Sin pendientes 🎉" else "Toca para iniciar sesión"
            )

            // Botón de refrescar
            val refresco = Intent(context, AvisosWidget::class.java).setAction(ACCION_REFRESCAR)
            vistas.setOnClickPendingIntent(
                R.id.refrescar,
                PendingIntent.getBroadcast(
                    context, 0, refresco,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            // Botón de agregar un aviso nuevo
            vistas.setOnClickPendingIntent(
                R.id.agregar,
                PendingIntent.getActivity(
                    context, 4,
                    Intent(context, NuevoAvisoActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            // Tocar el widget abre la app (o el inicio de sesión si falta). El destino
            // lo decide AbrirActivity al recibir el toque: el intent tiene que ser
            // EXPLÍCITO, porque Android 14+ rechaza los implícitos en estos casos.
            val abrir = Intent(context, AbrirActivity::class.java)
            abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val alTocar = PendingIntent.getActivity(
                context, 1, abrir,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            vistas.setOnClickPendingIntent(R.id.cabecera, alTocar)
            vistas.setOnClickPendingIntent(R.id.vacio, alTocar)

            // La plantilla de las filas sí es mutable: cada fila le añade su acción
            // y su id. Es válido porque el intent apunta a un componente propio.
            vistas.setPendingIntentTemplate(
                R.id.lista,
                PendingIntent.getActivity(
                    context, 3, abrir,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            manager.updateAppWidget(id, vistas)
        }
    }
}
