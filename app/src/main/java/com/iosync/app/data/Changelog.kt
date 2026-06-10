package com.iosync.app.data

data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<String>
)

val appChangelog = listOf(
    ChangelogEntry(
        version = "4.4.7",
        date = "10.06.2026",
        changes = listOf(
            "Fix: Uhr erreicht HTTP-Adapter wieder (Klartext-Traffic auf der Uhr erlaubt)",
            "Fix: App stuerzt nicht mehr ab, wenn Adapter-Switch ohne gueltigen Host umgelegt wird",
            "Diagnose: Uhr zeigt die echte Fehlerursache (z. B. HTTP 401) statt 'Adapter nicht erreichbar'"
        )
    ),
    ChangelogEntry(
        version = "4.4.6",
        date = "10.06.2026",
        changes = listOf(
            "Diagnose: Uhr zeigt 'Keine Verbindungs-Config', falls keine Adapter-Daten ankommen",
            "Diagnose: Uhr zeigt 'Adapter nicht erreichbar' bei fehlgeschlagenem Datenpunkt-Abruf",
            "Fix: fehlgeschlagene Datenpunkt-Abrufe werden jetzt protokolliert (vorher still verworfen)"
        )
    ),
    ChangelogEntry(
        version = "4.4.5",
        date = "10.06.2026",
        changes = listOf(
            "Button-Uebertragung zu ioBroker verifiziert (Pillen, Slider)",
            "Versionierung vor jedem Push sichergestellt"
        )
    ),
    ChangelogEntry(
        version = "4.4.4",
        date = "09.06.2026",
        changes = listOf(
            "Fix: Verbindungsconfig wird nach Speichern sofort an die Uhr uebertragen",
            "Layout: Untere Komplikations-Ringe weiter nach unten und aussen",
            "Layout: Uhrzeit-Anzeige leicht nach links und oben verschoben"
        )
    ),
    ChangelogEntry(
        version = "4.4.3",
        date = "09.06.2026",
        changes = listOf(
            "v5-Architektur: Watchface fragt ioBroker, Wetter & Health eigenstaendig ab",
            "Handy sendet nur noch Konfiguration + Akku-Stand (kein Wert-Sync mehr)",
            "BC1/BC2: Komplikations-Ringe frei ein-/ausschaltbar (Farbe1/2, Min/Max)",
            "BC1: Herzrate oder frei waehlbarer ioBroker-Datenpunkt",
            "BC2: KCal / Sauerstoff / Blutdruck / Training, optional ioBroker-Datenpunkt",
            "IoSyncSyncService verschlankt: nur noch Handy-Akku-Push",
            "WatchFaceTriggerListenerService: nur noch NTP-Offset-Empfang",
            "Neues Watchface-Hintergrundbild (Page 1)"
        )
    ),
    ChangelogEntry(
        version = "4.4.2",
        date = "08.06.2026",
        changes = listOf(
            "NTP-Zeitkorrektur persistiert ueber Neustart (SharedPreferences)",
            "Sekunden-Ring: konfigurierbare Farbe, Breite & Glow",
            "Backup & Wiederherstellen aller Einstellungen als .ios-Datei",
            "Health Connect: pro Datentyp individuelle Quelle konfigurierbar",
            "SSE-Push vom ioBroker-Adapter fuer Echtzeit-Updates auf der Uhr",
            "Page-2-Slider: Tap-Bedienung (Wisch blockiert System-Gesten)"
        )
    ),
    ChangelogEntry(
        version = "4.4.1",
        date = "06.06.2026",
        changes = listOf(
            "WatchDataSyncManager: eigener Orchestrator auf der Uhr",
            "NTP-Zeitkorrektur: Offset alle 6 h erneuert, sofort bei Config-Empfang",
            "ioBroker-Slots auf der Uhr: umschaltbare Aktionspillen per Tap",
            "Verbindungs-Config-Kanal /iosync/watchface/connection eingefuehrt",
            "Build: OPENWEATHER_API_KEY ueber local.properties + CI Secret"
        )
    ),
    ChangelogEntry(
        version = "4.3.0",
        date = "01.06.2026",
        changes = listOf(
            "Foreground-Service IoSyncSyncService: stabiler Hintergrund-Sync",
            "Health Connect Integration (Herzrate, Sauerstoff, Kalorien, Schritte)",
            "Wetter-Widget auf der Uhr via OpenWeatherMap",
            "Crash-Log-System mit Anzeige in den Einstellungen",
            "Watchface Page 2: Slider + Aktionspillen"
        )
    )
)
