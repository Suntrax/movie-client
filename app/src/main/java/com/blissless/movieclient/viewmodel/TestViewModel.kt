package com.blissless.movieclient.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blissless.movieclient.data.ExtensionManager
import com.blissless.movieclient.data.InstalledExtension
import com.blissless.movieclient.data.MediaType
import com.blissless.movieclient.data.ScrapeRequest
import com.blissless.movieclient.data.ScrapeResult
import com.blissless.movieclient.data.TmdbApi
import com.blissless.movieclient.data.TmdbSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single ViewModel holding all UI state for the test screen.
 *
 * State model:
 *  - [extensions]    : auto-discovered list (re-scanned on every resume).
 *  - [selectedExt]   : the extension the next scrape will use.
 *  - [inputMode]     : TMDB id direct vs TMDB name search.
 *  - [mediaType]     : movie vs tv.
 *  - [searchQuery] / [searchResults] / [selectedSearchResult]
 *  - [tmdbIdInput], [seasonInput], [episodeInput]
 *  - [scrapeResult]  : the last response (success JSON or error).
 *  - [isScraping]    : loading flag.
 */
class TestViewModel(app: Application) : AndroidViewModel(app) {

    private val extMgr = ExtensionManager(app)
    private val tmdb = TmdbApi()

    private val _ui = MutableStateFlow(UiState(tmdbKeyInfo = tmdb.keyDebugInfo()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun refreshExtensions() {
        val list = extMgr.discover()
        _ui.update { s ->
            val stillPresent = s.selectedExt?.let { sel -> list.firstOrNull { it.packageName == sel.packageName } }
            s.copy(
                extensions = list,
                selectedExt = stillPresent ?: list.firstOrNull(),
                extensionsDirty = false,
            )
        }
    }

    fun selectExtension(ext: InstalledExtension) = _ui.update { it.copy(selectedExt = ext) }

    fun setInputMode(mode: InputMode) = _ui.update { it.copy(inputMode = mode) }
    fun setMediaType(type: MediaType) = _ui.update { it.copy(mediaType = type) }
    fun setTmdbIdInput(v: String) = _ui.update { it.copy(tmdbIdInput = v.filter { c -> c.isDigit() }) }
    fun setSeasonInput(v: String) = _ui.update { it.copy(seasonInput = v.filter { c -> c.isDigit() }) }
    fun setEpisodeInput(v: String) = _ui.update { it.copy(episodeInput = v.filter { c -> c.isDigit() }) }
    fun setSearchQuery(v: String) = _ui.update {
        it.copy(searchQuery = v, searchResults = emptyList(), selectedSearchResult = null, lastSearchQuery = null, searchError = null)
    }
    fun markExtensionsDirty() = _ui.update { it.copy(extensionsDirty = true) }

    fun runSearch() {
        val q = _ui.value.searchQuery.trim()
        if (q.isBlank()) return
        _ui.update { it.copy(isSearching = true, searchError = null, searchResults = emptyList(), selectedSearchResult = null, lastSearchQuery = q) }
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { tmdb.searchMulti(q) }
                _ui.update { it.copy(isSearching = false, searchResults = results, searchError = null) }
            } catch (e: TmdbApi.TmdbException) {
                _ui.update { it.copy(isSearching = false, searchError = e.message ?: "Unknown TMDB error", searchResults = emptyList()) }
            } catch (e: Exception) {
                _ui.update { it.copy(isSearching = false, searchError = "${e.javaClass.simpleName}: ${e.message}", searchResults = emptyList()) }
            }
        }
    }

    fun selectSearchResult(r: TmdbSearchResult) = _ui.update {
        it.copy(
            selectedSearchResult = r,
            tmdbIdInput = r.id.toString(),
            mediaType = r.mediaType,
        )
    }

