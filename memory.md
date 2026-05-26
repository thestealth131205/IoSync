# memory.md — IoSync Arbeitsgedächtnis

Letzte Aktualisierung: 2026-05-18

## Was wurde erstellt (Grundgerüst v1.0)

Das vollständige Projektgrundgerüst wurde unter `/opt/Iosync/` angelegt. Alle Dateien sind direkt kompilierbar.

### Android-Module (Gradle Multi-Module)

**`app/`** — Smartphone-App
- `IoSyncApplication.kt` — Hilt-Application-Klasse
- `MainActivity.kt` — Entry Point, NavHost mit 3 Routen (home, detail/{id}, settings)
- `data/model/SmartHomeState.kt` — Kerndatenmodell (id, name, value, type, timestamp, unitOfMeasure, icon)
- `data/network/ApiService.kt` — Retrofit-Interface: GET /states, GET /state/{id}, POST /state/{id}, GET /history/{id}
- `data/network/WebSocketManager.kt` — OkHttp-WebSocket mit StateFlow (Connected/Disconnected/Error/MessageReceived)
- `data/network/SmartHomeWebSocketService.kt` — höherstufige WebSocket-Abstraktion, parsed JSON zu SmartHomeState
- `data/repository/SmartHomeRepository.kt` — kombiniert REST + WebSocket, Cache via Map
- `wear/WearDataLayerService.kt` — schreibt SmartHomeState-Liste als JSON in den Data Layer (`/iosync/smart-home-states`)
- `di/NetworkModule.kt` — Hilt-Provider für Retrofit, OkHttpClient, ApiService, WebSocketManager
- `di/AppModule.kt` — Hilt-Provider für SmartHomeRepository, WearDataLayerService
- `ui/theme/Color.kt` — Dark Theme, Neon-Gelb #EAFF00 als Primärfarbe
- `ui/theme/Theme.kt` — Material 3 Dark ColorScheme, IoSyncTheme-Composable
- `ui/theme/Type.kt` — Typography-Definitionen
- `ui/viewmodel/MainViewModel.kt` — StateFlow: states, selectedState, connectionStatus, serverUrl, apiKey
- `ui/screens/HomeScreen.kt` — Liste aller SmartHome-Datenpunkte, Verbindungsindikator, Pull-to-Refresh
- `ui/screens/DetailScreen.kt` — Detailansicht eines Datenpunkts mit Historien-Chart
- `ui/screens/SettingsScreen.kt` — Server-URL + API-Key konfigurieren, in DataStore speichern

**`wear-app/`** — Wear OS App
- `WearApplication.kt` — Hilt-Application
- `WearDataListenerService.kt` — empfängt Data Layer Events von der Smartphone-App
- `data/model/SmartHomeState.kt` — spiegelbildliches Datenmodell
- `data/repository/WearRepository.kt` — StateFlow der aktuellen States, wird vom Listener befüllt
- `di/AppModule.kt` — Hilt-Provider
- `presentation/theme/` — Wear OS Dark Theme mit Neon-Gelb
- `presentation/viewmodel/WearViewModel.kt` — leitet States aus Repository weiter
- `presentation/screens/MainScreen.kt` — Compose for Wear OS: ScalingLazyColumn mit State-Karten
- `MainActivity.kt` — Wear OS Entry Point

**`wear-watchface/`** — Wear OS 4 Watchface
- `IoSyncWatchFaceService.kt` — erbt von WatchFaceService, erstellt den Renderer
- `renderer/IoSyncWatchFaceRenderer.kt` — CanvasRenderer: digitale Uhrzeit (Neon-Gelb), Datum, Complication-Slot (oben), ioBroker-Datenpunkt-Anzeige (unten)
- `WatchFaceConfigActivity.kt` — ComponentActivity für Watchface-Konfiguration
- `datalayer/WatchFaceDataListenerService.kt` — empfängt SmartHome-Updates aus dem Data Layer, speichert in SharedPreferences
- `AndroidManifest.xml` — korrekte Wear OS 4 Metadaten, WAKE_LOCK + RECEIVE_BOOT_COMPLETED Permissions, watch_face.xml Referenz
- `res/xml/watch_face.xml` — WatchFace-Deklaration mit Complication-Slot

**Gradle-Konfiguration**
- `settings.gradle.kts` — inkludiert app, wear-app, wear-watchface
- `build.gradle.kts` (root) — Android/Kotlin/Hilt Plugin-Versionen
- `gradle/libs.versions.toml` — zentrale Version Catalog (Compose 1.6.0, Material3 1.2.0, Hilt 2.51, Retrofit 2.11.0, OkHttp 4.12.0, Wear OS libs, Watchface API)
- `gradle.properties` — Kotlin Compose enabled, AndroidX enabled

### ioBroker Plugin (`iobroker-plugin/`)

- `main.js` — vollständiger Adapter: `onReady()` initialisiert Konfiguration, `onStateChange()` überwacht alle subscrizierten Datenpunkte, `sendToAndroid()` sendet per HTTP-POST + optionalem WebSocket
- `io-package.json` — Adapter-Metadaten: Name "iosync", Version 1.0.0, Objekt-Schema für serverUrl, apiKey, dataPoints, publishInterval
- `package.json` — Abhängigkeiten: @iobroker/adapter-core, axios, ws
- `tsconfig.json` — TypeScript-Konfiguration (für optionale TS-Migration)
- `admin/index_m.html` — Admin-UI mit Formular für Server-URL, API-Key, Datenpunkt-Konfiguration

## Offene Punkte / Nächste Schritte

- [ ] Echte ioBroker-Datenpunkt-IDs in Plugin konfigurieren und testen
- [ ] Backend-API (ioBroker / Home Assistant REST-Endpoint) an `serverUrl` anbinden
- [ ] Complication-Provider im Watchface vollständig implementieren (aktuell Platzhalter)
- [ ] App-Icons (echte PNG-Assets) ersetzen (aktuell XML-Vektoren als Platzhalter)
- [ ] Signing-Konfiguration für Release-Build einrichten
- [ ] Play Services Availability-Check in WearDataLayerService robuster machen
- [ ] Unit Tests für Repository und ViewModel
- [ ] Wear OS Tile als weiteres Modul (optionale Erweiterung)

## Bekannte Einschränkungen des Grundgerüsts

- Mipmap-Icons sind aktuell nur als adaptive-icon XML angelegt (kein PNG)
- `preview.xml` und `preview_circular.xml` im Watchface sind Platzhalter-Vektoren
- Das ioBroker-Plugin verwendet JavaScript (kein TypeScript kompiliert), `tsconfig.json` ist für spätere Migration vorbereitet
- Die Watchface-Complication ist als Slot deklariert, der Complication-Provider muss noch als eigener Service implementiert werden

## Architektur-Entscheidungen

- **Data Layer Pfad:** `/iosync/smart-home-states` (JSON-Array aller States)
- **WebSocket-URL-Schema:** `ws://SERVER:PORT/ws` (konfigurierbar in Settings)
- **REST-Base-URL:** `http://SERVER:PORT/api/` (konfigurierbar in Settings)
- **ioBroker → Android:** HTTP-POST an `/api/state/{id}` mit JSON-Body `{value, timestamp}`
- **Datenmodell-JSON-Key für Data Layer:** `states` (Gson-serialisiertes Array)
