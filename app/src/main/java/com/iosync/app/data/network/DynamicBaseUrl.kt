package com.iosync.app.data.network

import com.iosync.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hält die aktuelle ioBroker Simple-API Basis-URL (Host + Port).
 * Wird zur Laufzeit aktualisiert wenn der Nutzer Einstellungen speichert.
 */
@Singleton
class DynamicBaseUrl @Inject constructor() {
    @Volatile var host: String = BuildConfig.IOBROKER_DEFAULT_HOST
    @Volatile var port: Int    = BuildConfig.IOBROKER_DEFAULT_PORT

    fun update(host: String, port: Int) {
        this.host = host
        this.port = port
    }
}
