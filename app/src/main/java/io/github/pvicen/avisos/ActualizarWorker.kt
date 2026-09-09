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
        val resultado = Actualizar.ahora(applicationContext)
        // Los fallos pasajeros se reintentan solos, con esperas crecientes.
        return if (resultado.pasajero && runAttemptCount < MAX_INTENTOS) {
            Result.retry()
        } else {
            Result.success()
        }
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
