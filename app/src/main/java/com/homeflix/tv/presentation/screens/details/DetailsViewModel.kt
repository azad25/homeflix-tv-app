package com.homeflix.tv.presentation.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeflix.tv.core.PlaybackProgressStore
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.repository.MediaRepository
import com.homeflix.tv.presentation.ui.ContentRow
import com.homeflix.tv.presentation.ui.MediaFormat
import com.homeflix.tv.presentation.ui.Tiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class DetailsState(
    val loading: Boolean = true,
    val error: String? = null,
    val media: Media? = null,
    val resumeMs: Long = 0,
    val progress: Float? = null,
    val progressLabel: String? = null,
    val inMyList: Boolean? = null,
    val similar: ContentRow? = null
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repo: MediaRepository,
    private val progressStore: PlaybackProgressStore
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()
    private var loadedId = -1

    fun load(id: Int) {
        if (loadedId == id) { refreshProgress(); return }
        loadedId = id
        viewModelScope.launch {
            val media = withTimeoutOrNull(10_000) { repo.getMediaById(id.toString()).first().getOrNull() }
            if (media == null) {
                _state.value = DetailsState(loading = false, error = "Couldn't load this title.")
                return@launch
            }
            _state.update { it.copy(loading = false, media = media) }
            refreshProgress()
            val inList = async { runCatching { repo.checkMyList(id.toString()).getOrNull() }.getOrNull() }
            val similar = async { loadSimilar(media) }
            _state.update { it.copy(inMyList = inList.await(), similar = similar.await()) }
        }
    }

    /** Called on (re)entry — e.g. back from the player — without a loading flash. */
    fun refreshProgress() {
        val media = _state.value.media ?: return
        viewModelScope.launch {
            val local = progressStore.get(media.id)
            val resume = progressStore.resolveResumeMs(media.id, 0)
            val durMs = local?.durationMs?.takeIf { it > 0 } ?: (media.duration * 1000L)
            _state.update {
                it.copy(
                    resumeMs = resume,
                    progress = if (resume > 0 && durMs > 0) (resume.toFloat() / durMs).coerceIn(0f, 1f) else null,
                    progressLabel = if (resume > 0 && durMs > 0) {
                        val left = ((durMs - resume) / 60_000).toInt()
                        if (left > 0) "${MediaFormat.duration(left)} left" else null
                    } else null
                )
            }
        }
    }

    fun toggleMyList() {
        val media = _state.value.media ?: return
        val inList = _state.value.inMyList ?: false
        _state.update { it.copy(inMyList = !inList) }
        viewModelScope.launch {
            val r = if (inList) repo.removeFromMyList(media.id.toString()) else repo.addToMyList(media.id.toString())
            if (r.isFailure) _state.update { it.copy(inMyList = inList) }
        }
    }

    private suspend fun loadSimilar(media: Media): ContentRow? {
        val genre = MediaFormat.genreOf(media)
        var items: List<Media> = emptyList()
        if (!genre.isNullOrBlank()) {
            items = withTimeoutOrNull(6_000) { repo.getMediaByGenre(genre, 24, 0).first().getOrNull() }.orEmpty()
        }
        if (items.size < 4) {
            items = items + withTimeoutOrNull(6_000) { repo.getMovies(limit = 30).first().getOrNull() }.orEmpty()
        }
        val tiles = items.filter { it.id != media.id }.distinctBy { it.id }.take(20).map { Tiles.movie(it, "sim") }
        return if (tiles.isEmpty()) null else ContentRow("similar", "More Like This", tiles)
    }
}
