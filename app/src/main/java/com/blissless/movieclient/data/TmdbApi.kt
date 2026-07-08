package com.blissless.movieclient.data

import com.blissless.movieclient.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Minimal TMDB v3 client using raw `HttpURLConnection` (matches the reference
 * host's approach — no Retrofit, no Gson, just `org.json`).
 *
 * Two operations:
 *  - [searchMulti] : `/search/multi` for "search by name" input mode.
 *  - [fetchMetadata] : `/movie/{id}` or `/tv/{id}` to resolve a raw TMDB id
 *    into the title the extension expects in its scrape URI.
 *
 * The API key is injected via BuildConfig from `local.properties`.
 *
 * Failures throw [TmdbException] with a human-readable message that the
 * ViewModel forwards straight to the UI — we never silently swallow errors,
 * because a silent empty-result list looks identical to "no matches" and is
 * impossible to debug from the UI.
 */
class TmdbApi {

    /**
     * The TMDB v3 API key, sanitised at construction time:
     *  - trimmed of whitespace / newlines
     *  - stripped of surrounding quotes (in case local.properties had `TMDB_API_KEY="abc"`)
     *
     * TMDB v3 keys are 32-char hex strings. The v4 "read access token" is a
     * ~200-char JWT and is NOT compatible with the `?api_key=` query param —
     * if the user pasted that, we flag it in [keyDebugInfo].
     */
    private val apiKey: String = BuildConfig.TMDB_API_KEY
        .trim()
        .removePrefix("\"").removeSuffix("\"")
        .removePrefix("'").removeSuffix("'")
        .trim()

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    /** Human-readable summary of the loaded API key state, for the UI. */
    data class KeyDebugInfo(
        val loaded: Boolean,
        val length: Int,
        val preview: String,        // "ab…12" — first 2 + last 2 chars
        val looksLikeV4Token: Boolean,
        val diagnosis: String,      // "" if healthy, otherwise the problem
    )

    fun keyDebugInfo(): KeyDebugInfo {
        val len = apiKey.length
        val preview = if (len <= 4) {
            "*".repeat(len)
        } else {
            apiKey.take(2) + "…" + apiKey.takeLast(2)
        }
        val looksV4 = apiKey.startsWith("eyJ") && len > 100  // JWTs start with eyJ
        val diagnosis = when {
            len == 0 -> "Key is empty. Add TMDB_API_KEY=<your_v3_key> to local.properties and rebuild."
            looksV4 -> "This looks like a v4 read access token (JWT). You need the v3 API key — a 32-char hex string. Get it at https://www.themoviedb.org/settings/api (the 'API Key (v3 auth)' field, not the 'API Read Access Token')."
            len != 32 -> "Key has length $len — TMDB v3 keys are exactly 32 chars. Double-check you copied the whole key."
            else -> ""
        }
        return KeyDebugInfo(
            loaded = len > 0,
            length = len,
            preview = preview,
            looksLikeV4Token = looksV4,
            diagnosis = diagnosis,
        )
    }

    /** Search movies and TV shows by name. Throws [TmdbException] on failure. */
    fun searchMulti(query: String): List<TmdbSearchResult> {
        if (!isConfigured()) {
            throw TmdbException("TMDB_API_KEY is not set. Add it to local.properties and rebuild.")
        }
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = "$BASE_URL/search/multi?api_key=$apiKey" +
            "&language=en-US&page=1&include_adult=false&query=$encoded"
        val raw = get(url)
        val json = JSONObject(raw)
        val arr = json.optJSONArray("results") ?: run {
            val msg = json.optString("status_message").ifBlank { "No 'results' array in response." }
            throw TmdbException(msg)
        }
        return parseResults(arr)
    }

    /**
     * Resolve a raw TMDB id into a title. Extensions expect both `title` and
     * `tmdbId` in the scrape URI — when the user enters only an ID, we fetch
     * the title from TMDB so the extension doesn't have to.
     *
     * Throws [TmdbException] on failure so the caller can surface the error
     * rather than silently invoking the extension with a placeholder title.
     */
    fun fetchMetadata(tmdbId: Int, mediaType: MediaType): TmdbSearchResult {
        if (!isConfigured()) {
            throw TmdbException("TMDB_API_KEY is not set. Add it to local.properties and rebuild.")
        }
        val url = "$BASE_URL/${mediaType.apiValue}/$tmdbId?api_key=$apiKey&language=en-US"
        val obj = JSONObject(get(url))
        val title = obj.optString("title")
            .ifBlank { obj.optString("name") }
            .ifBlank { obj.optString("original_title") }
            .ifBlank { obj.optString("original_name") }
        if (title.isBlank()) {
            throw TmdbException("TMDB returned no title for ${mediaType.apiValue}/$tmdbId. The ID may be wrong or belong to the other media type.")
        }
        return TmdbSearchResult(
            id = tmdbId,
            title = title,
            mediaType = mediaType,
            releaseDate = obj.optString("release_date")
                .ifBlank { obj.optString("first_air_date") }
                .ifBlank { null },
            overview = obj.optString("overview", ""),
            posterPath = obj.optString("poster_path").ifBlank { null },
        )
    }

    private fun parseResults(arr: org.json.JSONArray): List<TmdbSearchResult> {
        val out = ArrayList<TmdbSearchResult>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val typeStr = obj.optString("media_type")
            val mediaType = when (typeStr) {
                "movie" -> MediaType.MOVIE
                "tv" -> MediaType.TV
                else -> continue
            }
            val title = obj.optString("title")
                .ifBlank { obj.optString("name") }
                .ifBlank { obj.optString("original_title") }
                .ifBlank { obj.optString("original_name") }
            if (title.isBlank()) continue
            out.add(
                TmdbSearchResult(
                    id = obj.optInt("id", -1).takeIf { it > 0 } ?: continue,
                    title = title,
                    mediaType = mediaType,
                    releaseDate = obj.optString("release_date")
                        .ifBlank { obj.optString("first_air_date") }
                        .ifBlank { null },
                    overview = obj.optString("overview", ""),
                    posterPath = obj.optString("poster_path").ifBlank { null },
                )
            )
        }
        return out
    }

    /**
     * GET a URL and return the response body. On non-2xx, reads the error
     * stream (TMDB puts a JSON `status_message` there) and throws
     * [TmdbException] with both the status code and the response body so the
     * user can see exactly what TMDB said.
     */
    private fun get(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = runCatching {
                    BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, StandardCharsets.UTF_8)).use { r ->
                        val sb = StringBuilder()
                        var line: String?
                        while (r.readLine().also { line = it } != null) sb.append(line).append('\n')
                        sb.toString().trim().take(500)
                    }
                }.getOrDefault("<no error body>")
                val msg = parseTmdbErrorMessage(errBody) ?: errBody
                throw TmdbException("HTTP $code — $msg\n\nURL: ${urlStr.take(160)}")
            }
            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { r ->
                val sb = StringBuilder()
                var line: String?
                while (r.readLine().also { line = it } != null) sb.append(line).append('\n')
                return sb.toString()
            }
        } catch (e: TmdbException) {
            throw e
        } catch (e: Exception) {
            // Network/connect/parse errors — surface them too.
            throw TmdbException("${e.javaClass.simpleName}: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    /** TMDB error bodies are JSON like `{"status_code":7,"status_message":"Invalid API key"}`. */
    private fun parseTmdbErrorMessage(body: String): String? {
        return try {
            val obj = JSONObject(body)
            obj.optString("status_message").ifBlank { null }
        } catch (_: Exception) { null }
    }

    class TmdbException(message: String) : Exception(message)

    companion object {
        private const val TAG = "TmdbApi"
        private const val BASE_URL = "https://api.themoviedb.org/3"
    }
}
