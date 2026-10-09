# Deuda técnica

Errores o límites no críticos, anotados para no frenar el avance (método C.C.D. §3).
Nada de esto es bloqueante ni pone en riesgo datos o seguridad.

## Avisos por lugar (2026-10-10)

- **Lista de hasta media hora de antigüedad.** Al llegar a un lugar se enseña lo que había en la
  copia local (se actualiza cada ~30 min, con ↻ o al abrir la app); un aviso recién puesto en otro
  dispositivo puede no salir todavía.
- **«Una vez por visita» a la buena de Android.** Las zonas de 150 m pueden tardar unos minutos en
  dispararse y fallar en interiores sin GPS. Si la salida se pierde, la visita caduca a las 12 h;
  pasando más de 12 h seguidas en un lugar con avisos, puede volver a avisar.
- **Las zonas se vuelven a poner al arrancar, al cambiar la lista y cada 6 h.** Si se apaga y
  enciende la ubicación, puede pasar hasta una actualización sin vigilar.
- **Sin mapa.** Los lugares se guardan estando allí («Guardar dónde estoy»); para moverlos, se
  guarda otra vez con el mismo nombre.
- **Solo en este Android.** El iPhone (la app web) no puede vigilar lugares.
- **Probado solo compilando** hasta que se instale: no hay emulador en el flujo.

## «Te toca a ti» (2026-10-09)

- **Solo se ve, no se cambia.** El widget enseña «Para ti» / «Para Ana», pero pasar un aviso a
  alguien se hace desde la app.
- **Depende de la migración `2026-10-09-te-toca.sql` de app-avisos** (columna `para`). Está
  aplicada; sin ella, el widget no podría actualizar.

## Hora exacta (2026-10-08)

- **«Hoy · 18:00» pasa a relleno (ya pasó) con retraso.** El widget solo se repinta al
  actualizar (↻ o cada ~30 min), no en el minuto justo.
- **Depende de la migración `2026-10-08-hora.sql` de app-avisos** (columna `hora`). Está
  aplicada; sin ella, el widget no podría actualizar.
- **Reloj del teléfono.** «Hoy» y «ya pasó» usan la zona del teléfono; la hora guardada es la
  de España. Con el teléfono en otra zona horaria no cuadrarían.

## Diseño "Cálido" e iniciales (2026-09-25)

- **Sin la letra Nunito.** Los widgets (RemoteViews) no cargan fuentes propias de forma
  fiable; se usa la del sistema, así que el widget no calza al 100 % con la app.
- **Colores de iniciales y fechas en Android 11 o anterior.** En Android 12+ se resuelven al
  pintar y siguen al modo oscuro; en versiones anteriores se fijan al actualizar, y si cambia
  el modo oscuro se corrigen en la siguiente actualización (↻ o cada ~30 min).
- **Depende de la migración `2026-09-25-app-compartida.sql` de app-avisos.** Si la base no
  tuviera la columna `creado_por`, el widget no podría actualizar. Está aplicada.
- **Probado solo compilando.** No hay emulador en el flujo: el aspecto se revisa en el
  teléfono tras instalar.
- **Editar, notas, prioridades y fechas** siguen haciéndose desde la app, no desde el widget.
