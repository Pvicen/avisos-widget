# 📌 Avisos Widget

Widget de Android que muestra los pendientes de la app
[app-avisos](https://github.com/Pvicen/app-avisos) directamente en la pantalla de inicio:
texto, notas, prioridades (⚑) y fechas de vencimiento, con botón de actualizar.

Al tocarlo se abre la app de avisos. El widget es **solo lectura**: para agregar, editar
o completar avisos se usa la app.

## Instalar

1. Descarga el APK desde la [última versión publicada](https://github.com/Pvicen/avisos-widget/releases/tag/apk)
   (archivo `avisos-widget.apk`), **desde el propio celular**.
2. Ábrelo. Android pedirá permitir instalar apps de esta fuente: acepta.
   Si aparece un aviso de Play Protect, elige *Instalar de todas formas* (es tu propia app,
   compilada desde este repositorio).
3. Abre **Avisos Widget** e inicia sesión con la misma cuenta de la app de avisos.
   La sesión queda guardada en el dispositivo, cifrada.
4. Mantén presionada la pantalla de inicio → **Widgets** → busca **Avisos** → arrástralo.

## Cómo funciona

- Lee los avisos pendientes directamente de Supabase con la sesión del usuario, así que
  las mismas políticas de seguridad (RLS) de la app protegen los datos.
- Guarda una copia local de la última lista: el widget sigue mostrándola sin conexión.
- Se actualiza solo cada ~30 minutos (lo que permite Android sin gastar batería), al tocar
  el botón ↻ del widget, y cada vez que se abre la app y se pulsa *Actualizar*.

## Compilación

No hace falta instalar nada: GitHub Actions compila el APK en la nube
(workflow [`apk.yml`](.github/workflows/apk.yml)) y lo publica como release en cada push.

La llave de firma se generó una sola vez con el workflow
[`keystore.yml`](.github/workflows/keystore.yml) y se guarda **cifrada** en `firma/`;
su contraseña vive únicamente en el secreto `KEYSTORE_PASSWORD` del repositorio. Gracias a
eso todas las versiones se firman igual y las actualizaciones se instalan encima de la
anterior, sin desinstalar.
