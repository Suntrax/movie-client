package com.blissless.movieclient.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blissless.movieclient.data.MediaType
import com.blissless.movieclient.data.ScrapeResult
import com.blissless.movieclient.viewmodel.TestViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    viewModel: TestViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.ui.collectAsState()
    val ctx = LocalContext.current

    // Note: extension discovery is triggered from MainActivity's lifecycle
    // observer on every ON_RESUME (covers first launch + returning after the
    // user sideloads a new extension APK). The toolbar refresh icon is the
    // manual fallback.

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MovieClient Test", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Extension IPC harness",
                            fontSize = 11.sp,
                            color = ChizukiOnBgMuted,
                            fontFamily = MonoFamily,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshExtensions() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Re-scan extensions")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChizukiSurface,
                    titleContentColor = ChizukiOnBg,
                    navigationIconContentColor = ChizukiOnBg,
                    actionIconContentColor = ChizukiOnBgMuted,
                ),
            )
        },
        containerColor = ChizukiBackground,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ---- 1. Extensions ----
            SectionHeader("Installed extensions")
            if (state.extensions.isEmpty()) {
                EmptyExtensionsHint()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.extensions.forEach { ext ->
                        ExtensionRow(
                            label = ext.label,
                            packageName = ext.packageName,
                            versionName = ext.versionName,
                            selected = ext.packageName == state.selectedExt?.packageName,
                            onClick = { viewModel.selectExtension(ext) },
                        )
                    }
                }
            }
            if (state.extensionsDirty) {
                Text(
                    "Apps list changed — press the refresh icon to re-scan.",
                    color = ChizukiWarn,
                    fontSize = 11.sp,
                    fontFamily = MonoFamily,
                )
            }

            // ---- 2. Input mode ----
            SectionHeader("Input")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("TMDB ID", state.inputMode == TestViewModel.InputMode.TMDB_ID) {
                    viewModel.setInputMode(TestViewModel.InputMode.TMDB_ID)
                }
                Pill("Search by name", state.inputMode == TestViewModel.InputMode.NAME_SEARCH) {
                    viewModel.setInputMode(TestViewModel.InputMode.NAME_SEARCH)
                }
            }

            // ---- 3. Media type ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Movie", state.mediaType == MediaType.MOVIE) { viewModel.setMediaType(MediaType.MOVIE) }
                Pill("TV", state.mediaType == MediaType.TV) { viewModel.setMediaType(MediaType.TV) }
            }

            // ---- 4. Mode-specific inputs ----
            when (state.inputMode) {
                TestViewModel.InputMode.TMDB_ID -> TmdbIdInputBlock(viewModel, state)
                TestViewModel.InputMode.NAME_SEARCH -> NameSearchBlock(viewModel, state)
            }

            // ---- 5. Run scrape ----
            PrimaryActionButton(
                label = if (state.isScraping) "Scraping…" else "Run scrape",
                onClick = { viewModel.runScrape() },
                enabled = state.selectedExt != null && !state.isScraping,
                loading = state.isScraping,
                icon = Icons.Rounded.Bolt,
            )

            // ---- 6. Result ----
            state.lastRequest?.let { req -> RequestSummaryCard(req.title, req.tmdbId, req.mediaType, req.season, req.episode) }
            state.scrapeResult?.let { r ->
                ResultCard(
                    result = r,
                    onCopy = { text -> copyToClipboard(ctx, text); toast(ctx, "Copied to clipboard") },
                    onClear = { viewModel.clearResult() },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TmdbIdInputBlock(
    vm: TestViewModel,
    state: TestViewModel.UiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LabeledNumberField(
            label = "TMDB ID",
            value = state.tmdbIdInput,
            onValueChange = vm::setTmdbIdInput,
            placeholder = "e.g. 671 —哈利波特 Philosophers Stone",
        )
        Text(
            text = "Title will be auto-resolved via TMDB /movie/{id} or /tv/{id} " +
                "so the extension receives both title and tmdbId.",
            color = ChizukiOnBgMuted,
            fontSize = 11.sp,
        )
        if (state.mediaType == MediaType.TV) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledNumberField(
                    label = "Season",
                    value = state.seasonInput,
                    onValueChange = vm::setSeasonInput,
                    placeholder = "1",
                    modifier = Modifier.weight(1f),
                )
                LabeledNumberField(
                    label = "Episode",
                    value = state.episodeInput,
                    onValueChange = vm::setEpisodeInput,
                    placeholder = "1",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NameSearchBlock(
    vm: TestViewModel,
    state: TestViewModel.UiState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledTextField(
            label = "Search TMDB",
            value = state.searchQuery,
            onValueChange = vm::setSearchQuery,
            placeholder = "Movie or series title",
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconAction(
                        icon = Icons.Rounded.Clear,
                        contentDescription = "Clear",
                        onClick = { vm.setSearchQuery("") },
                    )
                }
            },
        )
        PrimaryActionButton(
            label = if (state.isSearching) "Searching…" else "Search",
            onClick = { vm.runSearch() },
            enabled = state.searchQuery.isNotBlank() && !state.isSearching,
            loading = state.isSearching,
            icon = Icons.Rounded.Refresh,
        )
        state.searchError?.let {
            Text(it, color = ChizukiError, fontSize = 12.sp)
        }
        if (state.searchResults.isNotEmpty()) {
            SectionHeader("Results (${state.searchResults.size})")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.searchResults.take(15).forEach { r ->
                    SearchSuggestionRow(
                        title = r.title,
                        subtitle = buildString {
                            append(r.mediaType.apiValue.uppercase())
                            r.releaseDate?.take(4)?.let { append("  ·  ").append(it) }
                            append("  ·  tmdb:").append(r.id)
                        },
                        selected = state.selectedSearchResult?.id == r.id,
                        onClick = { vm.selectSearchResult(r) },
                    )
                }
                if (state.searchResults.size > 15) {
                    Text(
                        "+ ${state.searchResults.size - 15} more — refine your query.",
                        color = ChizukiOnBgMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 10.dp, top = 4.dp),
                    )
                }
            }
        }
        if (state.mediaType == MediaType.TV && state.selectedSearchResult != null) {
            SectionHeader("Season / episode")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledNumberField(
                    label = "Season",
                    value = state.seasonInput,
                    onValueChange = vm::setSeasonInput,
                    placeholder = "1",
                    modifier = Modifier.weight(1f),
                )
                LabeledNumberField(
                    label = "Episode",
                    value = state.episodeInput,
                    onValueChange = vm::setEpisodeInput,
                    placeholder = "1",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RequestSummaryCard(
    title: String,
    tmdbId: Int,
    mediaType: MediaType,
    season: Int?,
    episode: Int?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChizukiSurface)
            .border(1.dp, ChizukiOutline, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text("LAST REQUEST", color = ChizukiOnBgMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        KeyValue("title", title)
        KeyValue("tmdbId", tmdbId.toString())
        KeyValue("mediaType", mediaType.apiValue)
        if (season != null) KeyValue("season", season.toString())
        if (episode != null) KeyValue("episode", episode.toString())
    }
}

@Composable
private fun KeyValue(k: String, v: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            "$k:",
            color = ChizukiAccent,
            fontFamily = MonoFamily,
            fontSize = 12.sp,
            modifier = Modifier.width(80.dp),
        )
        Text(
            v,
            color = ChizukiOnBg,
            fontFamily = MonoFamily,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultCard(
    result: ScrapeResult,
    onCopy: (String) -> Unit,
    onClear: () -> Unit,
) {
    val isSuccess: Boolean
    val body: String
    val accent: Color
    val statusLabel: String
    when (result) {
        is ScrapeResult.Success -> {
            isSuccess = true; body = result.raw; accent = ChizukiSuccess; statusLabel = "OK"
        }
        is ScrapeResult.Error -> {
            isSuccess = false; body = result.message; accent = ChizukiError; statusLabel = "ERR"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChizukiSurface)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(statusLabel, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFamily)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "EXTENSION RESPONSE",
                color = ChizukiOnBgMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${TimeUnit.MILLISECONDS.toSeconds(result.durationMs)}s",
                color = ChizukiOnBgFaint,
                fontSize = 11.sp,
                fontFamily = MonoFamily,
            )
            Spacer(Modifier.width(6.dp))
            IconAction(
                icon = Icons.Rounded.ContentCopy,
                contentDescription = "Copy",
                onClick = { onCopy(body) },
                tint = ChizukiOnBgMuted,
            )
            IconAction(
                icon = Icons.Rounded.Clear,
                contentDescription = "Clear",
                onClick = onClear,
                tint = ChizukiOnBgMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ChizukiBackground)
                .padding(10.dp),
        ) {
            Text(
                text = body,
                color = if (isSuccess) ChizukiOnBg else ChizukiError,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("chizuki_response", text))
}

private fun toast(ctx: Context, msg: String) {
    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
}
