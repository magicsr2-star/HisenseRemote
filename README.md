# Hisense Remote — APK nativa Android (Kotlin, sin Termux, sin PC)

TV: Hisense HU50N6000UW, 172.30.218.102, MQTT 36669 legacy
Usuario `hisenseservice` / password `multimqttservice`, sin PIN.
Topic: `/remoteapp/tv/remote_service/user4321$normal_v4/actions/sendkey`
Payload: texto plano, ej. `KEY_POWER`

La app habla MQTT directo con Paho, en el mismo WiFi.

## Compilar desde Android, sin PC (recomendado)
GitHub Actions compila por ti en la nube:
1. En github.com, crea un repo nuevo vacío, ej. `HisenseRemote`.
2. Sube el ZIP de este proyecto (Add file → Upload files, descomprime
   antes en el celular con tu explorador de archivos).
   Debe quedar `.github/workflows/build-apk.yml` en el repo.
3. Ve a Actions → Build APK → Run workflow, o haz un push a `main`.
4. Cuando termine (unos 3-5 min), entra al run → Artifacts →
   `HisenseRemote-debug` → descarga el ZIP, saca el `app-debug.apk`
   e instálalo en tu moto g84.

No necesitas Android Studio en el celular.
Alternativa en el celular (más lenta, no recomendada): apps
AndroidIDE / CodeAssist, que compilan Gradle en el dispositivo,
necesitan varios GB y tardan mucho en un moto g84.

## Compilar en Android Studio (PC)
1. Copia esta carpeta `hisense-remote-apk` a tu PC.
2. Android Studio → Open → elige la carpeta.
   Deja que descargue Gradle 8.7, AGP 8.5.2 y Kotlin 1.9.24.
3. Conecta tu moto g84 por USB, activa Depuración USB, o usa
   Build → Build App Bundle(s) / APK(s) → Build APK(s).
4. El APK debug queda en:
   `app/build/outputs/apk/debug/app-debug.apk`
   Pásalo al celular e instálalo (permite "instalar apps desconocidas").

O por terminal, en la carpeta del proyecto:
```
./gradlew assembleDebug
```

## Novedades v2.0
- Diseno nuevo como en la imagen de referencia: fondo oscuro, titulo
  "Hisense Remote" y subtitulo "HU50N6000UW · MQTT · legacy".
- Boton de encendido grande y redondo = boton de conectar: rojo
  cuando no esta conectada, VERDE cuando la conexion MQTT se confirma.
  Al abrir la app conecta sola. Si ya esta conectada, tocarlo envia
  KEY_POWER (apaga/enciende la TV).
- Ya NO se ve la IP en pantalla. Para cambiarla: pulsacion larga
  sobre el titulo "Hisense Remote", se guarda en el telefono.
- Atajos Netflix y YouTube (botones circulares negros).
- Boton TECLADO: abre un dialogo con teclado numerico 0-9 (sendkey,
  probado) y un campo de texto con el teclado QWERTY del telefono
  para escribir en la TV.
- D-pad azul con OK dorado, ATRAS/HOME/SALIR grises, MUTE/MENU
  turquesa, filas "— VOL +" y "— CANAL +", PLAY verde y PAUSA morada.

### A probar en la TV (no verificado aun en la HU50N6000UW)
1. **Netflix / YouTube**: los botones publican en
   `/remoteapp/tv/remote_service/user4321$normal_v4/actions/launchapp`
   con payload "netflix" / "youtube" (mecanismo documentado de VIDAA).
   Si la TV los ignora, el siguiente paso es capturar lo que envia
   la app oficial al abrir esas apps.
2. **Escribir texto**: el boton Enviar del dialogo publica el texto en
   `.../actions/input`. Si no funciona, usar el teclado numerico +
   el D-pad para moverse por el teclado en pantalla de la TV.

## Usar
1. Celular y TV en el mismo WiFi DEPARTAMENTOS_24.
2. Abre la app: conecta sola. El boton de encendido se pone verde.
3. Si dice que no conecta, la TV puede estar apagada del todo:
   toca el boton rojo de encendido (manda Wake-on-LAN con la MAC
   10:c7:53:19:5c:f4 y luego intenta conectar), espera unos segundos.
4. Para probar, toca Vol +.

## Archivos clave
- `app/src/main/java/com/vts/hisenseremote/TvRemote.kt` — MQTT + WOL
- `app/src/main/java/com/vts/hisenseremote/MainActivity.kt` — botones
- `app/src/main/res/layout/activity_main.xml` — diseño del control
- `app/build.gradle.kts` — dependencia Paho MQTT 1.2.5

## Nota honesta
Este ZIP es el proyecto fuente, no el APK ya compilado:
aquí no hay Android SDK / Java para compilarlo.
En Android Studio compila en el primer intento si tienes internet
para bajar las dependencias. Si te da error de Gradle, dime el mensaje
exacto y lo ajustamos.
