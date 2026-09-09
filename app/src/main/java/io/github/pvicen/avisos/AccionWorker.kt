package io.github.pvicen.avisos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Confirma contra el servidor un aviso marcado como hecho desde el widget. */
class AccionWorker(contexto: Context, parametros: WorkerParameters) :
    Worker(contexto, parametros) {

    override fun doWork(): Result {
        val contexto = applicationContext
        val id = inputData.getString(CLAVE_ID)
        if (id.isNullOrBlank()) return Result.success()

        try {
            Api.completar(contexto, id)
        } catch (e: ErrorSesion) {
            Sesion.limpiar(contexto)
            Cache.revertir(contexto, id)
            Cache.guardarError(contexto, "toca para entrar")
            AvisosWidget.refrescar(contexto)
            return Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_INTENTOS) {
                Cache.guardarError(contexto, "guardando…")
                AvisosWidget.refrescar(contexto)
                return Result.retry()
            }
            // Se agotaron los intentos: que el aviso vuelva a verse.
            Cache.revertir(contexto, id)
            Cache.guardarError(contexto, "no se pudo marcar")
            AvisosWidget.refrescar(contexto)
            return Result.success()
        }

        Cache.completado(contexto, id)
        Actualizar.ahora(contexto)
        return Result.success()
    }

    companion object {
        private const val CLAVE_ID = "id"
        private const val MAX_INTENTOS = 5

        fun completar(contexto: Context, id: String) {
            // Con red obligatoria: sin señal el trabajo ESPERA (sobrevive incluso a
            // reiniciar el teléfono) en vez de gastar los reintentos contra el vacío.
            // Los intentos quedan para fallos reales del servidor.
            WorkManager.getInstance(contexto.applicationContext).enqueue(
                OneTimeWorkRequestBuilder<AccionWorker>()
                    .setInputData(workDataOf(CLAVE_ID to id))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                    .build()
            )
        }
    }
}
