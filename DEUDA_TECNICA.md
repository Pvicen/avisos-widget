# Deuda técnica

Errores o límites no críticos, anotados para no frenar el avance (método C.C.D. §3).
Nada de esto es bloqueante ni pone en riesgo datos o seguridad.

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
