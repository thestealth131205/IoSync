package com.iosync.app.data

data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<String>
)

val appChangelog = listOf(
    ChangelogEntry(
        version = "4.7.1",
        date = "11.06.2026",
        changes = listOf(
            "Page 3: LED-Kachel unterstützt Tasmota Power-Geräte (Moonraker Power API)",
            "Page 3: Chamber-Heater-Kachel hinzugefügt (heater_generic)",
            "Page 3: Chamber-Heater-Symbol als Flamme (grau/orange-rot)"
        )
    ),
    ChangelogEntry(
        version = "4.7.0",
        date = "11.06.2026",
        changes = listOf(
            "Page 3: Kacheln vergrößert",
            "Page 3: Beschriftung für LED- und Heater-Kacheln konfigurierbar",
            "Page 3: Schriftgröße per Stufen (+5%/+10%/+15%/+20%) anpassbar"
        )
    ),
    ChangelogEntry(
        version = "4.6.9",
        date = "10.06.2026",
        changes = listOf(
            "Versionsbump für Deploy"
        )
    ),
    ChangelogEntry(
        version = "4.6.8",
        date = "10.06.2026",
        changes = listOf(
            "Watchface Page 3: alle 4 Kacheln vergrößert (füllen den Raum bis kurz vor den runden Rand)",
            "Watchface Page 3: Lüfter-Rad dreht sich animiert, wenn aktiv – Tempo in 10 Stufen abhängig vom %-Wert (max. bei 100 %)",
            "Watchface Page 3: Tap auf die Lüfter-Kachel öffnet einen Slider – Tippen setzt den %-Wert (M106) und der Slider verschwindet sofort"
        )
    ),
    ChangelogEntry(
        version = "4.6.7",
        date = "10.06.2026",
        changes = listOf(
            "Watchface Page 3: Tempo (Druckgeschwindigkeit) und Lüfter (Bauteil-Lüfter) als eigene Kacheln mit Symbol"
        )
    ),
    ChangelogEntry(
        version = "4.6.5",
        date = "10.06.2026",
        changes = listOf(
            "Watchface Page 3: neues Hintergrundbild (gebürstetes Metall) eingefügt"
        )
    ),
    ChangelogEntry(
        version = "4.6.4",
        date = "10.06.2026",
        changes = listOf(
            "Klipper: zentrales Abruf-Intervall (Sekunden) in den Einstellungen – gilt für alle Klipper-Daten",
            "Watchface Page 3: Hintergrundbild entfernt (jetzt schwarz)",
            "Fix: Page-1-Pille wird nicht mehr fälschlich auf Page 3 angezeigt",
            "Watchface Page 3: LED- und Heater-Button als Kacheln mit Status (An/Aus) + leuchtendem Symbol (Lampe/Flamme)",
            "Fix: LED-/Heater-Objekt + G-Codes werden jetzt korrekt an die Uhr übertragen (Status-Anzeige funktioniert)"
        )
    ),
    ChangelogEntry(
        version = "4.6.2",
        date = "10.06.2026",
        changes = listOf(
            "Fix: Klipper-Daten werden jetzt sofort beim Display-Einschalten abgerufen (syncNow inkl. Klipper)",
            "Fix: Race-Condition beim Start — nach loadInitialConfig wird Klipper sofort abgerufen statt bis zu 120s zu warten"
        )
    ),
    ChangelogEntry(
        version = "4.6.1",
        date = "10.06.2026",
        changes = listOf(
            "Fix: Klipper-Aktivierungsstatus (klipperEnabled) wird jetzt korrekt an die Uhr übertragen — Klipper-Loop auf der Uhr startet nun zuverlässig",
            "Fix: Klipper API-Key (X-Api-Key Header) wird bei allen Moonraker-Anfragen der Uhr mitgesendet"
        )
    ),
    ChangelogEntry(
        version = "4.6.0",
        date = "10.06.2026",
        changes = listOf(
            "Watchface: Tipp auf Klipper-Balken (Page 1) wechselt direkt zu Page 3 wenn Klipper aktiv + druckt",
            "Watchface: Boden-Komplikationen weiter nach außen verschoben (bessere Position in den Kreistaschen)"
        )
    ),
    ChangelogEntry(
        version = "4.5.9",
        date = "10.06.2026",
        changes = listOf(
            "Klipper API-Key: Moonraker X-Api-Key Unterstützung (Eingabefeld in Klipper-Einstellungen, wird an Uhr übertragen)",
            "App + Watchface senden X-Api-Key Header bei allen Moonraker-Anfragen wenn gesetzt"
        )
    ),
    ChangelogEntry(
        version = "4.5.8",
        date = "10.06.2026",
        changes = listOf(
            "Slot-4-Balken (Page 1): Klipper-Fortschritt als optionale Datenquelle (Druckfortschritt, Düsen-/Bett-/Kammer-Temp, Lüfter, Geschwindigkeit)",
            "Klipper-Override: Während aktiver Druck (print_stats.state == printing) wird Klipper-Wert mit eigener Farbe angezeigt; sonst bleibt ioBroker-Wert aktiv",
            "App-Einstellungen: Toggle + Quellenauswahl + Aktivfarbe für Slot-4-Klipper-Modus",
            "Moonraker-Abfrage erweitert: print_stats.state wird jetzt für isActive-Erkennung ausgewertet"
        )
    ),
    ChangelogEntry(
        version = "4.5.7",
        date = "10.06.2026",
        changes = listOf(
            "Klipper-Verbindung jetzt unter 'ioBroker Adapter' als aufklappbarer Bereich mit Enable/IP/Port-Button",
            "Page 3: LED-Button und Chamber-Heater-Button: G-Code On/Off + Objekt/Feld in App konfigurierbar",
            "Page 3: Host/Port-Felder aus Dritte-Seite-Abschnitt in Adapter-Abschnitt verschoben",
            "Bugfix: Save-Button in 'Dritte Seite' übergab alle Parameter korrekt (klipperEnabled, LED/Heater G-Codes)"
        )
    ),
    ChangelogEntry(
        version = "4.5.6",
        date = "10.06.2026",
        changes = listOf(
            "Klipper/Moonraker-Integration: 3D-Drucker-API per Port 7125 anbindbar",
            "Page 3 Watchface: Pille auf 6 Uhr zeigt Klipper-Datenpunkt an und schaltet per Doppeltipp",
            "App-Einstellungen: Klipper-Host/Port konfigurierbar, Drucker-Objekte werden live geladen",
            "Pill-Farben, G-Code-Befehle (Ein/Aus) und Abfragefeld frei konfigurierbar"
        )
    ),
    ChangelogEntry(
        version = "4.5.5",
        date = "10.06.2026",
        changes = listOf(
            "Dritte Watchface-Seite (Page 3) eingebaut",
            "Navigation: Doppeltipp 12 Uhr auf Seite 2 → Seite 3, Doppeltipp 12 Uhr auf Seite 3 → Seite 1",
            "Seiten-Indikator (3 Punkte) auf Seite 3 zeigt aktive Seite an"
        )
    ),
    ChangelogEntry(
        version = "4.5.4",
        date = "10.06.2026",
        changes = listOf(
            "Boden-Ringe (Puls + Kcal/Oxygen) weiter nach außen verschoben und verkleinert",
            "Passen jetzt sauber in die Kreistaschen des Hintergrunds"
        )
    ),
    ChangelogEntry(
        version = "4.5.3",
        date = "10.06.2026",
        changes = listOf(
            "Fix: Aktions-Pille (Seite 1) blitzt beim Aufwachen nicht mehr kurz als 'aktiv' auf",
            "Pille startet deaktiviert und zeigt 'aktiv' erst nach bestaetigtem Datenpunkt-Abruf"
        )
    ),
    ChangelogEntry(
        version = "4.5.2",
        date = "10.06.2026",
        changes = listOf(
            "Neu: Schriftgröße je Boden-Komplikation (Puls + Kcal/Oxygen/ioBroker) einstellbar",
            "Größen-Auswahl von 70 % bis 160 % – zwei Stufen kleiner und mehrere größer"
        )
    ),
    ChangelogEntry(
        version = "4.5.1",
        date = "10.06.2026",
        changes = listOf(
            "Neu: Boden-Komplikation rechts kann auf ioBroker-Datenpunkt gestellt werden",
            "Neu: Datenpunkt-Picker mit Suche zum schnellen Finden eines Datensatzes"
        )
    ),
    ChangelogEntry(
        version = "4.5.0",
        date = "10.06.2026",
        changes = listOf(
            "Stabile Version der Boden-Ring-Konfiguration (Puls + Kcal/Oxygen)",
            "Ring-Verlaufsfarben, Schwellenwert-Farbumschlag und Breiten-Slider gebuendelt"
        )
    ),
    ChangelogEntry(
        version = "4.4.9",
        date = "10.06.2026",
        changes = listOf(
            "Neu: Breiten-Slider fuer Puls-Ring und Kcal/Oxygen-Ring (2-16 dp)"
        )
    ),
    ChangelogEntry(
        version = "4.4.8",
        date = "10.06.2026",
        changes = listOf(
            "Puls-Ring: Standard-Maximum auf 140 (360° = 140 bpm), Start weiterhin bei 12 Uhr",
            "Neu: Boden-Komplikationen (Puls/Kcal-Oxygen) komplett in der App konfigurierbar",
            "Neu: Ring-Verlaufsfarben + Schwellenwert-Farbumschlag (darueber/darunter, Farbe 1 oder 2)",
            "Rechts: Umschaltung Kcal <-> Oxygen mit eigenem Ring (gleiche Einstellungen wie Puls)"
        )
    ),
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
