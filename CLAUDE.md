# CLAUDE.md — IoSync Project

Dieses Dokument beschreibt die Projektstruktur und Konventionen für das **IoSync**-Projekt.

## Projektübersicht

**IoSync** ist ein modulares Ökosystem, das als Schnittstelle zwischen ioBroker / Home Assistant und Android dient. Es besteht aus vier unabhängigen Modulen:

| Modul | Pfad | Zweck |
|-------|------|-------|
| `app` | `app/` | Smartphone-Hauptapp (Kotlin, Compose, MVVM, Hilt) |
| `wear-app` | `wear-app/` | Standalone Wear OS App (Compose for Wear OS) |
| `wear-watchface` | `wear-watchface/` | Wear OS 4 Watchface (Jetpack Watch Face API) |
| `iobroker-plugin` | `iobroker-plugin/` | ioBroker-Adapter (Node.js / JavaScript) |

## Build-Befehle

```bash
# Vom Projektroot /opt/Iosync/
./gradlew :app:assembleDebug           # Smartphone-APK
./gradlew :wear-app:assembleDebug      # Wear OS APK
./gradlew :wear-watchface:assembleDebug # Watchface APK
./gradlew build                        # Alle Android-Module

# ioBroker Plugin (Node.js)
cd iobroker-plugin/
npm install
node main.js                           # Lokal testen
```

**Wichtig:** Gradle nur auf explizite Anweisung ausführen — Builds sind langsam.

## Architektur

### Android `app/` (MVVM + Hilt)

```
UI Layer (Compose Screens: HomeScreen, DetailScreen, SettingsScreen)
    └── MainViewModel.kt
         ├── SmartHomeRepository.kt
         │    ├── ApiService.kt (Retrofit REST)
         │    └── SmartHomeWebSocketService.kt (OkHttp WebSocket)
         └── WearDataLayerService.kt (Play Services Data Layer)
```

- **Package:** `com.iosync.app`
- **DI:** Hilt — `di/NetworkModule.kt`, `di/AppModule.kt`
- **Datenmodell:** `data/model/SmartHomeState.kt` (id, name, value, type, timestamp)
- **WebSocket:** `WebSocketManager.kt` kapselt OkHttp-WebSocket-Lifecycle

### Wear OS App `wear-app/`

- **Package:** `com.iosync.wear`
- Empfängt Daten via `WearDataListenerService.kt` aus dem Google Play Services Data Layer
- `WearRepository.kt` stellt die Daten als StateFlow bereit
- `WearViewModel.kt` → `MainScreen.kt` (Compose for Wear OS)

### Wear OS Watchface `wear-watchface/`

- **Package:** `com.iosync.watchface`
- `IoSyncWatchFaceService.kt` erbt von `WatchFaceService` (Jetpack Watch Face API)
- `IoSyncWatchFaceRenderer.kt` — CanvasRenderer, zeichnet digitale Uhrzeit + Complication-Platzhalter
- `WatchFaceDataListenerService.kt` — empfängt SmartHome-Daten aus dem Data Layer
- `WatchFaceConfigActivity.kt` — Konfigurationsscreen für das Watchface
- Complication-Slot: vorbereitet für ioBroker-Datenpunkte

### ioBroker Plugin `iobroker-plugin/`

- Basiert auf `@iobroker/adapter-core`
- `main.js` — Haupt-Adapter-Logik: `onStateChange` überwacht Datenpunkte, sendet per HTTP-POST / WebSocket an die Android-App
- `io-package.json` — Adapter-Metadaten, Objekt-Definitionen
- `package.json` — Node.js-Abhängigkeiten
- Admin-UI: `admin/index_m.html` (Konfigurationsseite im ioBroker-Admin)

## Design-System

- **Theme:** Dark (Deep Black / dunkle Grautöne), standardmäßig aktiv
- **Primärfarbe (Akzent):** Neon-Gelb `#EAFF00`
- **Color.kt:** `app/src/main/java/com/iosync/app/ui/theme/Color.kt`
- **Theme.kt:** `app/src/main/java/com/iosync/app/ui/theme/Theme.kt`
- Wear OS verwendet dasselbe Farbschema: `wear-app/src/main/java/com/iosync/wear/presentation/theme/`

## Wichtige Dateien

| Datei | Zweck |
|-------|-------|
| `settings.gradle.kts` | Multi-Module-Konfiguration (app, wear-app, wear-watchface) |
| `build.gradle.kts` | Root Build-Config, gemeinsame Plugin-Versionen |
| `gradle/libs.versions.toml` | Version Catalog (alle Abhängigkeiten zentral) |
| `app/src/main/java/com/iosync/app/data/model/SmartHomeState.kt` | Kern-Datenmodell |
| `app/src/main/java/com/iosync/app/data/network/WebSocketManager.kt` | OkHttp WebSocket |
| `app/src/main/java/com/iosync/app/wear/WearDataLayerService.kt` | Daten → Wear OS senden |
| `wear-watchface/src/main/java/com/iosync/watchface/renderer/IoSyncWatchFaceRenderer.kt` | Watchface-Rendering |
| `iobroker-plugin/main.js` | ioBroker-Adapter-Logik |
| `iobroker-plugin/io-package.json` | Adapter-Metadaten |

## Konventionen

- **Sprache:** Kotlin (Android), JavaScript/Node.js (ioBroker Plugin)
- **Min SDK:** 26 (app), 30 (wear-app, wear-watchface)
- **Compile/Target SDK:** 34
- **JVM:** 17
- **JSON:** Gson (Android), JSON.parse/stringify (Node.js)
- **Kommentare & Commit-Messages:** Deutsch bevorzugt
- **Kein Auto-Deploy** ohne explizite Anweisung
