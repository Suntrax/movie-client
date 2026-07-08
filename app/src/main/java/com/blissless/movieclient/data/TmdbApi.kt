package com.blissless.movieclient.data

import android.util.Log
import com.blissless.movieclient.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
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
 */
class TmdbApi {

    fun isConfigured(): Boolean = BuildConfig.TMDB_API_KEY.isNotBlank()

    /** Search movies and TV shows by name. */
    fun searchMulti(query: String): List<TmdbSearchResult> {
        if (!isConfigured()) return emptyList()
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = "$BASE_URL/search/multi?api_key=${BuildConfig.TMDB_API_KEY}" +
            "&language=en-US&page=1&include_adult=false&query=$encoded"
        return try {
            val json = JSONObject(get(url))
            parseResults(json.optJSONArray("results") ?: return emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "searchMulti failed for '$query'", e)
            emptyList()
        }
    }

    /**
     * Resolve a raw TMDB id into a title. Extensions expect both `title` and
     * `tmdbId` in the scrape URI — when the user enters only an ID, we fetch
     * the title from TMDB so the extension doesn't have to.
     */
    fun fetchMetadata(tmdbId: Int, mediaType: MediaType): TmdbSearchResult? {
        if (!isConfigured()) return null
        val url = "$BASE_URL/${mediaType.apiValue}/$tmdbId?api_key=${BuildConfig.TMDB_API_KEY}&language=en-US"
        return try {
            val obj = JSONObject(get(url))
            val title = obj.optString("title")
                .ifBlank { obj.optString("name") }
                .ifBlank { obj.optString("original_title") }
                .ifBlank { obj.optString("original_name") }
            if (title.isBlank()) return null
            TmdbSearchResult(
                id = tmdbId,
                title = title,
                mediaType = mediaType,
                releaseDate = obj.optString("release_date")
                    .ifBlank { obj.optString("first_air_date") }
                    .ifBlank { null },
                overview = obj.optString("overview", ""),
                posterPath = obj.optString("poster_path").ifBlank { null },
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchMetadata failed for $mediaType/$tmdbId", e)
            null
        }
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
                throw IOException("HTTP $code for ${urlStr.take(120)}")
            }
            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { r ->
                val sb = StringBuilder()
                var line: String?
                while (r.readLine().also { line = it } != null) sb.append(line).append('\n')
                return sb.toString()
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "TmdbApi"
        private const val BASE_URL = "https://api.themoviedb.org/3"
    }
}
