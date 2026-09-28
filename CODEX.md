# CODEX.md — contexto de ingeniería de Linka

Este documento es la guía de entrada para agentes de IA, desarrolladores y colaboradores humanos. Describe el estado y comportamiento reales del repositorio para que el trabajo pueda continuar sin depender del historial del chat. El `README.md` está dirigido a usuarios y reclutadores; este archivo prioriza arquitectura, rutas, decisiones y operación.

## 1. Identidad y objetivo

- **Nombre:** Linka.
- **Repositorio público:** `https://github.com/rikiluciano/Linka`.
- **Plataforma:** aplicación nativa Android escrita en Java.
- **Paquete / application ID:** `com.rikiluciano.linka`.
- **Actividad principal:** `com.rikiluciano.linka.MainActivity`.
- **Problema que resuelve:** reduce los pasos necesarios para guardar un archivo de audio/video accesible después de recibir su enlace desde otra aplicación.
- **Propuesta de valor:** el usuario comparte o pega un enlace; Linka comprueba si llega a un archivo multimedia directo y entrega la descarga al gestor nativo de Android.
- **Límite de producto:** el proyecto no descarga ni extrae streams de YouTube, Instagram, Facebook u otras páginas de reproducción. No elude autenticación, controles de acceso o restricciones de plataforma. Solo descarga archivos directos propios o autorizados.

El alcance actual es deliberadamente un MVP. No hay conversión a MP3/MP4, selector de calidad, navegador interno, historial local, reproductor, backend, cuentas, telemetría ni anuncios. La app conserva el archivo y la calidad de la URL original.

## 2. Fuente de verdad y ubicaciones

- **Rama de trabajo/publicación:** `main`.
- **Remoto GitHub del servidor:** `git@github.com:rikiluciano/Linka.git`.
- **Ruta canónica del clon Git en el servidor:** `/home/ricardo/proyects/Linka`.
- **Clave SSH del servidor:** `/home/ricardo/.ssh/linka_github`; la clave privada permanece en el servidor y está autorizada como deploy key de escritura solo para este repositorio.
- **Alias SSH usado desde Windows:** `serveras` (configuración local, no se versiona).
- **Publicador de un solo comando:** `publish-linka.ps1`.
- **Publicador de servidor:** `sync-to-github.sh`.
- **Copia anterior de seguridad en el servidor:** `/home/ricardo/proyects/Linka-before-git`.

No copiar, imprimir, commitear ni incluir en la documentación credenciales FTP, claves privadas, tokens personales, secretos SSH, archivos `local.properties` o claves de firma Android. La carpeta de respaldo no es el checkout activo.

## 3. Estructura y rutas importantes

```text
Linka/
├── .github/
│   └── workflows/
│       └── android.yml                 # Build en main; build + Release en tags v*
├── app/
│   ├── build.gradle                   # Android SDK, applicationId y versiones
│   └── src/main/
│       ├── AndroidManifest.xml        # Internet, launcher y ACTION_SEND text/plain
│       ├── java/com/rikiluciano/linka/
│       │   └── MainActivity.java      # UI, share intent, inspección y descarga
│       └── res/
│           ├── drawable/ic_linka.xml  # Icono vectorial
│           └── values/
│               ├── strings.xml        # Nombre visible Linka
│               └── styles.xml        # Tema Material nativo, colores
├── build.gradle                       # Plugin com.android.application 9.4.0
├── gradle.properties                 # JVM/AndroidX
├── settings.gradle                   # Repositorios y módulo :app
├── .gitignore                        # Build local, IDE, APK/AAB y SDK local
├── publish-linka.ps1                 # Copiar árbol → sincronizar → Release
├── sync-to-github.sh                 # Commit, push, versionado y etiqueta en servidor
├── README.md                         # Documentación pública para usuarios
└── CODEX.md                          # Este contexto técnico
```

No hay módulos adicionales ni dependencias Java externas declaradas. La UI usa widgets Android estándar y la descarga usa APIs del sistema.

## 4. Plataforma y compilación

- JDK: 17.
- Android Gradle Plugin: 9.4.0.
- Gradle de CI: 9.6.0.
- `compileSdk`: 37.
- `targetSdk`: 36.
- `minSdk`: 29 (Android 10).
- Java source/target compatibility: 17.
- Tarea CI: `gradle --no-daemon --stacktrace assembleDebug`.
- Salida original: `app/build/outputs/apk/debug/app-debug.apk`.
- Nombre de artefacto/asset público: `linka.apk`.

El repositorio no tiene Gradle Wrapper. El workflow instala Gradle 9.6.0 con `gradle/actions/setup-gradle`; Android Studio debe tener un Gradle compatible y descargar Android SDK Platform 37. No hay configuración de signing de distribución: el APK producido es debug-signed.

## 5. Recorrido de ejecución

### Entrada desde Android Share

