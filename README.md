# Linka

Linka es una aplicación Android personal que recibe enlaces compartidos y descarga archivos de audio o video cuando el enlace apunta directamente a un archivo accesible y tienes derecho a guardarlo.

## MVP

- Aparece como destino al compartir enlaces desde otras aplicaciones Android.
- Comprueba si el enlace apunta a un archivo directo de audio o video.
- Descarga el archivo original en Descargas mediante el gestor de Android.
- No extrae streams de plataformas, elude protecciones ni convierte formatos.

Los enlaces de YouTube, Instagram y Facebook se rechazan con una explicación. Usa las opciones oficiales de descarga del servicio o enlaces directos a archivos propios o autorizados. El MVP conserva el formato y la resolución del archivo fuente.

## Compilar e instalar

Abre el proyecto en Android Studio con JDK 17, Android SDK API 37 y Gradle 9.6.0 (AGP 9.4.0). La acción `Android APK` compila el APK de depuración `linka.apk`.

Los APK de compilación continua aparecen como artefactos en GitHub Actions. Las versiones etiquetadas como `vMAJOR.MINOR.PATCH` se publican en GitHub Releases con el APK adjunto. El APK está firmado con la clave de depuración de CI; no se incluye una clave privada de firma de distribución.

## Sincronización del servidor

La carpeta de trabajo del servidor es `/home/ricardo/proyects/Linka`. La clave SSH dedicada del servidor tiene acceso de escritura únicamente a este repositorio.

Después de copiar los cambios completos a esa carpeta, ejecuta:

```bash
cd /home/ricardo/proyects/Linka
./sync-to-github.sh "Describe el cambio"
```

El script incorpora los cambios, crea un commit y los sube a `main`. Para publicar una versión y disparar la creación del Release con su APK:

```bash
./sync-to-github.sh "Preparar Linka 1.0.0" v1.0.0
```

Primero comprueba que el nombre de la etiqueta no esté ya usado. Cada cambio subido queda guardado en el historial de Git.
