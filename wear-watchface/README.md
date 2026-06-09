# IoSync Watchface

Wear OS 4 Watchface (Jetpack Watch Face API). Ab **v5** ruft die Uhr ioBroker-Datenpunkte
und das Wetter direkt selbst ab — das Handy liefert nur noch die Konfiguration.

## Build-Voraussetzungen

> ⚠️ Builds **niemals lokal auf dem Raspberry Pi** ausführen — nur über GitHub Actions.

### OpenWeather API-Key

Da die Uhr das Wetter ab v5 direkt abruft, benötigt das `wear-watchface`-Modul den
OpenWeatherMap-API-Key zur Build-Zeit. Er wird in `BuildConfig.OPENWEATHER_API_KEY` injiziert
(siehe `build.gradle.kts`). Es gibt zwei Wege — `System.getenv` hat Vorrang vor `local.properties`:

**Lokal (`local.properties` im Projekt-Root, gitignored):**

```properties
sdk.dir=/pfad/zum/android-sdk
OPENWEATHER_API_KEY=dein_openweather_key
```

**CI (GitHub Actions):** Als Repository-Secret `OPENWEATHER_API_KEY` hinterlegen. Der
Build-Step in `.github/workflows/release.yml` reicht es per Umgebungsvariable an beide
Module durch:

```yaml
- name: Alle APKs bauen
  env:
    OPENWEATHER_API_KEY: ${{ secrets.OPENWEATHER_API_KEY }}
  run: ./gradlew :app:assembleRelease :wear-watchface:assembleRelease --no-daemon
```

Fehlt der Key, wird ein leerer String gesetzt — das Watchface baut, zeigt aber keine
Wetterdaten an.
