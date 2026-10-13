package io.github.pvicen.avisos

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Saca el nombre y el punto de lo que se comparte desde Google Maps (o desde cualquier app que
 * comparta un enlace de mapa con coordenadas, como Mundi: `https://maps.google.com/?q=LAT,LON`).
 *
 * Primero mira el propio texto; si el enlace es corto (maps.app.goo.gl), sigue sus redirecciones a
 * mano mirando cada salto (en España Google pasa por su página de cookies, que lleva el destino en
 * `continue=`) y, al final, el cuerpo de la página. Si nada de eso trae coordenadas, quien llama puede
 * probar con el Geocoder del teléfono usando el nombre y la dirección.
 */
object EnlaceMaps {
    private const val SALTOS = 8
    private const val MAX_CUERPO = 512 * 1024

    // De más preciso a menos: el punto del sitio, unas coordenadas explícitas y, por último, el
    // centro de la vista del mapa
    private val PATRONES = listOf(
        Regex("""!3d(-?\d{1,3}\.\d+)!4d(-?\d{1,3}\.\d+)"""),
        Regex("""[?&](?:q|ll|query|center|destination|daddr)=(-?\d{1,3}\.\d+),\s*(-?\d{1,3}\.\d+)"""),
        Regex("""geo:(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)"""),
        Regex("""@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")
    )
    private val RE_ENLACE = Regex("""(?:https?://|geo:)\S+""")

    private val cliente: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Coordenadas (lat, lon) escritas en el texto, o null. */
    fun coordenadas(texto: String): Pair<Double, Double>? {
        // Los enlaces van codificados, a veces dos veces (el «continue=» de las cookies). Lo que
        // importa (, = ! @ :) se descodifica a mano por si el resto del texto no es descodificable.
        var s = texto
        repeat(3) {
            s = s.replace("%2C", ",", ignoreCase = true).replace("%3D", "=", ignoreCase = true)
                .replace("%21", "!").replace("%40", "@").replace("%3A", ":", ignoreCase = true)
                .replace("%3F", "?", ignoreCase = true).replace("%26", "&")
            s = try {
                URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
            } catch (e: Exception) {
                s
            }
        }
        for (patron in PATRONES) {
            val m = patron.find(s) ?: continue
            val lat = m.groupValues[1].toDoubleOrNull() ?: continue
            val lon = m.groupValues[2].toDoubleOrNull() ?: continue
            if (lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) return lat to lon
        }
        return null
    }

    /** El primer enlace del texto compartido. */
    fun enlace(texto: String): String? = RE_ENLACE.find(texto)?.value

    /** Las líneas que no son enlaces: la primera suele ser el nombre y las demás, la dirección. */
    fun lineas(texto: String): List<String> =
        texto.lines().map { it.trim() }.filter { it.isNotEmpty() && RE_ENLACE.find(it)?.value != it }

    /**
     * Sigue un enlace (corto o largo) buscando coordenadas en cada salto y luego en la página.
     * Hace red: llamarlo fuera del hilo principal. null si no las encuentra.
     */
    fun resolver(enlace: String): Pair<Double, Double>? {
        if (!enlace.startsWith("http")) return coordenadas(enlace)
        var url = enlace
        repeat(SALTOS) {
            coordenadas(url)?.let { return it }
            val peticion = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) AvisosWidget")
                .header("Accept-Language", "es-ES,es;q=0.9")
                .build()
            try {
                cliente.newCall(peticion).execute().use { respuesta ->
                    val destino = respuesta.header("Location")
                    if (destino != null && respuesta.code in 300..399) {
                        url = respuesta.request.url.resolve(destino)?.toString() ?: return null
                    } else {
                        val cuerpo = respuesta.body?.source()?.let { fuente ->
                            fuente.request(MAX_CUERPO.toLong())
                            fuente.buffer.snapshot().utf8()
                        } ?: return null
                        return coordenadas(cuerpo)
                    }
                }
            } catch (e: Exception) {
                return null
            }
        }
        return coordenadas(url)
    }
}
