package com.homeflix.tv.presentation.screens.feeds

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.homeflix.tv.data.remote.dto.toDomain
import com.homeflix.tv.domain.model.Media
import com.homeflix.tv.domain.model.MediaType
import com.homeflix.tv.domain.model.Notification
import com.homeflix.tv.domain.model.RecentlyWatchedItem
import com.homeflix.tv.domain.repository.MediaRepository
import com.homeflix.tv.presentation.screens.tvshows.TvSeries
import com.homeflix.tv.presentation.ui.ContentRow
import com.homeflix.tv.presentation.ui.Feed
import com.homeflix.tv.presentation.ui.MediaFormat
import com.homeflix.tv.presentation.ui.RowStyle
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.Tiles
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class FeedState(
    val feed: Feed = Feed(),
    val loading: Boolean = true,
    val error: String? = null
)

/** Stores each page's last built feed so the app paints instantly on launch. */
@Singleton
class FeedCache @Inject constructor(@ApplicationContext private val context: Context) {
    private val gson = Gson()
    private fun file(name: String) = File(context.filesDir, "feed_v5_$name.json")

    suspend fun read(name: String): Feed? = withContext(Dispatchers.IO) {
        runCatching { gson.fromJson(file(name).readText(), Feed::class.java) }.getOrNull()
            ?.takeIf { it.rows != null && !it.isEmpty }
    }

    suspend fun write(name: String, feed: Feed) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.filesDir, "feed_v5_$name.tmp")
            tmp.writeText(gson.toJson(feed))
            tmp.renameTo(file(name))
        }
    }
}

/**
 * Cache-first feed: paint the last known feed immediately, then rebuild from
 * the network in parallel and swap in place (rows/tiles are keyed, so focus
 * and scroll survive the swap).
 */
abstract class FeedViewModel(private val cache: FeedCache) : ViewModel() {
    protected val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()
    protected abstract val cacheName: String
    private var refreshJob: Job? = null
    private var started = false

    protected abstract suspend fun build(): Feed

    /** Most recent loader failure, shown on the error screen so problems are diagnosable. */
    protected open val lastError: Throwable? get() = null

    protected fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            cache.read(cacheName)?.let { cached ->
                if (_state.value.feed.isEmpty) _state.value = FeedState(cached, loading = false)
            }
            refresh()
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val result = runCatching { build() }
            val feed = result.getOrNull()
            when {
                feed != null && !feed.isEmpty -> {
                    _state.value = FeedState(feed, loading = false)
                    cache.write(cacheName, feed)
                }
                _state.value.feed.isEmpty -> _state.value = FeedState(
                    loading = false,
                    error = describeFailure(lastError ?: result.exceptionOrNull())
                )
                else -> _state.value = _state.value.copy(loading = false)
            }
            result.exceptionOrNull()?.let { Log.w("Feed", "$cacheName refresh failed", it) }
        }
    }
}

private fun describeFailure(e: Throwable?): String {
    val server = com.homeflix.tv.util.ServerConfig.activeApiBase.removeSuffix("/api")
    val why = when (e) {
        null -> return "Nothing here yet."
        is java.net.ConnectException, is java.net.NoRouteToHostException -> "connection refused or no route"
        is java.net.SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException -> "timed out"
        is java.net.UnknownHostException -> "unknown host"
        else -> e.message?.take(140) ?: e.javaClass.simpleName
    }
    return "Can't load from $server ($why)"
}

/** Shared loaders, each bounded so one slow endpoint can never stall a page. */
internal class Loaders(private val repo: MediaRepository) {
    @Volatile var lastError: Throwable? = null

    suspend fun movies(limit: Int = 120): List<Media> = runCatching {
        kotlinx.coroutines.withTimeout(15_000) { repo.getMovies(limit = limit).first().getOrThrow() }
    }.onFailure { lastError = it }.getOrNull().orEmpty()

    suspend fun trending(): List<Media> = runCatching {
        withTimeoutOrNull(6_000) { repo.getTrendingRecommendations(20).body()?.map { it.toDomain() } }
    }.getOrNull().orEmpty()

    suspend fun recentlyWatched(): List<RecentlyWatchedItem> = runCatching {
        withTimeoutOrNull(6_000) { repo.getRecentlyWatchedWithProgress().first().getOrNull() }
    }.getOrNull().orEmpty().sortedByDescending { it.lastWatchedAt.time }

    suspend fun series(): List<TvSeries> = runCatching {
        kotlinx.coroutines.withTimeout(15_000) { repo.getTvSeries() }
    }.onFailure { lastError = it }.getOrNull().orEmpty()

    suspend fun myList(): List<Media> = runCatching {
        withTimeoutOrNull(6_000) { repo.getMyList().getOrNull() }
    }.getOrNull().orEmpty().sortedByDescending { it.addedAt.time }.map { it.media }

    suspend fun notifications(): List<Notification> = runCatching {
        withTimeoutOrNull(6_000) { repo.getNotifications(30) }
    }.getOrNull().orEmpty()
}