    /** Run the scrape against the currently-selected extension + inputs. */
    fun runScrape() {
        val s = _ui.value
        val ext = s.selectedExt ?: return

        // Validate inputs synchronously first — these don't need IO.
        val tmdbId: Int
        val titleFromSearch: String?  // non-null only in NAME_SEARCH mode
        when (s.inputMode) {
            InputMode.TMDB_ID -> {
                val id = s.tmdbIdInput.toIntOrNull()
                if (id == null || id <= 0) {
                    _ui.update { it.copy(scrapeResult = ScrapeResult.Error("Enter a valid TMDB id.", 0)) }
                    return
                }
                tmdbId = id
                titleFromSearch = null
            }
            InputMode.NAME_SEARCH -> {
                val picked = s.selectedSearchResult
                if (picked == null) {
                    _ui.update { it.copy(scrapeResult = ScrapeResult.Error("Pick a search result first.", 0)) }
                    return
                }
                titleFromSearch = picked.title
                tmdbId = picked.id
            }
        }

        _ui.update { it.copy(isScraping = true, scrapeResult = null, lastRequest = null) }
        viewModelScope.launch {
            // Resolve the title (TMDB_ID mode only — NAME_SEARCH already has it).
            // Surface TMDB errors instead of falling back to "Unknown Title <id>",
            // because that placeholder guarantees the extension won't find a match
            // and masks the real problem (bad API key, network, wrong mediaType).
            val title: String = try {
                if (titleFromSearch != null) {
                    titleFromSearch
                } else {
                    withContext(Dispatchers.IO) { tmdb.fetchMetadata(tmdbId, s.mediaType) }.title
                }
            } catch (e: TmdbApi.TmdbException) {
                _ui.update {
                    it.copy(
                        isScraping = false,
                        scrapeResult = ScrapeResult.Error(
                            "TMDB metadata lookup failed — scrape aborted.\n\n${e.message}", 0
                        ),
                    )
                }
                return@launch
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        isScraping = false,
                        scrapeResult = ScrapeResult.Error(
                            "TMDB metadata lookup failed — scrape aborted.\n\n${e.javaClass.simpleName}: ${e.message}", 0
                        ),
                    )
                }
                return@launch
            }

            val season = if (s.mediaType == MediaType.TV) s.seasonInput.toIntOrNull() else null
            val episode = if (s.mediaType == MediaType.TV) s.episodeInput.toIntOrNull() else null
            val request = ScrapeRequest(
                title = title,
                tmdbId = tmdbId,
                mediaType = s.mediaType,
                season = season,
                episode = episode,
            )
            _ui.update { it.copy(lastRequest = request) }

            val result = withContext(Dispatchers.IO) { extMgr.scrape(ext, request) }
            _ui.update { it.copy(isScraping = false, scrapeResult = result) }
        }
    }

    fun clearResult() = _ui.update { it.copy(scrapeResult = null, lastRequest = null) }

    // -------------------------------------------------------------------

    data class UiState(
        val extensions: List<InstalledExtension> = emptyList(),
        val selectedExt: InstalledExtension? = null,
        val extensionsDirty: Boolean = false,

        val tmdbKeyInfo: TmdbApi.KeyDebugInfo = TmdbApi.KeyDebugInfo(false, 0, "", false, "Not checked yet."),

        val inputMode: InputMode = InputMode.TMDB_ID,
        val mediaType: MediaType = MediaType.MOVIE,

        val tmdbIdInput: String = "",
        val seasonInput: String = "",
        val episodeInput: String = "",

        val searchQuery: String = "",
        val searchResults: List<TmdbSearchResult> = emptyList(),
        val selectedSearchResult: TmdbSearchResult? = null,
        val isSearching: Boolean = false,
        val searchError: String? = null,
        val lastSearchQuery: String? = null,

        val isScraping: Boolean = false,
        val lastRequest: ScrapeRequest? = null,
        val scrapeResult: ScrapeResult? = null,
    )

    enum class InputMode { TMDB_ID, NAME_SEARCH }
}
