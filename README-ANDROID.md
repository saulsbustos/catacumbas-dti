# Catacumbas DTI - Android 7.1.2+

Proyecto Java Android nativo. No requiere Android Studio local; GitHub Actions compila `app-debug.apk`.

## Instalación del proyecto
1. Descomprime el ZIP en tu computadora.
2. En el repositorio público `saulsbustos/catacumbas-dti`, usa **Add file > Upload files** y arrastra **las carpetas `app` y `.github` y los archivos `build.gradle`, `settings.gradle`, `gradle.properties`** (respetando las rutas). Si la interfaz web no conserva directorios, usa GitHub Desktop o `git` para subir el proyecto.
3. Abre **Actions > Compilar APK Android > Run workflow**, o espera al flujo automático tras subir el código.
4. Descarga el artifact `catacumbas-dti-debug-apk` y extrae `app-debug.apk`.
5. Copia el APK al teléfono, autoriza la instalación desde el navegador/gestor de archivos, instala y concede permiso de cámara.

## Comportamiento
- Consulta automáticamente `https://api.github.com/repos/saulsbustos/catacumbas-dti/contents?ref=main` al abrirse; botón manual para sincronizar.
- Acepta MP3 directamente en la raíz del repositorio con nombres sencillos (letras, números, guion, guion bajo, punto).
- Compara SHA de GitHub para descargar solo archivos nuevos o cambiados, guarda en almacenamiento privado y funciona sin conexión después de sincronizar.
- Cámara trasera con análisis simple de movimiento; pantalla encendida a brillo reducido durante vigilancia.
- Reproduce MP3 localmente, uno a la vez, sin repetir inmediatamente el anterior.

## Limitaciones y pruebas necesarias
- **Prototipo sin compilar ni probar en dispositivo**: revisar el resultado de GitHub Actions y probar en Android 7.1.2.
- Android Camera1 es una API antigua, adecuada para Android 7 pero puede requerir ajustes en modelos específicos.
- El brillo vuelve al valor de sistema al detener vigilancia; mantener el celular conectado y vigilar temperatura.
- La API pública de GitHub tiene límites de uso. Si GitHub falla, los sonidos existentes se conservan.
- La sincronización mientras un MP3 se reproduce debe evitarse en esta primera versión: sincronizar antes de iniciar vigilancia.
- No es una aplicación firmada para distribución; el APK debug es para pruebas.
