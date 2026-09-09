package io.github.pvicen.avisos

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Trae los avisos pendientes desde Supabase y repinta el widget. */
class ActualizarWorker(contexto: Context, parametros: WorkerParameters) :
    Worker(contexto, parametros) {

    override fun doWork(): Result {
        val contexto = applicationContext

        if (!Sesion.hay(contexto)) {
            Cache.guardarError(contexto, "toca para entrar")
            AvisosWidget.refrescar(contexto)
            return Result.success()
        }

        try {
            Cache.guardarAvisos(contexto, Api.avisosPendientes(contexto))
        } catch (e: ErrorSesion) {
            Sesion.limpiar(contexto)
            Cache.guardarError(contexto, "toca para entrar")
        } catch (e: Exception) {
            Cache.guardarError(contexto, "sin conexión")
        }

        AvisosWidget.refrescar(contexto)
        return Result.success()
    }

    companion object {
        private const val TRABAJO = "actualizar-avisos"

        fun encolar(contexto: Context) {
            WorkManager.getInstance(contexto.applicationContext).enqueueUniqueWork(
                TRABAJO,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ActualizarWorker>().build()
            )
        }
    }
}