`app/src/main/AndroidManifest.xml` registra `MainActivity` como launcher y como receptor `android.intent.action.SEND`, categoría `DEFAULT`, MIME `text/plain`. La actividad exportada (`exported=true`) recibe enlaces de navegadores y apps que comparten texto. `launchMode="singleTop"` permite tratar enlaces posteriores en `onNewIntent`.

`MainActivity.onCreate` construye la interfaz y llama `acceptSharedIntent(getIntent())`. `onNewIntent` actualiza el Intent de la actividad y vuelve a procesar el contenido. Se lee `Intent.EXTRA_TEXT`, se extrae la primera URL con `Patterns.WEB_URL`, se quita puntuación final habitual y se antepone `https://` si no venía un esquema.

### Interfaz

`buildUi()` compone la pantalla con `ScrollView`, `LinearLayout`, `TextView`, `EditText` y `Button`. Los textos visibles están en español. `styles.xml` define el tema base; `MainActivity` aplica la paleta oscura y el acento azul en la UI dinámica. `ic_linka.xml` es un VectorDrawable.

El usuario puede:

1. compartir una URL y dejar que Linka la prellene y analice;
2. pegar una URL y tocar **Analizar enlace**;
3. iniciar la descarga si la respuesta se reconoce como medio directo.

### Validación de dirección

`inspectCurrentUrl()` normaliza la entrada, permite solo esquemas HTTP/HTTPS y rechaza estos hosts antes de consultar el contenido:

- `youtube.com` y subdominios, más `youtu.be`;
- `instagram.com` y subdominios;
- `facebook.com` y subdominios, más `fb.watch`.

El bloqueo comprueba el host parseado, no una coincidencia arbitraria en todo el texto de la URL. Si el host se rechaza, la UI no ofrece descarga y sugiere la función oficial del servicio.

### Inspección del tipo

La red corre en un `ExecutorService` de un hilo único para mantener fluida la UI. `inspectContentType()` hace una petición `HEAD`, con timeout de 8 segundos y seguimiento de redirects. Si el servidor responde 405 o 501, repite con `GET` y `Range: bytes=0-0`. La respuesta se normaliza a minúsculas y sin parámetros MIME.

`isSupportedMediaType()` admite respuestas MIME que empiezan con `audio/` o `video/`. Si el MIME es genérico/ausente y no es `text/html`, también puede reconocer una de estas extensiones en la ruta: `.mp3`, `.m4a`, `.aac`, `.mp4`, `.webm`, `.mov`. Una página HTML o un enlace cuyo tipo no coincida queda fuera del flujo.

Cuando vuelve una petición asíncrona, la UI solo aplica el resultado si la URL inspeccionada sigue siendo la misma. Los errores de red se convierten en un resultado no compatible; no se conserva ningún estado de autenticación.

### Descarga

`startDownload()` crea `DownloadManager.Request` para la URL inspeccionada. Deriva el nombre del último segmento de ruta; si no hay nombre usa `linka-media`, y reemplaza caracteres fuera de `[A-Za-z0-9._-]` por `_`. La petición guarda en `Environment.DIRECTORY_DOWNLOADS`, permite datos móviles, prohíbe roaming y solicita una notificación visible al terminar.

La aplicación no abre streams, no copia cookies, no controla la transferencia byte a byte ni solicita permisos amplios de almacenamiento. `DownloadManager` administra la descarga; las restricciones de red, certificados, redirect o servidor pueden hacer que falle.

## 6. Privacidad y seguridad

- Único permiso declarado por la app: `android.permission.INTERNET`.
- No existe backend Linka al que se envíen enlaces.
- No se han agregado SDKs de analítica, publicidad o cuentas.
- La dirección se consulta directamente desde el teléfono y se vuelve a entregar a `DownloadManager` para descargar.
- El usuario debe ser dueño del archivo o tener permiso para conservarlo.
- La app permite HTTP además de HTTPS actualmente; no describirla como HTTPS-only.
- Solo dominios sociales explícitos están en la lista de rechazo. No prometer que sea una lista exhaustiva de toda web con restricciones.
- No se incorpora en Git ninguna llave SSH privada, deploy key, secreto de firma, token o contraseña.
- El repositorio ahora es público; todos los archivos y todo el historial Git son visibles. Revisar secretos antes de añadir cualquier archivo o commit.

## 7. Versiones, CI y Releases

El workflow `.github/workflows/android.yml` ejecuta el job `build` en cada push a `main`, en tags `v*` y manualmente (`workflow_dispatch`). Usa checkout v6, setup-java v6, setup-gradle v6 (Gradle 9.6.0, cache básico), upload-artifact v6 y Android SDK disponible en runner. El artefacto de Actions se llama `linka.apk` y se conserva 30 días.

Para tags `v*`, el job `release` espera a `build`, descarga el artefacto y crea/actualiza un GitHub Release con `softprops/action-gh-release@v3`. Solo el job de publicación tiene `contents: write`; el de compilación tiene lectura. El Release incluye `linka.apk` y notas automáticas de GitHub.

