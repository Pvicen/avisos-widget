package io.github.pvicen.avisos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

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

        var reintentar = false
        try {
            Cache.guardarAvisos(contexto, Api.avisosPendientes(contexto))
        } catch (e: ErrorSesion) {
            Sesion.limpiar(contexto)
            Cache.guardarError(contexto, "toca para entrar")
        } catch (e: Exception) {
            // Fallo pasajero (sin señal, servidor ocupado): se muestra el estado y
            // WorkManager vuelve a intentarlo solo, con esperas crecientes.
            Cache.guardarError(contexto, "sin conexión")
            reintentar = runAttemptCount < MAX_INTENTOS
        }

        AvisosWidget.refrescar(contexto)
        return if (reintentar) Result.retry() else Result.success()
    }

    companion object {
        private const val TRABAJO = "actualizar-avisos"
        private const val MAX_INTENTOS = 5

        fun encolar(contexto: Context) {
            WorkManager.getInstance(contexto.applicationContext).enqueueUniqueWork(
                TRABAJO,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ActualizarWorker>()
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            )
        }
    }
}