internal object Rows {
    val GENRES = listOf(
        "Action" to listOf("action"),
        "Comedies" to listOf("comedy"),
        "Dramas" to listOf("drama"),
        "Sci-Fi" to listOf("science fiction", "sci-fi"),
        "Thrillers" to listOf("thriller"),
        "Crime" to listOf("crime"),
        "Horror" to listOf("horror"),
        "Romance" to listOf("romance"),
        "Animation" to listOf("animation"),
        "Family" to listOf("family"),
        "Adventure" to listOf("adventure"),
        "Mystery" to listOf("mystery"),
        "Documentaries" to listOf("documentary")
    )

    fun mediaGenres(m: Media): List<String> =
        (m.genreNames + m.genres.map { it.name }).map { it.lowercase() }

    fun genreRows(prefix: String, movies: List<Media>, min: Int = 5, label: (String) -> String = { it }): List<ContentRow> =
        GENRES.mapNotNull { (title, keys) ->
            val items = movies.filter { m -> mediaGenres(m).any { g -> keys.any { k -> g.contains(k) } } }
            if (items.size >= min) ContentRow("$prefix-genre-$title", label(title), items.take(20).map { Tiles.movie(it, "$prefix${title.take(3)}") })
            else null
        }

    fun seriesGenreRows(series: List<TvSeries>, min: Int = 4): List<ContentRow> =
        GENRES.mapNotNull { (title, keys) ->
            val items = series.filter { s -> s.genres.any { g -> keys.any { k -> g.lowercase().contains(k) } } }
            if (items.size >= min) ContentRow("shows-genre-$title", "$title Shows".replace("Dramas Shows", "Drama Shows")
                .replace("Comedies Shows", "Comedy Shows").replace("Thrillers Shows", "Thriller Shows"),
                items.take(20).map { Tiles.series(it, "sg${title.take(3)}") })
            else null
        }

    fun byDate(movies: List<Media>) = movies.sortedByDescending { it.createdAt?.time ?: it.id.toLong() }

    fun top10(movies: List<Media>) = movies
        .sortedWith(compareByDescending<Media> { it.viewCount }.thenByDescending { it.rating }.thenBy { it.id })
        .take(10)

    /** Prefer a title with a trailer, a logo and a backdrop for billboards. */
    fun heroPick(candidates: List<Media>): Media? =
        candidates.maxByOrNull { m ->
            (if (!m.previewClipPath.isNullOrBlank()) 4 else 0) +
                (if (!m.logoPath.isNullOrBlank()) 2 else 0) +
                (if (!MediaFormat.description(m).isNullOrBlank()) 1 else 0)
        }

    fun continueWatching(items: List<RecentlyWatchedItem>, filter: (RecentlyWatchedItem) -> Boolean = { true }): ContentRow? {
        val tiles = items.filter(filter)
            .distinctBy { it.media.seriesId?.takeIf { s -> s > 0 }?.let { s -> "s$s" } ?: "m${it.mediaId}" }
            .mapNotNull { Tiles.continueWatching(it) }
            .take(15)
        return if (tiles.isEmpty()) null else ContentRow("continue", "Continue Watching", tiles, RowStyle.LANDSCAPE)
    }

    fun isEpisode(item: RecentlyWatchedItem) =
        (item.media.seriesId ?: 0) > 0 || item.media.type != MediaType.MOVIE
}

// --------------------------------------------------------------------------------- pages

@HiltViewModel
class HomeFeedViewModel @Inject constructor(
    repo: MediaRepository,
    cache: FeedCache
) : FeedViewModel(cache) {
    override val cacheName = "home"
    private val load = Loaders(repo)
    override val lastError: Throwable? get() = load.lastError

    init { start() }

    override suspend fun build(): Feed = coroutineScope {
        val moviesD = async { load.movies() }
        val trendingD = async { load.trending() }
        val recentD = async { load.recentlyWatched() }
        val seriesD = async { load.series() }
        val movies = moviesD.await()
        val series = seriesD.await()
        if (movies.isEmpty() && series.isEmpty()) error("no content")
        val latest = Rows.byDate(movies)
        val hero = Rows.heroPick(latest.take(10))
        val rows = buildList {
            Rows.continueWatching(recentD.await())?.let(::add)
            Rows.top10(movies).takeIf { it.size >= 5 }?.let { top ->
                add(ContentRow("top10", "Top 10 Movies on HomeFlix Today",
                    top.mapIndexed { i, m -> Tiles.movie(m, "t", rank = i + 1) }, RowStyle.TOP10))
            }
            trendingD.await().ifEmpty { movies.sortedByDescending { it.rating }.take(20) }.let { t ->
                if (t.isNotEmpty()) add(ContentRow("trending", "Trending Now", t.take(20).map { Tiles.movie(it, "tr") }))
            }
            if (latest.isNotEmpty()) add(ContentRow("new", "New on HomeFlix", latest.take(20).map { Tiles.movie(it, "n") }))
            if (series.isNotEmpty()) {
                add(ContentRow("shows", "TV Shows", series.sortedByDescending { it.createdAt }.take(20).map { Tiles.series(it) }))
            }
            addAll(Rows.genreRows("h", latest))
        }
        Feed(hero = hero?.let { Tiles.movie(it, "hero") }, rows = rows)
    }
}

