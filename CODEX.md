# CODEX.md — ingeniería y operaciones de Linka

Guía autosuficiente para mantener Linka sin depender del historial de conversación. Describe el código actual, sus límites, comandos, permisos, dependencias y flujo de release. Para la descripción orientada a usuarios/reclutadores, consulta [README.md](README.md).

## 1. Identidad y alcance

- **Nombre:** Linka.
- **Repositorio público:** `https://github.com/rikiluciano/Linka`.
- **Application ID / namespace:** `com.rikiluciano.linka`.
- **Lenguaje/UI:** Kotlin, Jetpack Compose.
- **Arquitectura:** una Activity, ViewModel con `StateFlow`, extractor separado y servicio de descarga en primer plano.
- **Propósito:** navegador Android con análisis de páginas de video mediante yt-dlp, selección de formatos y descarga/ensamblado local con FFmpeg.
- **Uso:** el usuario debe poseer los derechos o tener autorización para guardar el medio.
- **Límites:** no usar cookies del WebView, credenciales, fingerprint spoofing, CAPTCHA bypass, DRM bypass ni otros controles de acceso. El extractor no garantiza soporte de cualquier dominio. Los sitios pueden cambiar y bloquear peticiones.
- **Privacidad:** no hay backend Linka, telemetría, anuncios, cuentas ni historial persistido de URLs.

La descarga desde algunas plataformas puede estar limitada por los términos del sitio y por derechos de autor. Las capacidades del extractor no implican que el usuario tenga derechos de descarga. No publicitar compatibilidad universal.

## 2. Dependencias/licencia: decisión explícita

El usuario aprobó adoptar GPL-3.0 para Linka para integrar `io.github.junkfood02.youtubedl-android:library:0.18.1` y su módulo `ffmpeg:0.18.1`. El texto completo de GPL está en la raíz (`LICENSE`). Al publicar el código y APK, conserva ese archivo y los avisos de las dependencias transitivas; no relicenciar el conjunto bajo una licencia incompatible. Revisa las licencias nuevamente al actualizar dependencias.

Dependencias Android de Compose, Lifecycle, Core KTX y coroutines están fijadas en `app/build.gradle`; no uses rangos dinámicos.

`ffmpeg-kit` de arthenica fue retirado/archivado. Este proyecto usa el módulo FFmpeg de `youtubedl-android`, no `com.arthenica:ffmpeg-kit-*`.

## 3. Fuentes de verdad / ubicaciones

- Rama de publicación: `main`.
- Remoto del servidor: `git@github.com:rikiluciano/Linka.git`.
- Clon canónico servidor: `/home/ricardo/proyects/Linka`.
- Publicador desde Windows: `publish-linka.ps1`.
- Sincronizador del servidor: `sync-to-github.sh`.
- Clave SSH dedicada permanece en `/home/ricardo/.ssh/linka_github`, fuera de Git.
- Alias SSH de Windows: `serveras`, configuración no versionada.
- Respaldo antiguo: `/home/ricardo/proyects/Linka-before-git`; no es el clon activo.

Nunca imprimir, copiar al proyecto o versionar contraseñas FTP, tokens, claves privadas, keystores, `local.properties` o credenciales de servicios.

## 4. Árbol del proyecto

```text
Linka/
├── .github/workflows/android.yml            # build APK; Release por tag v*
├── app/
│   ├── build.gradle                          # AGP Android/Kotlin, SDK, dependencias
│   └── src/main/
│       ├── AndroidManifest.xml               # permisos, Application, Activity, service
│       ├── java/com/rikiluciano/linka/
│       │   ├── LinkaApplication.kt            # inicialización yt-dlp + FFmpeg
│       │   ├── MainActivity.kt                # host Compose, ACTION_SEND/ACTION_VIEW
│       │   ├── download/DownloadService.kt    # FGS, progreso, MediaStore
│       │   ├── extractor/MediaFormat.kt       # dominio y selector de calidad
│       │   ├── extractor/VideoExtractor.kt    # getInfo, filtros, request yt-dlp
│       │   └── ui/
│       │       ├── BrowserView.kt              # WebView y detección de medios
│       │       ├── LinkaScreen.kt              # Compose, barra, FAB y BottomSheet
│       │       └── LinkaViewModel.kt           # StateFlow y coordinación UI
│       └── res/{drawable,values}/              # icono, nombre y tema
├── build.gradle                               # AGP y Compose Compiler plugins
├── settings.gradle                            # repositorios y módulo :app
├── gradle.properties                          # AndroidX/JVM
├── LICENSE                                    # GPL-3.0
├── README.md                                  # documentación pública
├── publish-linka.ps1                          # estación → servidor → GitHub
└── sync-to-github.sh                          # commit, semver, push, tag
```