Cada publicación estándar sube el código y genera la próxima versión patch:

1. lee el último tag `vMAJOR.MINOR.PATCH`;
2. incrementa `PATCH` y `versionCode`, y actualiza `versionName` en `app/build.gradle`;
3. crea un commit en `main`;
4. empuja `main` y una etiqueta anotada como `v1.0.1`;
5. GitHub Actions compila el APK y adjunta el archivo al Release correspondiente.

Los cambios de documentación también crean una nueva versión/Release, porque el flujo solicitado publica un APK por tanda de cambios. Si no hay cambios preparados, el script servidor termina sin commit ni Release. No reutilizar etiquetas publicadas.

## 8. Flujo de publicación de un solo comando

### Windows / estación que edita el proyecto

Desde PowerShell, en la raíz del clon, ejecutar:

```powershell
.\publish-linka.ps1 -Message "Describe el cambio"
```

El script:

1. obtiene los archivos versionados, nuevos no ignorados y eliminados del árbol local;
2. copia cada archivo al clon canónico `/home/ricardo/proyects/Linka` por SCP y propaga eliminaciones;
3. ejecuta `sync-to-github.sh` por SSH;
4. espera resultado correcto del commit, push y tag;
5. cambia el remoto local a HTTPS público, hace fetch y alinea el clon de Windows con `origin/main`.

Requisitos de la estación: Windows PowerShell, Git, OpenSSH `ssh`/`scp`, conectividad SSH y alias `serveras` correctamente configurado. El mensaje es una línea. El script restringe nombres de archivo a letras ASCII, números, puntos, guion, guion bajo y `/` para no interpolar rutas arbitrarias en comandos SSH.

### Servidor

El script de servidor vive en `sync-to-github.sh`; debe conservar permiso ejecutable. Su identidad Git local es Ricardo Luciano con correo noreply de GitHub. Usa la clave dedicada `~/.ssh/linka_github` con `IdentitiesOnly=yes`; no depende de un token personal.

La invocación de bajo nivel para cambios ya copiados al servidor es:

```bash
cd /home/ricardo/proyects/Linka
./sync-to-github.sh "Describe el cambio" --release
```

El camino recomendado para el equipo que trabaja desde Windows es el publicador PowerShell de un solo comando. Los cambios externos al servidor (por ejemplo, merge de PRs) deben incorporarse al checkout antes de volver a publicar. Si `git pull --rebase` detecta un conflicto real, detenerse y resolverlo; no usar `reset --hard` en el servidor.

### Estado esperado al terminar

- La carpeta canónica del servidor está en `main`, limpia y apunta a `origin/main`.
- El repo público contiene el mismo contenido y commit que el servidor.
- La etiqueta `vX.Y.Z` apunta a ese release commit.
- GitHub Actions muestra `build` y `release` satisfactorios.
- `linka.apk` aparece como asset en la página del Release.
- La estación Windows se actualiza a `origin/main` cuando el publicador finaliza con éxito.

Si el build falla, inspeccionar el job `build` antes de repetir la publicación. Si `build` pasa y el job `release` falla, revisar el permiso `contents: write`, el artefacto `linka.apk` y el log de `softprops/action-gh-release`; corregir y reintentar el job, sin crear otra etiqueta para el mismo cambio.

## 9. Convenciones de mantenimiento

- Mantener el código UI y el flujo de share en `MainActivity.java` mientras siga siendo una única pantalla pequeña. Extraer clases cuando exista una responsabilidad independiente que lo justifique.
- La red nunca debe ejecutarse en el hilo principal.
- Evitar permisos que no hagan falta y nunca guardar credenciales de plataformas externas.
- Mantener identificadores, `namespace` y package Java alineados en `app/build.gradle` y el árbol fuente.
- Mantener `versionCode` creciente y `versionName` igual al tag de Release tras cada publicación.
- Si se cambia el proceso de publicación, actualizar juntos `README.md`, este `CODEX.md`, `publish-linka.ps1`, `sync-to-github.sh` y el workflow, y hacer una publicación de extremo a extremo.
- Añadir y ejecutar tests solo cuando el usuario lo pida; la comprobación de Release requiere además observar el workflow real de GitHub Actions.
- No afirmar que se descargan plataformas sociales, hay transcodificación, firma de distribución o pruebas en dispositivo hasta que estén implementadas y verificadas.

## 10. Estado base conocido

- Primera versión publicada: `v1.0.0`.
- Implementación: share target, entrada manual, inspección MIME, bloqueo explícito de plataformas sociales y descarga directa con `DownloadManager`.
- Build CI: disponible en la pestaña **Actions**.
- Release con APK: disponible en **Releases**.
- Test suite de Android: aún no existe.
- UI: pantalla nativa programática; no se usa Compose ni layout XML para la pantalla.
- Firma: debug; no hay keystore privado ni firma de Play Store.
