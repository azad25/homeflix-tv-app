package com.homeflix.tv.presentation.screens.tvshows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.core.PlaybackProgressStore
import com.homeflix.tv.domain.repository.MediaRepository
import com.homeflix.tv.presentation.ui.ContentRow
import com.homeflix.tv.presentation.ui.MediaFormat
import com.homeflix.tv.presentation.ui.RowStyle
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileKind
import com.homeflix.tv.util.ApiUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class SeriesResume(val episodeId: Int, val season: Int, val episode: Int?, val positionMs: Long, val progress: Float)

data class SeriesState(
    val loading: Boolean = true,
    val error: String? = null,
    val series: TvSeries? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeason: Int = 1,
    val episodes: ContentRow? = null,
    val episodesLoading: Boolean = false,
    val resume: SeriesResume? = null,
    val firstEpisodeId: Int? = null,
    val inMyList: Boolean? = null,
    /** Thumbs rating of the whole show (see Thumbs); null while loading. */
    val rating: Int? = null
)

@HiltViewModel
class TvSeriesDetailsViewModel @Inject constructor(
    private val repo: MediaRepository,
    private val progressStore: PlaybackProgressStore,
    private val personal: com.homeflix.tv.data.repository.PersonalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SeriesState())
    val state: StateFlow<SeriesState> = _state.asStateFlow()
    private var loadedId = -1
    private var seasonJob: Job? = null
    private val episodeCache = HashMap<Int, List<Episode>>()

    fun load(seriesId: Int) {
        if (loadedId == seriesId) { refreshProgress(); return }
        loadedId = seriesId
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val seriesD = async { runCatching { withTimeoutOrNull(10_000) { repo.getTvSeriesById(seriesId) } }.getOrNull() }
            val seasonsD = async { runCatching { withTimeoutOrNull(10_000) { repo.getTvSeriesSeasons(seriesId) } }.getOrNull().orEmpty() }
            val resumeD = async { findResume(seriesId) }
            val series = seriesD.await()
            if (series == null) {
                _state.value = SeriesState(loading = false, error = "Couldn't load this show.")
                return@launch
            }
            val seasons = seasonsD.await().sortedBy { it.seasonNumber }.filter { it.seasonNumber > 0 }.ifEmpty { seasonsD.await() }
            val resume = resumeD.await()
            val initialSeason = resume?.season ?: seasons.firstOrNull()?.seasonNumber ?: 1
            _state.update {
                it.copy(loading = false, series = series, seasons = seasons, resume = resume, selectedSeason = initialSeason)
            }
            selectSeason(initialSeason)
            launch {
                val inList = async { personal.inMyList(seriesId, series = true).getOrNull() }
                val rating = async { personal.rating(seriesId, series = true).getOrNull() }
                _state.update { it.copy(inMyList = inList.await() ?: false, rating = rating.await()) }
            }
            // First episode of the first season for "Play S1:E1".
            seasons.firstOrNull()?.seasonNumber?.let { first ->
                val eps = episodes(seriesId, first)
                _state.update { it.copy(firstEpisodeId = eps.firstOrNull()?.id) }
            }
        }
    }

    fun toggleMyList() {
        val id = loadedId.takeIf { it > 0 } ?: return
        val inList = _state.value.inMyList ?: false
        _state.update { it.copy(inMyList = !inList) }
        viewModelScope.launch {
            if (personal.setInMyList(id, series = true, add = !inList).isFailure) _state.update { it.copy(inMyList = inList) }
        }
    }

    fun rate(value: Int) {
        val id = loadedId.takeIf { it > 0 } ?: return
        val before = _state.value.rating
        _state.update { it.copy(rating = value) }
        viewModelScope.launch {
            if (personal.rate(id, series = true, value = value).isFailure) _state.update { it.copy(rating = before) }
        }
    }

    fun selectSeason(season: Int) {
        val seriesId = loadedId
        if (seriesId <= 0) return
        _state.update { it.copy(selectedSeason = season, episodesLoading = true) }
        seasonJob?.cancel()
        seasonJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val eps = episodes(seriesId, season)
            if (_state.value.selectedSeason != season) return@launch
            _state.update { it.copy(episodes = row(seriesId, season, eps), episodesLoading = false) }
        }
    }

    /** Back from the player: update progress bars + resume button without a loading flash. */
    fun refreshProgress() {
        val seriesId = loadedId
        if (seriesId <= 0) return
        viewModelScope.launch {
            val resume = findResume(seriesId)
            val season = _state.value.selectedSeason
            val eps = episodeCache[season]
            _state.update { it.copy(resume = resume, episodes = eps?.let { e -> row(seriesId, season, e) } ?: it.episodes) }
        }
    }

    private suspend fun episodes(seriesId: Int, season: Int): List<Episode> =
        episodeCache[season] ?: runCatching {
            withTimeoutOrNull(10_000) { repo.getTvSeriesEpisodes(seriesId, season) }
        }.getOrNull().orEmpty().also { if (it.isNotEmpty()) episodeCache[season] = it }

    private fun row(seriesId: Int, season: Int, eps: List<Episode>): ContentRow = ContentRow(
        id = "season-$season",
        title = "Season $season",
        style = RowStyle.LANDSCAPE,
        tiles = eps.mapIndexed { i, e ->
            val local = progressStore.get(e.id)
            val name = e.episodeTitle?.takeIf { it.isNotBlank() } ?: e.title
            Tile(
                key = "e${e.id}",
                kind = TileKind.EPISODE,
                id = e.id,
                seriesId = seriesId,
                title = "${i + 1}. $name",
                posterUrl = ApiUtils.getEpisodeStillUrl(e.id),
                backdropUrl = ApiUtils.getEpisodeStillUrl(e.id),
                meta = listOfNotNull("S$season:E${i + 1}", e.duration?.takeIf { it > 0 }?.let { MediaFormat.duration(it) })
                    .joinToString("  •  "),
                description = e.description?.takeIf { it.isNotBlank() },
                subtitle = e.duration?.takeIf { it > 0 }?.let { MediaFormat.duration(it) },
                progress = local?.takeIf { it.positionMs > 0 }?.fraction,
                resumeMs = local?.takeIf { !it.isFinished }?.positionMs ?: 0
            )
        }
    )

    private suspend fun findResume(seriesId: Int): SeriesResume? {
        val items = runCatching {
            withTimeoutOrNull(6_000) { repo.getRecentlyWatchedWithProgress().first().getOrNull() }
        }.getOrNull().orEmpty()
        val item = items.filter { it.media.seriesId == seriesId && it.durationSeconds > 0 }
            .maxByOrNull { it.lastWatchedAt.time } ?: return null
        val local = progressStore.get(item.mediaId)
        val posMs = local?.positionMs ?: (item.progressSeconds * 1000)
        val durMs = local?.durationMs ?: (item.durationSeconds * 1000)
        if (durMs <= 0 || posMs >= durMs * 0.95) return null
        return SeriesResume(
            episodeId = item.mediaId,
            season = item.media.seasonNumber ?: 1,
            episode = item.media.episodeNumber,
            positionMs = posMs,
            progress = (posMs.toFloat() / durMs).coerceIn(0f, 1f)
        )
    }
}

/** Season model shared across the TV-series screens and the repository. */
data class Season(
    val id: Int,
    val seasonNumber: Int,
    val name: String,
    val description: String?,
    val episodeCount: Int,
    val posterPath: String?
)
