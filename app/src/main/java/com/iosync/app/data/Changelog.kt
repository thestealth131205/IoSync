package com.iosync.app.data

data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<String>
)

val appChangelog = listOf(
    ChangelogEntry(
        version = "5.1.0",
        date = "02.07.2026",
        changes = listOf(
            "Geofence: Hintergrund-Standortberechtigung (ACCESS_BACKGROUND_LOCATION) ergänzt – Standort wird jetzt auch im Hintergrund zuverlässig aktualisiert",
            "Geofence: RECEIVE_BOOT_COMPLETED-Berechtigung ergänzt – Geofence startet automatisch nach Neustart",
            "Geofence: GeofenceService läuft jetzt als Foreground-Service mit persistenter Benachrichtigung für zuverlässigen Hintergrundbetrieb"
        )
    ),
    ChangelogEntry(
        version = "5.0.9",
        date = "02.07.2026",
        changes = listOf(
            "Geofence: Nach Neustart des Geräts wird der GeofenceService automatisch wieder gestartet (BootReceiver)",
            "Geofence: Fallback auf letzten bekannten Standort wenn kein frischer GPS-Fix verfügbar (z. B. im Gebäude)",
            "Geofence: Warnung + Direktlink wenn Akku-Optimierung den Hintergrund-Standort blockiert"
        )
    ),
    ChangelogEntry(
        version = "5.0.8",
        date = "02.07.2026",
        changes = listOf(
            "Geofence: Akku-Optimierung-Warnung in den Einstellungen – direkter Link zum Ausschließen aus der Akku-Optimierung verhindert Hintergrund-Blockierung"
        )
    ),
    ChangelogEntry(
        version = "5.0.7",
        date = "29.06.2026",
        changes = listOf(
            "Boden-Komplikationen X-Feinjustage: X− rückt Ringe zusammen, X+ weiter auseinander (symmetrisch)"
        )
    ),
    ChangelogEntry(
        version = "5.0.6",
        date = "29.06.2026",
        changes = listOf(
            "Boden-Komplikationen X-Feinjustage: X− rückt Ringe zusammen, X+ weiter auseinander (symmetrisch)"
        )
    ),
    ChangelogEntry(
        version = "5.0.5",
        date = "29.06.2026",
        changes = listOf(
            "Neue X/Y-Feinjustierung (±) für die Wetter-Anzeige oben in den Einstellungen",
            "Gleiche Position-Feinjustierung für die beiden Boden-Komplikations-Ringe mit Wert"
        )
    ),
    ChangelogEntry(
        version = "5.0.4",
        date = "29.06.2026",
        changes = listOf(
            "Weißton-Buttons jetzt im Bogen um den SAVE-Button angeordnet",
            "Watchface-Layout: Werte, Balken & Schrittzahl feiner positioniert"
        )
    ),
    ChangelogEntry(
        version = "5.0.3",
        date = "28.06.2026",
        changes = listOf(
            "Weißton-Buttons & schnellere Schaltbefehle ausgerollt"
        )
    ),
    ChangelogEntry(
        version = "5.0.2",
        date = "28.06.2026",
        changes = listOf(
            "Fix: Schaltbefehle (Pillen/Farbe/Balken) der Uhr kommen zuverlässiger und schneller an",
            "Eigener, schlanker Netzwerk-Kanal nur für Befehle — wartet nicht mehr hinter dem Datenabruf",
            "Automatischer Wiederholversuch (bis zu 3×) bei kurzzeitig überlasteter Bluetooth-Leitung"
        )
    ),
    ChangelogEntry(
        version = "5.0.1",
        date = "28.06.2026",
        changes = listOf(
            "RGB-Farbwahlrad: oben drei Weißton-Buttons (warmweiß / neutralweiß / kaltweiß)",
            "Tippen schreibt den jeweiligen Kelvin-Wert in einen eigenen Datenpunkt",
            "Datenpunkt + die drei Kelvin-Werte wählbar in Einstellungen → Zweite Seite"
        )
    ),
    ChangelogEntry(
        version = "5.0.0",
        date = "28.06.2026",
        changes = listOf(
            "Seite 2: neuer Farb-Streifen links zeigt die Farbe eines ioBroker-Datenpunkts (RGB-Hex)",
            "Tippen auf den Streifen öffnet ein RGB-Farbwahlrad über dem Watchface",
            "Save-Button in der Mitte schreibt die gewählte Farbe in den Datenpunkt zurück",
            "Datenpunkt für den Farb-Streifen wählbar in Einstellungen → Zweite Seite"
        )
    ),
    ChangelogEntry(
        version = "4.9.8",
        date = "25.06.2026",
        changes = listOf(
            "Fix: Handy-Akkustand in der App wird jetzt live aktualisiert (statt auf dem Startwert hängen zu bleiben)"
        )
    ),
    ChangelogEntry(
        version = "4.9.7",
        date = "24.06.2026",
        changes = listOf(
            "Fix: Handy-Akkustatus wird regelmäßiger an die Uhr übertragen",
            "Standort-Vibration: mehrere Standorte möglich (je eigener Umkreis)",
            "Standort per „+“ hinzufügen, per „−“ löschen, antippen zum Bearbeiten",
            "Klingelmodus auf Vibration, sobald man in irgendeinem Bereich ist",
            "Prüf-Intervall gilt weiterhin global für alle Standorte"
        )
    ),
    ChangelogEntry(
        version = "4.9.5",
        date = "22.06.2026",
        changes = listOf(
            "Fix: Geofence-Build-Fehler (CancellationToken Import)"
        )
    ),
    ChangelogEntry(
        version = "4.9.4",
        date = "22.06.2026",
        changes = listOf(
            "Versionsbump & Deployment-Update"
        )
    ),
    ChangelogEntry(
        version = "4.9.3",
        date = "22.06.2026",
        changes = listOf(
            "Geofence: Verlassen setzt Modus auf Normal (statt vorherigen Modus)"
        )
    ),
    ChangelogEntry(
        version = "4.9.2",
        date = "22.06.2026",
        changes = listOf(
            "Versionsaktualisierung und Stabilitätsverbesserungen"
        )
    ),
    ChangelogEntry(
        version = "4.9.1",
        date = "22.06.2026",
        changes = listOf(
            "Geofence-Intervall-Fix (Doze-Modus): requestLocationUpdates durch AlarmManager.setExactAndAllowWhileIdle() ersetzt – weckt das Gerät jetzt auch bei ausgeschaltetem Display im Doze-Modus zuverlässig auf",
            "Standort-Check nutzt getCurrentLocation(HIGH_ACCURACY) für aktiven GPS-Fix statt passivem Polling, das ohne andere GPS-Apps keine Daten lieferte"
        )
    ),
    ChangelogEntry(
        version = "4.9.0",
        date = "22.06.2026",
        changes = listOf(
            "Standort-Intervall-Bug behoben: Standort wird jetzt zuverlässig im eingestellten Intervall abgefragt – auch wenn die App im Hintergrund läuft oder der Bildschirm aus ist",
            "Geofence-Service nutzt nun requestLocationUpdates (kontinuierliche Updates) statt einzelner getCurrentLocation()-Aufrufe, die im Doze-Modus häufig null lieferten",
            "Config (Koordinaten, Radius, Intervall) wird persistent gespeichert – der Service arbeitet nach einem Systemkill/Neustart korrekt weiter ohne Datenverlust"
        )
    ),
    ChangelogEntry(
        version = "4.8.9",
        date = "22.06.2026",
        changes = listOf(
            "Standort-Vibration prüft den GPS-Standort jetzt aktiv im eingestellten Intervall (eigene Abfrage), statt nur auf die akku-optimierten System-Geofence-Übergänge zu warten – der Standort wird zuverlässig im gewählten Takt abgeglichen",
            "Die \"Standort aktualisiert\"-Benachrichtigung erscheint nun bei jeder Intervall-Prüfung"
        )
    ),
    ChangelogEntry(
        version = "4.8.8",
        date = "22.06.2026",
        changes = listOf(
            "Standort-Vibration: dauerhafte Benachrichtigung hält die Überwachung im Hintergrund zuverlässig am Laufen",
            "Neue Benachrichtigung, sobald der GPS-Standort abgeglichen wurde",
            "Neue Benachrichtigung beim Betreten bzw. Verlassen des konfigurierten Bereichs",
            "Standort kann jetzt wahlweise per Adresse (wird automatisch in Koordinaten umgewandelt) oder direkt über Koordinaten (lat/lon) festgelegt werden"
        )
    ),
    ChangelogEntry(
        version = "4.8.7",
        date = "21.06.2026",
        changes = listOf(
            "Standort-Vibration: Statuspunkt zeigt jetzt grün (im Bereich) bzw. rot (außerhalb) an, ob du dich aktuell im konfigurierten Bereich befindest",
            "Health-Quellen (Puls/Kalorien/SpO2) werden jetzt auch über den allgemeinen \"Auf Uhr übertragen\"-Button gespeichert – die gewählte Pulsquelle geht nicht mehr verloren",
            "Aufräumen: redundante Datenwege entfernt"
        )
    ),
    ChangelogEntry(
        version = "4.8.6",
        date = "20.06.2026",
        changes = listOf(
            "Stabilitätsverbesserungen: Watchface-Daten bleiben auch nach längerer App-Inaktivität aktuell"
        )
    ),
    ChangelogEntry(
        version = "4.8.5",
        date = "20.06.2026",
        changes = listOf(
            "Verbindungs-Config (ioBroker-Host, Ports, Datenpunkt-IDs) wird auf der Uhr dauerhaft gespeichert – Watchface-Daten frieren nicht mehr ein wenn die App längere Zeit nicht geöffnet wurde"
        )
    ),
    ChangelogEntry(
        version = "4.8.4",
        date = "20.06.2026",
        changes = listOf(
            "Handy-Akku friert während eines Drucks nicht mehr ein: Funklast der Uhr bei aktivem Druck entzerrt, damit der Akkustand zuverlässig über Bluetooth durchkommt",
            "Klipper-Abruf während eines Drucks auf 15 s im Mittel gedrosselt (statt Sekundentakt) – spart Akku und Funkzeit",
            "ioBroker-Push während eines Drucks gebündelt (max. alle 5 s) und doppelte Abrufe vermieden; Abruf-Zeitpunkte leicht versetzt, um die Bluetooth-Leitung nicht dauerhaft zu belegen"
        )
    ),
    ChangelogEntry(
        version = "4.8.3",
        date = "18.06.2026",
        changes = listOf(
            "Standort-Vibration: Neues Prüf-Intervall per Schritt-Buttons (-5m/-1m/+1m/+5m) – legt fest, in welchen Abständen geprüft wird, ob du im Bereich bist (30 s–30 min); größere Intervalle sparen Akku"
        )
    ),
    ChangelogEntry(
        version = "4.8.2",
        date = "18.06.2026",
        changes = listOf(
            "Standort-Vibration: Umkreis lässt sich jetzt per Schritt-Buttons (-100/-50/+50/+100) feinjustieren statt fester Voreinstellungen – aktueller Wert steht in der Mitte, Bereich 100–2000 m"
        )
    ),
    ChangelogEntry(
        version = "4.8.1",
        date = "18.06.2026",
        changes = listOf(
            "Bugfix Geofence: Vibrations-Modus wird beim Betreten des Bereichs jetzt zuverlässig aktiviert und beim Verlassen wieder zurückgesetzt"
        )
    ),
    ChangelogEntry(
        version = "4.8.0",
        date = "18.06.2026",
        changes = listOf(
            "Bugfix Geofence: Vibrations-Zone wird jetzt nach Neustart oder App-Kill automatisch neu registriert – GPS-Abfrage läuft wieder zuverlässig"
        )
    ),
    ChangelogEntry(
        version = "4.7.9",
        date = "18.06.2026",
        changes = listOf(
            "Ambient-Modus: zeigt jetzt nur noch Uhrzeit mit Sekunden + Wochentag/Tag, alles andere bleibt schwarz (spart Energie)",
            "Neu: Rechte Boden-Komplikation kann den Klipper-Druck-Status in % anzeigen – Ring füllt sich von 0–100 % auf 360°",
            "Druck-Status für Seite 1 wird live per Moonraker-WebSocket geholt und funktioniert unabhängig von Seite 3",
            "Seite 3 (Klipper): Werte werden beim Öffnen sofort aktualisiert statt erst nach dem nächsten Poll-Zyklus",
            "Akkufix Puls: optischer Sensor wird nicht mehr dauerhaft aktiv gehalten – Messung nur noch periodisch bei sichtbarem Watchface"
        )
    ),
    ChangelogEntry(
        version = "4.7.8",
        date = "16.06.2026",
        changes = listOf(
            "Akkuoptimierung: Im Ambient-Modus pausieren ioBroker-, Health- und Echtzeit-Push-Abrufe komplett",
            "Akkuoptimierung: Wetter wird im Ambient-Modus nur noch halb so oft abgefragt (Intervall verdoppelt)",
            "Beim Aufwachen werden alle pausierten Abrufe sofort nachgeholt"
        )
    ),
    ChangelogEntry(
        version = "4.7.7",
        date = "15.06.2026",
        changes = listOf(
            "Watchface: Klipper-Daten werden nur noch abgefragt wenn Seite 3 aktiv ist (spart Akku & Traffic)"
        )
    ),
    ChangelogEntry(
        version = "4.7.6",
        date = "15.06.2026",
        changes = listOf(
            "Neu: Standort-Vibration (Geofence) – Handy schaltet automatisch auf Vibration wenn du einen gespeicherten Standort betrittst",
            "Adresssuche mit Live-Vorschlägen (Straße + Hausnummer) über OpenStreetMap",
            "Umkreis wählbar: 150 m, 300 m oder 500 m",
            "Watchface: Seite 2 / 3 wechselt automatisch zurück zu Seite 1 wenn das Display ausgeht (Ambient-Modus)"
        )
    ),
    ChangelogEntry(
        version = "4.7.5",
        date = "13.06.2026",
        changes = listOf(
            "Akkuoptimierung: Klipper-Anfragen gebündelt (4 → 1 HTTP-Request pro Zyklus)",
            "Akkuoptimierung: Puls-Sensor periodisch statt live (konfigurierbar, Standard 10 min)",
            "Klipper: Abruf bei inaktivem Drucker automatisch gedrosselt (4× langsamer)"
        )
    ),
    ChangelogEntry(
        version = "4.7.4",
        date = "13.06.2026",
        changes = listOf(
            "Akku: Puls wird nicht mehr live gemessen, sondern nur periodisch (Standard alle 10 min) – Sensor zwischen den Messungen aus",
            "Neue Einstellung \"Puls-Mess-Intervall (Uhr)\" im Bereich Wetter & Gesundheitsdaten",
            "Akku: Klipper-Abruf bei stillstehendem Drucker deutlich seltener (4× langsamer, mind. 60 s), eingestelltes Druck-Intervall bleibt erhalten"
        )
    ),
    ChangelogEntry(
        version = "4.7.3",
        date = "12.06.2026",
        changes = listOf(
            "Watchface Seite 1: Anzeige für Sonnenauf-/-untergang ein-/ausschaltbar"
        )
    ),
    ChangelogEntry(
        version = "4.7.2",
        date = "12.06.2026",
        changes = listOf(
            "Klipper: eigener \"Intervall speichern & übertragen\"-Button im Klipper-Bereich",
            "Watchface: Klipper-Daten werden bei ausgeschaltetem Display nicht mehr abgerufen (spart Akku)"
        )
    ),
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
