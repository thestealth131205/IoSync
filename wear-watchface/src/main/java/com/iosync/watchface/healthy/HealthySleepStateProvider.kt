package com.iosync.watchface.healthy

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

private const val TAG = "HealthySleepState"

/** Data-Layer-Pfad zum Handy (WatchFaceTriggerListenerService in der App). */
const val PATH_HEALTHY_SLEEP_STATE = "/iosync/watch/healthy_sleep"

private const val HEALTHY_PACKAGE = "de.letheapp.healthy"

/** SHA-256 des Release-Zertifikats von Healthy (CN=Healthy, O=letheapp). */
private val HEALTHY_CERT_SHA256: ByteArray =
    "8F9DF927F469A1CD4CD34890D5AB1E56297EE26D1394E8812A36AAFBED5A673B"
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private const val METHOD_SLEEP_STATE = "sleepState"

// Schlüssel des Aufrufs — gleich in Healthy (wear/sync/IoSyncBridge).
private const val EXTRA_SLEEPING = "sleeping"
private const val EXTRA_CHANGED_AT = "changedAt"
private const val EXTRA_SLEEP_START = "sleepStart"
private const val EXTRA_SLEEP_END = "sleepEnd"
private const val RESULT_OK = "ok"
private const val RESULT_ERROR = "error"

// Schlüssel im Data Layer — gleich in der App (WatchFaceTriggerListenerService).
private const val KEY_SLEEPING = "sleeping"
private const val KEY_CHANGED_AT = "changed_at"
private const val KEY_SLEEP_START = "sleep_start"
private const val KEY_SLEEP_END = "sleep_end"

/**
 * Empfängt den Schlaf-/Wach-Status von der Healthy-Uhr-App und reicht ihn sofort per Data Layer
 * ans Handy weiter, das ihn als Entitäten in Home Assistant einträgt.
 *
 * Healthy und IoSync haben verschiedene Paket-IDs und Signaturen, der Data Layer verbindet sie
 * nicht — deshalb ruft Healthy diesen Provider auf der Uhr direkt auf (ContentResolver.call).
 * Angenommen wird nur, was nachweislich von Healthy kommt (Paketname + Signatur), damit keine
 * andere App über den Schlafstatus z.B. die Heizung steuern kann.
 *
 * Der Data Layer hält den Status vor, bis das Handy erreichbar ist.
 */
class HealthySleepStateProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != METHOD_SLEEP_STATE) return failure("Unbekannte Methode $method")
        val ctx = context ?: return failure("Kein Context")
        val caller = callingPackage
        if (caller != HEALTHY_PACKAGE || !isHealthy(ctx, caller)) {
            Log.w(TAG, "Schlafstatus von $caller abgelehnt")
            return failure("Absender nicht Healthy")
        }
        if (extras == null || !extras.containsKey(EXTRA_SLEEPING)) return failure("Kein Status")

        val sleeping = extras.getBoolean(EXTRA_SLEEPING)
        val changedAt = extras.getLong(EXTRA_CHANGED_AT, System.currentTimeMillis())
        val sleepStart = extras.getLong(EXTRA_SLEEP_START, 0L)
        val sleepEnd = extras.getLong(EXTRA_SLEEP_END, 0L)
        Log.d(TAG, "Healthy meldet: ${if (sleeping) "schläft" else "wach"}")

        val request = PutDataMapRequest.create(PATH_HEALTHY_SLEEP_STATE).apply {
            dataMap.putBoolean(KEY_SLEEPING, sleeping)
            // Zeitstempel ändert sich bei jeder Meldung → das Handy bekommt sie auch bei gleichem Status.
            dataMap.putLong(KEY_CHANGED_AT, changedAt)
            if (sleepStart > 0) dataMap.putLong(KEY_SLEEP_START, sleepStart)
            if (sleepEnd > 0) dataMap.putLong(KEY_SLEEP_END, sleepEnd)
        }.asPutDataRequest().setUrgent()
        return try {
            // Binder-Thread: Warten ist erlaubt; danach liegt der Status sicher im Data Layer.
            Tasks.await(Wearable.getDataClient(ctx).putDataItem(request), 10, TimeUnit.SECONDS)
            Bundle().apply { putBoolean(RESULT_OK, true) }
        } catch (e: Exception) {
            Log.w(TAG, "Schlafstatus nicht in den Data Layer geschrieben", e)
            failure("Data Layer: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun isHealthy(ctx: Context, pkg: String): Boolean = runCatching {
        ctx.packageManager.hasSigningCertificate(pkg, HEALTHY_CERT_SHA256, PackageManager.CERT_INPUT_SHA256)
    }.getOrDefault(false)

    private fun failure(message: String) = Bundle().apply {
        putBoolean(RESULT_OK, false)
        putString(RESULT_ERROR, message)
    }

    // Nur call() wird genutzt — keine Tabellen.
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
