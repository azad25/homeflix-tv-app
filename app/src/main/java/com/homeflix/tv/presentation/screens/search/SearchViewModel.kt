package com.homeflix.tv.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.data.remote.dto.toDomain
import com.homeflix.tv.domain.repository.MediaRepository
import com.homeflix.tv.presentation.screens.tvshows.TvSeries
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.Tiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val genre: String? = null,
    val title: String = "Your Search Recommendations",
    val results: List<Tile> = emptyList(),
    val loading: Boolean = false,
    val genres: List<String> = DEFAULT_GENRES
)

private val DEFAULT_GENRES = listOf(
    "Action", "Comedy", "Drama", "Thriller", "Science Fiction", "Horror", "Romance",
    "Animation", "Crime", "Adventure", "Family", "Mystery", "Documentary"
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: MediaRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState(loading = true))
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var recommendations: List<Tile> = emptyList()
    private var series: List<TvSeries> = emptyList()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val trending = async {
                runCatching { withTimeoutOrNull(6_000) { repo.getTrendingRecommendations(30).body() } }.getOrNull()
                    ?.map { it.toDomain() }.orEmpty()
            }
            val latest = async { withTimeoutOrNull(8_000) { repo.getMovies(limit = 40).first().getOrNull() }.orEmpty() }
            val shows = async { runCatching { withTimeoutOrNull(10_000) { repo.getTvSeries() } }.getOrNull().orEmpty() }
            series = shows.await()
            val movies = (trending.await() + latest.await().sortedByDescending { it.createdAt?.time ?: 0 })
                .distinctBy { it.id }.take(24)
            recommendations = (movies.map { Tiles.movie(it, "r") } + series.take(8).map { Tiles.series(it, "rs") })
            if (_state.value.query.isBlank() && _state.value.genre == null) {
                _state.update { it.copy(results = recommendations, loading = false) }
            }
        }
    }

    fun type(char: String) = setQuery(_state.value.query + char)
    fun backspace() = setQuery(_state.value.query.dropLast(1))
    fun clear() = setQuery("")

    fun setQuery(q: String) {
        _state.update { it.copy(query = q, genre = null) }
        job?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(title = "Your Search Recommendations", results = recommendations, loading = false) }
            return
        }
        job = viewModelScope.launch {
            delay(300) // debounce typing
            _state.update { it.copy(loading = true) }
            val movies = withTimeoutOrNull(8_000) { repo.searchMedia(q, 40).first().getOrNull() }.orEmpty()
            val shows = series.filter { it.title.contains(q, ignoreCase = true) }
            val tiles = shows.map { Tiles.series(it, "qs") } +
                movies.distinctBy { it.seriesId ?: -it.id }.map { m ->
                    if (m.seriesId != null) {
                        series.firstOrNull { it.id == m.seriesId }?.let { Tiles.series(it, "qe") } ?: Tiles.movie(m, "q")
                    } else Tiles.movie(m, "q")
                }
            _state.update {
                it.copy(
                    title = if (tiles.isEmpty()) "No results for “$q”" else "Results for “$q”",
                    results = tiles.distinctBy { t -> t.key },
                    loading = false
                )
            }
        }
    }

    fun selectGenre(genre: String) {
        job?.cancel()
        _state.update { it.copy(genre = genre, query = "", loading = true, title = genre) }
        job = viewModelScope.launch {
            val movies = withTimeoutOrNull(8_000) { repo.getMediaByGenre(genre, 40, 0).first().getOrNull() }.orEmpty()
            val shows = series.filter { s -> s.genres.any { it.contains(genre, ignoreCase = true) } }
            _state.update {
                it.copy(results = movies.map { m -> Tiles.movie(m, "g") } + shows.map { s -> Tiles.series(s, "gs") }, loading = false)
            }
        }
    }
}
