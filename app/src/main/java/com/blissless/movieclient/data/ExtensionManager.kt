package com.blissless.movieclient.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Discovers installed Chizuki extensions and invokes them via the
 * ContentProvider IPC contract.
 *
 * This is intentionally a faithful port of the reference host logic from
 * `Suntrax/chizuki`'s `ExtensionManager`, so that any extension written
 * against the public template (`Suntrax/chizuki-extensions`) works here
 * unchanged.
 */
class ExtensionManager(private val context: Context) {

    /**
     * Find every installed APK that:
     *  - declares a BroadcastReceiver for `com.blissless.movieclient.EXTENSION_BEACON`
     *  - has an application label starting with "Chizuki: "
     *
     * On Android 11+ this relies on the `<queries>` block in AndroidManifest.
     */
    fun discover(): List<InstalledExtension> {
        val beacon = "com.blissless.movieclient.EXTENSION_BEACON"
        val intent = Intent(beacon)
        val pm = context.packageManager
        // queryBroadcastReceivers accepts MATCH_* flags, not GET_RECEIVERS.
        // 0 returns every match regardless of default-only filter — which is
        // what we want since extensions don't need a DEFAULT category.
        val receivers = pm.queryBroadcastReceivers(intent, 0)

        return receivers.mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                if (!label.startsWith("Chizuki:")) return@mapNotNull null
                val versionName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0).versionName
                }
                InstalledExtension(label = label, packageName = pkg, versionName = versionName)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.distinctBy { it.packageName }
    }

    /**
     * Invoke the extension's ContentProvider at `/scrape` with the standard
     * query parameters and return the raw JSON string the extension returned.
     *
     * Implements the same lenient response parsing as the reference host:
     * the cursor is expected to have a single column named "data" containing
     * JSON. If the extension returns a non-JSON string (older protocol), we
     * wrap it in a JSON object so the UI can display it uniformly.
     */
    fun scrape(extension: InstalledExtension, request: ScrapeRequest): ScrapeResult {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(extension.authority)
            .appendPath("scrape")
            .appendQueryParameter("title", request.title)
            .appendQueryParameter("tmdbId", request.tmdbId.toString())
            .appendQueryParameter("mediaType", request.mediaType.apiValue)
            .apply {
                request.season?.let { appendQueryParameter("season", it.toString()) }
                request.episode?.let { appendQueryParameter("episode", it.toString()) }
            }
            .build()

        val started = System.nanoTime()
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf("data"),
                null, null, null
            )
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            if (cursor == null) {
                return ScrapeResult.Error(
                    "ContentProvider returned null cursor.\n" +
                        "URI: $uri\n" +
                        "Authority: ${extension.authority}\n" +
                        "This usually means the extension doesn't expose a provider with " +
                        "authority `${extension.authority}`.",
                    elapsedMs
                )
            }
            if (!cursor.moveToFirst()) {
                return ScrapeResult.Error(
                    "ContentProvider returned an empty cursor.\nURI: $uri",
                    elapsedMs
                )
            }
            val dataIdx = cursor.getColumnIndex("data")
            val raw = if (dataIdx >= 0) cursor.getString(dataIdx) else null
            if (raw.isNullOrBlank()) {
                return ScrapeResult.Error(
                    "ContentProvider returned an empty 'data' column.\nURI: $uri",
                    elapsedMs
                )
            }
            // Normalise: if it isn't valid JSON already, wrap it.
            val normalised = normaliseJson(raw)
            return ScrapeResult.Success(raw = normalised, durationMs = elapsedMs)
        } catch (e: SecurityException) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            return ScrapeResult.Error(
                "SecurityException: ${e.message}\n" +
                    "The provider is probably not exported, or the authority is wrong.",
                elapsedMs
            )
        } catch (e: IllegalArgumentException) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            return ScrapeResult.Error(
                "IllegalArgumentException: ${e.message}\n" +
                    "Usually means the authority doesn't match any installed provider.",
                elapsedMs
            )
        } catch (e: Exception) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            Log.e(TAG, "scrape failed", e)
            return ScrapeResult.Error(
                "${e.javaClass.simpleName}: ${e.message}",
                elapsedMs
            )
        } finally {
            cursor?.close()
        }
    }

    /** Pretty-print the JSON, or wrap non-JSON text in an object so it still displays. */
    private fun normaliseJson(raw: String): String {
        val trimmed = raw.trim()
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> JSONObject().put("raw", trimmed).toString(2)
            }
        } catch (e: Exception) {
            JSONObject().put("raw", trimmed).toString(2)
        }
    }

    companion object {
        private const val TAG = "ExtensionManager"
    }
}
