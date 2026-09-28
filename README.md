# Linka

Linka es una aplicación Android personal que recibe enlaces compartidos y descarga archivos de audio o video cuando el enlace apunta directamente a un archivo accesible y tienes derecho a guardarlo.

## MVP

- Aparece como destino al compartir enlaces desde otras aplicaciones Android.
- Comprueba si el enlace apunta a un archivo directo de audio o video.
- Descarga el archivo original en Descargas mediante el gestor de Android.
- No extrae streams de plataformas, elude protecciones ni convierte formatos.

Los enlaces de YouTube, Instagram y Facebook se rechazan con una explicación. Usa las opciones oficiales de descarga del servicio o enlaces directos a archivos propios o autorizados. El MVP conserva el formato y la resolución del archivo fuente.

## Compilar

Abre el proyecto en Android Studio con JDK 17, Android SDK API 37 y Gradle 9.6.0 (AGP 9.4.0). La acción de GitHub compila el APK de depuración y lo publica como artefacto descargable llamado linka.apk.

Para instalarlo en tu dispositivo, descarga el artefacto de la acción más reciente. El proyecto no incluye una clave de firma privada; las claves deben mantenerse fuera del repositorio.
