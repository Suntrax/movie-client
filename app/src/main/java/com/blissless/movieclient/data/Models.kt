package com.blissless.movieclient.data

/**
 * An installed Chizuki extension discovered via the EXTENSION_BEACON broadcast.
 *
 * Chizuki extensions are standalone APKs whose application label starts with
 * "Chizuki: " and which export a BroadcastReceiver listening for
 * `com.blissless.movieclient.EXTENSION_BEACON`. The host invokes them through
 * a ContentProvider whose authority follows the convention
 * `<extension_package>.provider`.
 */
data class InstalledExtension(
    val label: String,        // e.g. "Chizuki: vidlink"
    val packageName: String,  // e.g. com.blissless.vidlink
    val versionName: String? = null,
) {
    /** The ContentProvider authority — conventionally `<pkg>.provider`. */
    val authority: String get() = "$packageName.provider"

    /** Human-friendly name with the "Chizuki: " prefix stripped. */
    val displayName: String
        get() = label.removePrefix("Chizuki:").removePrefix(" ").ifBlank { packageName }
}

/**
 * The full set of inputs passed to a Chizuki extension's scrape URI.
 *
 * Mirrors the contract documented in `Suntrax/chizuki-extensions`:
 * `content://<pkg>.provider/scrape?title=...&tmdbId=...&mediaType=movie|tv
 *   &season=N&episode=M`
 */
data class ScrapeRequest(
    val title: String,
    val tmdbId: Int,
    val mediaType: MediaType,
    val season: Int? = null,
    val episode: Int? = null,
)

enum class MediaType(val apiValue: String) {
    MOVIE("movie"),
    TV("tv");

    override fun toString(): String = apiValue
}

/**
 * Outcome of a scrape call. We keep the raw JSON string verbatim because the
 * whole point of this app is to show what the extension actually returned —
 * parsing/normalisation is intentionally minimal.
 *
 * `durationMs` is promoted to the interface so callers can read it without
 * narrowing to a specific subclass.
 */
sealed interface ScrapeResult {
    val durationMs: Long

    /** Extension returned a JSON payload. [raw] is the untouched string. */
    data class Success(val raw: String, override val durationMs: Long) : ScrapeResult

    /** Extension threw, returned null, or the IPC call failed. */
    data class Error(val message: String, override val durationMs: Long) : ScrapeResult
}

/** A single TMDB search hit returned by `/search/multi`. */
data class TmdbSearchResult(
    val id: Int,
    val title: String,
    val mediaType: MediaType,
    val releaseDate: String?,
    val overview: String,
    val posterPath: String?,
) {
    val posterUrl: String?
        get() = posterPath?.let { "https://image.tmdb.org/t/p/w185$it" }
}
