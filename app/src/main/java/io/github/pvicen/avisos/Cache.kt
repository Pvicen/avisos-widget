package io.github.pvicen.avisos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Copia local de los avisos: el widget siempre pinta desde aquí, así que
 * sigue mostrando la última lista conocida aunque no haya red.
 *
 * Además lleva la cuenta de los avisos que se acaban de marcar como hechos
 * desde el widget: se ocultan al instante y se mantienen ocultos hasta que el
 * servidor confirma, para que no reaparezcan un segundo por una recarga.
 */
object Cache {
    private const val LISTA = "lista"
    private const val ACTUALIZADO = "actualizado"
    private const val ERROR = "error"
    private const val OCULTOS = "ocultos"
    private const val VIDA_OCULTO = 5 * 60 * 1000L

    // ---------- Lista ----------

    fun guardarAvisos(contexto: Context, avisos: List<Aviso>) {
        val ocultos = ocultosVigentes(contexto)
        val arreglo = JSONArray()
        for (aviso in avisos) {
            if (ocultos.contains(aviso.id)) continue
            val fila = JSONObject()
            fila.put("id", aviso.id)
            fila.put("texto", aviso.texto)
            fila.put("prioridad", aviso.prioridad)
            if (aviso.nota != null) fila.put("nota", aviso.nota)
            if (aviso.vence != null) fila.put("vence", aviso.vence)
            arreglo.put(fila)
        }
        val editor = Sesion.prefs(contexto).edit()
        editor.putString(LISTA, arreglo.toString())
        editor.putLong(ACTUALIZADO, System.currentTimeMillis())
        editor.remove(ERROR)
        editor.apply()
    }

    fun leerAvisos(contexto: Context): List<Aviso> {
        val texto = Sesion.prefs(contexto).getString(LISTA, null) ?: return emptyList()
        return try {
            val arreglo = JSONArray(texto)
            val avisos = ArrayList<Aviso>(arreglo.length())
            for (i in 0 until arreglo.length()) {
                val fila = arreglo.optJSONObject(i) ?: continue
                avisos.add(
                    Aviso(
                        id = fila.optString("id"),
                        texto = fila.optString("texto"),
                        nota = if (fila.isNull("nota")) null else fila.optString("nota"),
                        prioridad = fila.optBoolean("prioridad", false),
                        vence = if (fila.isNull("vence")) null else fila.optString("vence")
                    )
                )
            }
            avisos
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- Marcados como hechos desde el widget ----------

    /** Lo saca de la lista al instante y lo deja anotado como "en camino". */
    @Synchronized
    fun ocultar(contexto: Context, id: String) {
        val ocultos = leerOcultos(contexto)
        ocultos.put(id, System.currentTimeMillis())
        val quedan = leerAvisos(contexto).filter { it.id != id }

        val arreglo = JSONArray()
        for (aviso in quedan) {
            val fila = JSONObject()
            fila.put("id", aviso.id)
            fila.put("texto", aviso.texto)
            fila.put("prioridad", aviso.prioridad)
            if (aviso.nota != null) fila.put("nota", aviso.nota)
            if (aviso.vence != null) fila.put("vence", aviso.vence)
            arreglo.put(fila)
        }
        val editor = Sesion.prefs(contexto).edit()
        editor.putString(LISTA, arreglo.toString())
        editor.putString(OCULTOS, ocultos.toString())
        editor.apply()
    }

    /** El servidor ya respondió (bien o mal): deja de ocultarlo. */
    @Synchronized
    fun confirmar(contexto: Context, id: String) {
        val ocultos = leerOcultos(contexto)
        ocultos.remove(id)
        Sesion.prefs(contexto).edit().putString(OCULTOS, ocultos.toString()).apply()
    }

    private fun leerOcultos(contexto: Context): JSONObject {
        val texto = Sesion.prefs(contexto).getString(OCULTOS, null) ?: return JSONObject()
        return try {
            JSONObject(texto)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /** Ignora los que llevan demasiado tiempo ocultos: nada queda escondido para siempre. */
    private fun ocultosVigentes(contexto: Context): Set<String> {
        val ocultos = leerOcultos(contexto)
        val ahora = System.currentTimeMillis()
        val vigentes = HashSet<String>()
        val caducados = ArrayList<String>()
        for (id in ocultos.keys()) {
            if (ahora - ocultos.optLong(id, 0L) < VIDA_OCULTO) vigentes.add(id) else caducados.add(id)
        }
        if (caducados.isNotEmpty()) {
            for (id in caducados) ocultos.remove(id)
            Sesion.prefs(contexto).edit().putString(OCULTOS, ocultos.toString()).apply()
        }
        return vigentes
    }

    // ---------- Estado ----------

    fun guardarError(contexto: Context, mensaje: String) {
        Sesion.prefs(contexto).edit().putString(ERROR, mensaje).apply()
    }

    fun error(contexto: Context): String? = Sesion.prefs(contexto).getString(ERROR, null)

    fun actualizado(contexto: Context): Long = Sesion.prefs(contexto).getLong(ACTUALIZADO, 0L)
}
