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

    /** Called with every successfully built feed. */
    protected open fun onBuilt(feed: Feed) {}

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

    private var lastRefreshAt = 0L

    /**
     * Back from Details/Player: refresh quietly. Waits until the return
     * transition and focus restore are done, and skips if the feed is fresh.
     */
    fun refreshAfterReturn() {
        if (android.os.SystemClock.elapsedRealtime() - lastRefreshAt < 20_000) return
        viewModelScope.launch {
            kotlinx.coroutines.delay(1_200)
            refresh()
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            lastRefreshAt = android.os.SystemClock.elapsedRealtime()
            // All parsing/mapping/sorting off the main thread: the UI thread only
            // receives the finished feed, so D-pad input never waits on it.
            val result = runCatching { kotlinx.coroutines.withContext(Dispatchers.Default) { build() } }
            val feed = result.getOrNull()
            when {
                feed != null && !feed.isEmpty -> {
                    // Identical data → no emission → nothing on screen recomposes.
                    if (feed != _state.value.feed || _state.value.loading || _state.value.error != null) {
                        _state.value = FeedState(feed, loading = false)
                    }
                    cache.write(cacheName, feed)
                    onBuilt(feed)
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

    suspend fun recommendations(): List<Media> = runCatching {
        withTimeoutOrNull(6_000) {
            (repo.getPersonalizedRecommendations(20).body()?.takeIf { it.isNotEmpty() } ?: repo.getMixedRecommendations(20).body())
                ?.map { it.toDomain() }
        }
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

    fun byDate(movies: List<Media>) = movies.sortedByDescending { it.createdAt?.time ?: it.id.toLong() }

    fun top10(movies: List<Media>) = movies
        .sortedWith(compareByDescending<Media> { it.viewCount }.thenByDescending { it.rating }.thenBy { it.id })
        .take(10)

    /**
     * Billboard rotation: alternate newest additions with recommendations,
     * titles that have a trailer clip first, no duplicates.
     */
    fun heroMix(latest: List<Media>, recommended: List<Media>, size: Int = 6): List<Media> {
        fun hasClip(m: Media) = !m.previewClipPath.isNullOrBlank() || !m.previewPath.isNullOrBlank()
        val a = latest.sortedByDescending { hasClip(it) }
        val b = recommended.sortedByDescending { hasClip(it) }
        val out = LinkedHashMap<Int, Media>()
        var i = 0
        while (out.size < size && (i < a.size || i < b.size)) {
            a.getOrNull(i)?.let { out.putIfAbsent(it.id, it) }
            if (out.size < size) b.getOrNull(i)?.let { out.putIfAbsent(it.id, it) }
            i++
        }
        // Trailers first so the billboard starts moving right away.
        return out.values.sortedByDescending { hasClip(it) }
    }

    private val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    fun addedAt(series: TvSeries): Long =
        series.createdAt?.take(19)?.let { runCatching { isoDate.parse(it)?.time }.getOrNull() } ?: 0L

    /**
     * Home billboard: the most recently added titles released in [year] — movies
     * and shows together, newest addition first. Tops up with last year's newest
     * additions, then the newest additions overall, so it's never thin.
     */
    fun heroThisYear(
        movies: List<Media>,
        series: List<TvSeries>,
        year: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
        size: Int = 6
    ): List<Tile> {
        data class Pick(val added: Long, val year: Int?, val tile: Tile)
        val all = movies.filter { it.type == MediaType.MOVIE }
            .map { Pick(it.createdAt?.time ?: 0L, it.year, Tiles.movie(it, "hero")) } +
            series.map { Pick(addedAt(it), it.year, Tiles.series(it, "hero")) }
        val newestFirst = all.sortedByDescending { it.added }
        val picks = LinkedHashMap<String, Tile>()
        fun take(from: List<Pick>) = from.forEach { if (picks.size < size) picks.putIfAbsent(it.tile.key, it.tile) }
        take(newestFirst.filter { it.year == year })
        if (picks.size < 3) take(newestFirst.filter { it.year == year - 1 })
        if (picks.size < 3) take(newestFirst)
        return picks.values.toList()
    }

    /** Prefer a title with a trailer, a logo and a backdrop for billboards. */
    fun heroPick(candidates: List<Media>): Media? =
        candidates.maxByOrNull { m ->
            (if (!m.previewClipPath.isNullOrBlank()) 4 else 0) +
                (if (!m.logoPath.isNullOrBlank()) 2 else 0) +
                (if (!MediaFormat.description(m).isNullOrBlank()) 1 else 0)
        }

    fun continueWatching(
        items: List<RecentlyWatchedItem>,
        series: List<TvSeries> = emptyList(),
        filter: (RecentlyWatchedItem) -> Boolean = { true }
    ): ContentRow? {
        val byId = series.associateBy { it.id }
        // Newest first; one card per show (its latest episode), one per movie.
        val tiles = items.filter(filter)
            .sortedByDescending { it.lastWatchedAt.time }
            .distinctBy { it.media.seriesId?.takeIf { s -> s > 0 }?.let { s -> "s$s" } ?: "m${it.mediaId}" }
            .mapNotNull { Tiles.continueWatching(it, byId) }
            .take(15)
        return if (tiles.isEmpty()) null else ContentRow("continue", "Continue Watching", tiles, RowStyle.LANDSCAPE)
    }

    /** Movie genre rows (title → tiles, newest first), the viewer's favourite genres first. */
    fun genreCandidates(movies: List<Media>, topFeatures: List<String>?): List<Pair<String, List<Tile>>> =
        Taste.byTaste(GENRES, topFeatures) { title, keys ->
            val items = movies.filter { m -> mediaGenres(m).any { g -> keys.any { k -> g.contains(k) } } }
            if (items.isEmpty()) null else title to items.map { Tiles.movie(it, "g") }
        }

    /** Show genre rows, the viewer's favourite genres first. */
    fun seriesGenreCandidates(series: List<TvSeries>, topFeatures: List<String>?): List<Pair<String, List<Tile>>> =
        Taste.byTaste(GENRES, topFeatures) { title, keys ->
            val items = series.filter { s -> s.genres.any { g -> keys.any { k -> g.lowercase().contains(k) } } }
            if (items.isEmpty()) null else ("$title Shows".replace("Dramas Shows", "Drama Shows")
                .replace("Comedies Shows", "Comedy Shows").replace("Thrillers Shows", "Thriller Shows")
                .replace("Documentaries Shows", "Documentary Series")) to items.map { Tiles.series(it, "sg${title.take(3)}") }
        }

    fun isEpisode(item: RecentlyWatchedItem) =
        (item.media.seriesId ?: 0) > 0 || item.media.type != MediaType.MOVIE
}

// --------------------------------------------------------------------------------- pages

@HiltViewModel
class HomeFeedViewModel @Inject constructor(
    repo: MediaRepository,
    personal: com.homeflix.tv.data.repository.PersonalRepository,
    cache: FeedCache,
    private val homeScreen: com.homeflix.tv.launcher.HomeScreenPublisher,
    liveTv: com.homeflix.tv.livetv.LiveTvHome,
    comingSoon: com.homeflix.tv.comingsoon.ComingSoonHome
) : FeedViewModel(cache) {
    override val cacheName = "home"
    private val builder = HomeFeedBuilder(repo, personal, liveTv, comingSoon)
    override val lastError: Throwable? get() = builder.lastError

    init { start() }

    override suspend fun build(): Feed = builder.build()

    // Every fresh Home feed also refreshes HomeFlix's row on the TV home screen.
    override fun onBuilt(feed: Feed) = homeScreen.publishLater(feed)
}

/** Home's rows. Shared with the background job that keeps the TV home screen row fresh. */
class HomeFeedBuilder(
    repo: MediaRepository,
    private val personal: com.homeflix.tv.data.repository.PersonalRepository,
    private val liveTv: com.homeflix.tv.livetv.LiveTvHome? = null,
    private val comingSoon: com.homeflix.tv.comingsoon.ComingSoonHome? = null
) {
    private val load = Loaders(repo)
    val lastError: Throwable? get() = load.lastError

    suspend fun build(): Feed = coroutineScope {
        val moviesD = async { load.movies(300) }
        val trendingD = async { load.trending() }
        val recentD = async { load.recentlyWatched() }
        val seriesD = async { load.series() }
        val recommendedD = async { load.recommendations() }
        val myListD = async { load.myList() }
        val listedShowsD = async { personal.myListSeriesIds().getOrNull().orEmpty() }
        // Learned from what you watch, rate and add to My List; Top 10 from real plays.
        val forYouD = async { personal.forYou(20).getOrNull() }
        val chartD = async { personal.top10("all").getOrNull() }
        val liveD = async { runCatching { liveTv?.tiles() }.getOrNull() }
        val comingD = async { runCatching { comingSoon?.rows() }.getOrNull() }
        val movies = moviesD.await()
        val series = seriesD.await()
        if (movies.isEmpty() && series.isEmpty()) error("no content")
        val lib = Library(movies, series)
        val latest = Rows.byDate(movies)
        val heroTiles = Rows.heroThisYear(movies, series)
        val taste = forYouD.await()
        val themed = Taste.themed(taste)
        val seriesById = series.associateBy { it.id }
        val p = PagePlanner(librarySize = movies.size + series.size)

        // Claimed in priority order; [order] is where each row shows.
        Rows.continueWatching(recentD.await(), series)?.let { cw ->
            p.add(0, cw.id, cw.title, cw.tiles, RowStyle.LANDSCAPE, min = 1, exclusive = false)
        }
        val chart = chartD.await()
        (lib.top10(chart, "t") ?: Rows.top10(movies).mapIndexed { i, m -> Tiles.movie(m, "t", i + 1) }).let { top ->
            p.add(3, "top10", Charts.title("Titles", chart).replace("Top 10 Titles", "Top 10"), top, RowStyle.TOP10, min = 5, max = 10, exclusive = false)
        }
        val myList = listedShowsD.await().mapNotNull { seriesById[it] }.map { Tiles.series(it, "mls") } +
            myListD.await().map { Tiles.movie(it, "ml") }
        p.add(1, "mylist", "My List", myList, min = 1, exclusive = false)
        liveD.await()?.let { p.add(1, "livetv", "Live TV", it, RowStyle.CHANNEL, min = 1, max = com.homeflix.tv.livetv.LiveTvHome.MAX, exclusive = false) }
        themed["top-picks"]?.firstOrNull()?.let { r ->
            p.add(2, "recommended", r.title ?: "Top Picks for You", lib.tiles(r, "fy"), max = 15)
        } ?: p.add(2, "recommended", "Recommended for You",
            recommendedD.await().distinctBy { it.id }.map { Tiles.movie(it, "rc") })
        val newest = (movies.filter { it.type == MediaType.MOVIE }.map { (it.createdAt?.time ?: 0L) to Tiles.movie(it, "n") } +
            series.map { Rows.addedAt(it) to Tiles.series(it, "ns") }).sortedByDescending { it.first }.map { it.second }
        p.add(4, "new", "New on HomeFlix", newest)
        // Coming Soon (not in the library, so never a duplicate): right after what's new.
        comingD.await()?.let { (movies, series) ->
            p.add(4, "coming-movies", "Coming Soon: Movies", movies, RowStyle.COMING, min = 1, exclusive = false)
            p.add(4, "coming-series", "Coming Soon: Series", series, RowStyle.COMING, min = 1, exclusive = false)
        }
        themed["because"].orEmpty().take(3).forEachIndexed { i, r -> p.add(5 + i, "because$i", r.title ?: "Because You Watched", lib.tiles(r, "by$i"), min = 3) }
        themed["collection"].orEmpty().take(1).forEach { r -> p.add(8, "collection", r.title ?: "Continue the Collection", lib.tiles(r, "co"), min = 2) }
        themed["new-for-you"]?.firstOrNull()?.let { r -> p.add(9, "new-for-you", r.title ?: "New for You", lib.tiles(r, "nf"), min = 3) }
        (themed["actor"].orEmpty().take(2) + themed["director"].orEmpty().take(1)).forEachIndexed { i, r ->
            p.add(10 + i, "people$i", r.title ?: "More Like This", lib.tiles(r, "pp$i"), min = 3)
        }
        themed["hidden-gems"]?.firstOrNull()?.let { r -> p.add(13, "gems", r.title ?: "Hidden Gems for You", lib.tiles(r, "hg")) }
        trendingD.await().ifEmpty { movies.sortedByDescending { it.rating } }.let { t ->
            p.add(14, "trending", "Trending Now", t.map { Tiles.movie(it, "tr") })
        }
        p.add(15, "shows", "TV Shows", series.sortedByDescending { Rows.addedAt(it) }.map { Tiles.series(it) })
        p.add(16, "acclaimed", "Critically Acclaimed", movies.filter { it.rating >= 7.5 }.sortedByDescending { it.rating }.map { Tiles.movie(it, "ca") })
        Rows.genreCandidates(latest, taste?.topFeatures).forEachIndexed { i, (title, tiles) ->
            p.add(20 + i, "h-genre-$title", title, tiles.map { it.copy(key = "h${title.take(3)}${it.id}") }, min = 5)
        }
        Feed(hero = heroTiles.firstOrNull(), rows = p.rows, heroes = heroTiles)
    }
}

@HiltViewModel
class ShowsFeedViewModel @Inject constructor(
    repo: MediaRepository,
    private val personal: com.homeflix.tv.data.repository.PersonalRepository,
    cache: FeedCache
) : FeedViewModel(cache) {
    override val cacheName = "shows"
    private val load = Loaders(repo)
    override val lastError: Throwable? get() = load.lastError

    init { start() }

    override suspend fun build(): Feed = coroutineScope {
        val seriesD = async { load.series() }
        val recentD = async { load.recentlyWatched() }
        val forYouD = async { personal.forYou(20, kind = "series").getOrNull() }
        val chartD = async { personal.top10("series").getOrNull() }
        val series = seriesD.await()
        if (series.isEmpty()) error("no series")
        val lib = Library(emptyList(), series)
        val latest = series.sortedByDescending { Rows.addedAt(it) }
        val taste = forYouD.await()
        val themed = Taste.themed(taste)
        val p = PagePlanner(librarySize = series.size)

        Rows.continueWatching(recentD.await(), series, Rows::isEpisode)?.let { cw ->
            p.add(0, cw.id, cw.title, cw.tiles, RowStyle.LANDSCAPE, min = 1, exclusive = false)
        }
        val chart = chartD.await()
        lib.top10(chart, "st")?.let { top -> p.add(2, "shows-top10", Charts.title("Shows", chart), top, RowStyle.TOP10, min = 5, max = 10, exclusive = false) }
        themed["top-picks"]?.firstOrNull()?.let { r -> p.add(1, "shows-picks", "Top Picks for You", lib.tiles(r, "sp"), min = 3, max = 15) }
        p.add(3, "shows-new", "New Episodes & Shows", latest.map { Tiles.series(it, "sn") }, min = 3)
        themed["because"].orEmpty().take(2).forEachIndexed { i, r -> p.add(4 + i, "shows-because$i", r.title ?: "Because You Watched", lib.tiles(r, "sb$i"), min = 3) }
        themed["new-for-you"]?.firstOrNull()?.let { r -> p.add(6, "shows-nfy", r.title ?: "New for You", lib.tiles(r, "snf"), min = 3) }
        p.add(7, "shows-top", "Top Rated Shows", series.filter { it.rating > 0 }.sortedByDescending { it.rating }.map { s -> Tiles.series(s, "st") })
        Rows.seriesGenreCandidates(latest, taste?.topFeatures).forEachIndexed { i, (title, tiles) ->
            p.add(10 + i, "shows-genre-$title", title, tiles, min = 4)
        }
        p.leftovers(99, "shows-more", "More Shows", series.sortedBy { it.title.lowercase() }.map { Tiles.series(it, "sa") })
        val heroTiles = (latest.filter { !it.description.isNullOrBlank() }.take(5).ifEmpty { latest.take(5) })
            .map { Tiles.series(it, "hero") }
        Feed(hero = heroTiles.firstOrNull(), rows = p.rows, heroes = heroTiles)
    }
}

@HiltViewModel
class MoviesFeedViewModel @Inject constructor(
    repo: MediaRepository,
    private val personal: com.homeflix.tv.data.repository.PersonalRepository,
    cache: FeedCache
) : FeedViewModel(cache) {
    override val cacheName = "movies"
    private val load = Loaders(repo)
    override val lastError: Throwable? get() = load.lastError

    init { start() }

    override suspend fun build(): Feed = coroutineScope {
        val moviesD = async { load.movies(300) }
        val recentD = async { load.recentlyWatched() }
        val forYouD = async { personal.forYou(20, kind = "movie").getOrNull() }
        val chartD = async { personal.top10("movie").getOrNull() }
        val movies = moviesD.await().filter { it.type == MediaType.MOVIE }
        if (movies.isEmpty()) error("no movies")
        val lib = Library(movies, emptyList())
        val latest = Rows.byDate(movies)
        val topRated = movies.filter { it.rating > 0 }.sortedByDescending { it.rating }
        val taste = forYouD.await()
        val themed = Taste.themed(taste)
        val p = PagePlanner(librarySize = movies.size)

        Rows.continueWatching(recentD.await()) { !Rows.isEpisode(it) }?.let { cw ->
            p.add(0, cw.id, cw.title, cw.tiles, RowStyle.LANDSCAPE, min = 1, exclusive = false)
        }
        val chart = chartD.await()
        (lib.top10(chart, "mt") ?: Rows.top10(movies).mapIndexed { i, m -> Tiles.movie(m, "mt", i + 1) }).let { top ->
            p.add(2, "movies-top10", Charts.title("Movies", chart), top, RowStyle.TOP10, min = 5, max = 10, exclusive = false)
        }
        themed["top-picks"]?.firstOrNull()?.let { r -> p.add(1, "movies-picks", "Top Picks for You", lib.tiles(r, "mp"), max = 15) }
        p.add(3, "movies-new", "Recently Added Movies", latest.map { Tiles.movie(it, "mn") })
        themed["because"].orEmpty().take(3).forEachIndexed { i, r -> p.add(4 + i, "movies-because$i", r.title ?: "Because You Watched", lib.tiles(r, "mb$i"), min = 3) }
        themed["collection"].orEmpty().take(1).forEach { r -> p.add(7, "movies-collection", r.title ?: "Continue the Collection", lib.tiles(r, "mc"), min = 2) }
        themed["new-for-you"]?.firstOrNull()?.let { r -> p.add(8, "movies-nfy", r.title ?: "New for You", lib.tiles(r, "mnf"), min = 3) }
        (themed["actor"].orEmpty().take(2) + themed["director"].orEmpty().take(1)).forEachIndexed { i, r ->
            p.add(9 + i, "movies-people$i", r.title ?: "More Like This", lib.tiles(r, "mpp$i"), min = 3)
        }
        themed["hidden-gems"]?.firstOrNull()?.let { r -> p.add(12, "movies-gems", r.title ?: "Hidden Gems for You", lib.tiles(r, "mg")) }
        p.add(13, "movies-rated", "Critically Acclaimed", topRated.map { Tiles.movie(it, "mr") })
        Rows.genreCandidates(latest, taste?.topFeatures).forEachIndexed { i, (title, tiles) ->
            p.add(20 + i, "mv-genre-$title", title, tiles.map { it.copy(key = "mv${title.take(3)}${it.id}") }, min = 5)
        }
        p.leftovers(99, "movies-more", "More Movies", movies.sortedBy { it.title.lowercase() }.map { Tiles.movie(it, "ma") })
        val heroTiles = Rows.heroMix(latest.take(10), topRated.take(10)).map { Tiles.movie(it, "hero") }
        Feed(hero = heroTiles.firstOrNull(), rows = p.rows, heroes = heroTiles)
    }
}

@HiltViewModel
class MyHomeFlixViewModel @Inject constructor(
    repo: MediaRepository,
    private val personal: com.homeflix.tv.data.repository.PersonalRepository,
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
        val listedShowsD = async { personal.myListSeriesIds().getOrNull().orEmpty() }
        val seriesD = async { if (listedShowsD.await().isEmpty()) emptyList() else load.series() }
        val recent = recentD.await()
        val myList = listD.await()
        val seriesById = seriesD.await().associateBy { it.id }
        val listedShows = listedShowsD.await().mapNotNull { seriesById[it] }.map { Tiles.series(it, "mls") }
        val rows = buildList {
            Rows.continueWatching(recent)?.let(::add)
            val listTiles = listedShows + myList.map { Tiles.movie(it, "ml") }
            if (listTiles.isNotEmpty()) add(ContentRow("mylist", "My List", listTiles))
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
