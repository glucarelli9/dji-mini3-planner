# DJI Mini 3 Waypoint Planner

Android app para vuelo autónomo con el DJI Mini 3, usando el SDK V5 oficial.

## Qué hace

- Mapa satelital: tocás el mapa para agregar waypoints
- Control de altitud (5–120 m) y velocidad (1–15 m/s) por sliders
- Genera el archivo WPMZ (formato nativo DJI), lo sube al drone y arranca la misión automáticamente
- Estado de vuelo en tiempo real (Subiendo / Volando / Completado / etc.)
- Botón de stop de emergencia
- Export WPMZ local para usar con DJI Fly / Pilot 2

## Cómo funciona

```
WPMZExporter  →  pushKMZFileToAircraft()  →  startMission()
                 (DJI SDK V5.15.0)
```

El formato WPMZ fue reverse-engineered de `libwpmz_jni.so` del APK de DJI GO v5:
- `droneEnumValue=89` → DJI Mini 3
- `wpmz/template.xml` → configuración de la misión (velocidad, acción al terminar, etc.)
- `wpmz/waylines.wpml` → coordenadas GPS de cada waypoint

## Build

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requiere JDK 21 + Android SDK 34.

**Package:** `com.srk.mini3wp`
**SDK:** DJI Mobile SDK V5 v5.15.0