No hay módulos Gradle `:domain`/`:data` por separado todavía. Los paquetes dentro de `:app` separan responsabilidades sin aumentar el overhead del build.

## 5. Configuración Android

- `compileSdk 37`; `targetSdk 36`; `minSdk 29`.
- JDK 17; Android Gradle Plugin 9.4.0; plugin Compose Compiler 2.3.21.
- `buildFeatures.compose = true`; AGP 9 incorpora soporte Kotlin.
- Dependencias yt-dlp con ABI `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`; confirma que las builds debug/release empaquetan solo ABIs soportadas.
- `android:extractNativeLibs="true"` se declara para los binarios de la librería.
- `INTERNET` para WebView/extractor.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PROCESSING` para descargas y procesamiento en segundo plano según API.
- `POST_NOTIFICATIONS` se solicita cuando el usuario inicia una descarga en API 33+.
- No pedir `READ/WRITE_EXTERNAL_STORAGE` ni `MANAGE_EXTERNAL_STORAGE`; archivos temporales están en caché privada y los terminados se publican mediante `MediaStore.Downloads`.
- `DownloadService` no exportado. La Activity launcher exportada recibe enlaces `ACTION_SEND text/plain`; valida esquemas HTTP/HTTPS antes de navegar/extracción.

Los servicios foreground tienen requisitos/limites de Android dependientes de la versión. Revalida tipos, permisos y duración si cambia `targetSdk`.

## 6. Entrada y UI

`MainActivity` es `ComponentActivity`, configura `LinkaScreen` y entrega una URL recibida desde `ACTION_SEND`/`ACTION_VIEW` al `LinkaViewModel`. El ViewModel normaliza únicamente URL HTTP/HTTPS con host. No hace scraping en UI.

`LinkaUiState` concentra barra, URL cargada, flag detección, trabajo de inspección, `ExtractedMedia` y error. La UI observa `StateFlow` con `collectAsState`. Mantener llamadas de red/extracción fuera del main thread.

`LinkaScreen` presenta barra de dirección, navegador, indicador y botón FAB cuando el navegador detecta un posible medio. El BottomSheet enseña mejor calidad, MP3 y formatos extraídos. La descarga solo inicia tras elección expresa. Mantén visible el estado de error; no afirmes que el medio puede descargarse antes de que el extractor liste formatos.

## 7. Navegador/detección

`BrowserView` se monta con `AndroidView(WebView)`:

- JavaScript y DOM storage están habilitados para páginas normales; file/content access está apagado y mixed content bloqueado.
- `shouldOverrideUrlLoading` admite solo HTTP y HTTPS.
- `shouldInterceptRequest` observa extensiones comunes (`mp4`, `m3u8`, `mpd`, audio) y comunica la URL de la página superior como candidata.
- `onPageFinished` hace una inspección DOM read-only (`video`/`source`) vía `evaluateJavascript`.
- No se expone `JavascriptInterface` a páginas ajenas. El HTML de terceros se considera no confiable.
- Detección es solo indicio; no prueba que yt-dlp entienda la página ni que ésta ofrezca un archivo legalmente descargable.
- Evitar registrar contenido HTML, cookies, URL con tokens firmados o identificadores sensibles en logs.

El JavaScript consultado devuelve solo un boolean del DOM; no debe ejecutar callbacks privilegiados ni iniciar descargas automáticamente. Si se reemplaza por puente JS, diseñar origin allow-list estricta y demostrar que contenido de subframes/sitios hostiles no puede invocar acciones privilegiadas.

## 8. Extracción y formatos

`VideoExtractor.inspect(url)` usa `Dispatchers.IO` y `YoutubeDL.getInstance().getInfo(url)`. Filtra formatos con audio o video y los adapta a `MediaFormat(id, label, height, extension, hasVideo, hasAudio, bitrate)`. Prioriza altura y bitrate para presentar opciones.

Selectores:

- Mejor video/audio: `bestvideo*+bestaudio/best`.
- Video individual con audio embebido: usa su `format_id`.
- Video sin audio: `format_id+bestaudio/best`, y FFmpeg hace merge.
- Audio MP3: `bestaudio/best`, `-x`, `--audio-format mp3`, `--audio-quality 0`.
- No agregar cookies, contraseñas, cabeceras extraídas del WebView, flags de impersonation, client spoofing ni bypasses de autenticación.

`LinkaApplication.onCreate()` inicializa `YoutubeDL` y `FFmpeg`. Si falla, `extractorReady=false`; ViewModel presenta error y no arranca el servicio.

El formato puede ser adaptativo; `VideoFormat` API exacta depende de la librería. Al actualizar dependencia, verifica nuevamente nombres/nullability de campos (`formats`, `formatId`, `vcodec`, `acodec`, `height`, `tbr`, `formatNote`).

## 9. Descargas en background y almacenamiento

`DownloadService` recibe solo extras internos explícitos: URL, título, `format_id`, audio-only y presencia de audio. Usa `ContextCompat.startForegroundService`, ejecuta `YoutubeDL.execute` en coroutine IO y notifica porcentaje/ETA de red. El progreso estimado se obtiene del crecimiento de archivos temporales en caché; es una aproximación, no velocidad de red exacta suministrada por yt-dlp.

Al terminar, busca los archivos generados por esta tarea, excluye `.part`, e inserta/copía cada archivo terminado a `MediaStore.Downloads` con `RELATIVE_PATH=Download/Linka` e `IS_PENDING` durante la copia. Si la publicación falla, debe borrar cualquier entrada pendiente y mostrar fallo claro. No añadas permisos amplios de almacenamiento.

El callback yt-dlp usado actualmente proporciona porcentaje y ETA; el estado de FFmpeg se infiere al final del progreso, no es una señal estructurada del wrapper. No presentarlo como telemetría exacta de FFmpeg. El APK de la librería será mucho mayor que el MVP inicial por Python/FFmpeg.

Revisa: cancelación del proceso mediante `destroyProcessById`, exclusión/cola para descargas simultáneas, recuperar servicio tras muerte del proceso, errores de falta de espacio, notificación y limpieza de archivos temporales. No declarar que estas capacidades existen si no están implementadas.

## 10. Compilación y control de calidad

El repositorio no trae Gradle Wrapper. CI instala Gradle 9.6.0; tarea `assembleDebug`; APK `app/build/outputs/apk/debug/app-debug.apk`, renombrado como `linka.apk` en artifact/Release. Firma debug, no Play signing.

Comando local si Android Studio/Gradle adecuado está configurado: `gradle --no-daemon --stacktrace :app:assembleDebug`.

No hay suite de tests instrumentados aún. CI valida compilación, no un flujo real con redes sociales. Antes de afirmar soporte de un sitio, comprobar extracción autorizada en Android real, selección de formato, merge, segundo plano, permiso de notificaciones, MediaStore y ruta final. No añadir/ejecutar tests salvo solicitud explícita del usuario; la compilación CI es un build de release solicitado, no sustituye pruebas en dispositivo.

## 11. CI, versiones y publicación

`.github/workflows/android.yml` build en pushes a `main`, tags `v*` y `workflow_dispatch`; job de tag sube el `linka.apk` a Release. El publicador de servidor incrementa patch y `versionCode`, sincroniza `versionName`, crea commit/tag y empuja ambos.

Desde PowerShell en raíz del clon:

```powershell
.\publish-linka.ps1 -Message "Describe el cambio"
```

El script copia archivos no ignorados y propagará eliminaciones a `/home/ricardo/proyects/Linka`, ejecuta `sync-to-github.sh`, sube commit/tag y refresca el clon local tras éxito. Requiere Git, OpenSSH y alias `serveras`. Cada publicación de cambios genera Release con APK.

Comando de bajo nivel, solo para cambios ya sincronizados con el servidor:

```bash
cd /home/ricardo/proyects/Linka
./sync-to-github.sh "Describe el cambio" --release
```

No reutilizar una etiqueta existente. Si el workflow falla, inspeccionar el run antes de volver a publicar; mantener repo y servidor sincronizados. El usuario autorizó este flujo de publicación de un solo comando para cambios en el proyecto.

## 12. Checklist de cambios

1. Confirma el estado de Git y conserva cambios ajenos.
2. Mantén UI, web detector, extractor, servicio y storage en sus responsabilidades actuales; refactoriza a módulos Gradle solo si hay una razón concreta.
3. Revisa esquema HTTP/S, entradas IPC, filenames, fallos de medios parciales, no reutilizar cookies ni persistir datos privados.
4. Actualiza README y este archivo al cambiar capacidades, permisos, dependencias/licencia, estructura o publicación.
5. Compila `assembleDebug`, revisa el workflow y confirma el APK del tag. Una compilación correcta no prueba compatibilidad del extractor con todos los sitios.
6. Usa el comando único autorizado para sincronizar/publicar; no metas credenciales en Git.

## 13. Estado de producto previo

`v1.0.1` era una demo Java mínima que rechazaba de forma explícita plataformas sociales y solo permitía URLs directas. Esa versión **no cumplía** la intención original del usuario. La rama actual reemplaza esa base con Compose/WebView/yt-dlp/FFmpeg; no describir el APK anterior como esta arquitectura.