@HiltViewModel
class ShowsFeedViewModel @Inject constructor(
    repo: MediaRepository,
    cache: FeedCache
) : FeedViewModel(cache) {
    override val cacheName = "shows"
    private val load = Loaders(repo)
    override val lastError: Throwable? get() = load.lastError

    init { start() }

    override suspend fun build(): Feed = coroutineScope {
        val seriesD = async { load.series() }
        val recentD = async { load.recentlyWatched() }
        val series = seriesD.await()
        if (series.isEmpty()) error("no series")
        val latest = series.sortedByDescending { it.createdAt }
        val rows = buildList {
            Rows.continueWatching(recentD.await(), Rows::isEpisode)?.let(::add)
            add(ContentRow("shows-new", "New Episodes & Shows", latest.take(20).map { Tiles.series(it, "sn") }))
            series.filter { it.rating > 0 }.sortedByDescending { it.rating }.take(20).takeIf { it.size >= 5 }?.let {
                add(ContentRow("shows-top", "Top Rated Shows", it.map { s -> Tiles.series(s, "st") }))
            }
            addAll(Rows.seriesGenreRows(latest))
            if (latest.size > 20) add(ContentRow("shows-all", "All Shows", latest.sortedBy { it.title }.map { Tiles.series(it, "sa") }))
        }
        val hero = latest.firstOrNull { !it.description.isNullOrBlank() } ?: latest.first()
        Feed(hero = Tiles.series(hero, "hero"), rows = rows)
    }
}

@HiltViewModel
class MoviesFeedViewModel @Inject constructor(
    repo: MediaRepository,
    cache: FeedCache
) : FeedViewModel(cache) {
    override val cacheName = "movies"
    private val load = Loaders(repo)
    override val lastError: Throwable? get() = load.lastError

    init { start() }

    override suspend fun build(): Feed = coroutineScope {
        val moviesD = async { load.movies(200) }
        val recentD = async { load.recentlyWatched() }
        val movies = moviesD.await()
        if (movies.isEmpty()) error("no movies")
        val latest = Rows.byDate(movies)
        val topRated = movies.filter { it.rating > 0 }.sortedByDescending { it.rating }
        val rows = buildList {
            Rows.continueWatching(recentD.await()) { !Rows.isEpisode(it) }?.let(::add)
            add(ContentRow("movies-new", "Recently Added Movies", latest.take(20).map { Tiles.movie(it, "mn") }))
            Rows.top10(movies).takeIf { it.size >= 5 }?.let { top ->
                add(ContentRow("movies-top10", "Top 10 Movies", top.mapIndexed { i, m -> Tiles.movie(m, "mt", i + 1) }, RowStyle.TOP10))
            }
            if (topRated.size >= 5) add(ContentRow("movies-rated", "Critically Acclaimed", topRated.take(20).map { Tiles.movie(it, "mr") }))
            addAll(Rows.genreRows("mv", latest, label = { "$it" }))
        }
        val hero = Rows.heroPick(topRated.take(12).ifEmpty { latest.take(12) })
        Feed(hero = hero?.let { Tiles.movie(it, "hero") }, rows = rows)
    }
}

@HiltViewModel
class MyHomeFlixViewModel @Inject constructor(
    repo: MediaRepository,
    cache: FeedCache
) : FeedViewModel(cache) {
    override val cacheName = "mine"
    private val load = Loaders(repo)
    override val lastError: Throwable? get() = load.lastError

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    init {
        start()
        viewModelScope.launch { _notifications.value = load.notifications().filter { it.isLocal } }
    }

    override suspend fun build(): Feed = coroutineScope {
        val recentD = async { load.recentlyWatched() }
        val listD = async { load.myList() }
        val recent = recentD.await()
        val myList = listD.await()
        val rows = buildList {
            Rows.continueWatching(recent)?.let(::add)
            if (myList.isNotEmpty()) add(ContentRow("mylist", "My List", myList.map { Tiles.movie(it, "ml") }))
            val watched = recent.filter { !Rows.isEpisode(it) && it.durationSeconds > 0 &&
                it.progressSeconds >= it.durationSeconds * 0.9 }
            if (watched.isNotEmpty()) add(ContentRow("watched", "Watch It Again", watched.map { Tiles.movie(it.media, "wa") }))
        }
        if (rows.isEmpty()) error("empty")
        Feed(hero = null, rows = rows)
    }
}

/** Utility for screens that only need Tile lookups (e.g. search results). */
fun List<Media>.toTiles(prefix: String): List<Tile> = map { Tiles.movie(it, prefix) }
