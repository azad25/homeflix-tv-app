package com.homeflix.tv.data.repository

import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.data.remote.dto.RatingBody
import com.homeflix.tv.data.remote.dto.TasteResultDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** Thumbs ratings. Values match the server: -1 not for me, 1 like, 2 love (0 = none). */
object Thumbs {
    const val NONE = 0
    const val DISLIKE = -1
    const val LIKE = 1
    const val LOVE = 2
}

/**
 * What the user tells HomeFlix about their taste — thumbs ratings and My List
 * for movies and whole shows — and the personalised rows the server learns
 * from it (plus watch history).
 */
@Singleton
class PersonalRepository @Inject constructor(private val api: HomeFlixApiService) {

    private suspend fun <T> call(block: suspend () -> T): Result<T> = try {
        Result.success(withTimeout(8_000) { block() })
    } catch (e: Exception) {
        if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
        Result.failure(e)
    }

    private fun kind(series: Boolean) = if (series) "series" else "movie"
    private fun listType(series: Boolean) = if (series) "series" else null

    suspend fun rating(id: Int, series: Boolean): Result<Int> = call {
        val r = api.getRating(kind(series), id)
        if (!r.isSuccessful) error("HTTP ${r.code()}")
        r.body()?.rating ?: Thumbs.NONE
    }

    suspend fun rate(id: Int, series: Boolean, value: Int): Result<Unit> = call {
        val r = api.putRating(kind(series), id, RatingBody(value))
        if (!r.isSuccessful) error("HTTP ${r.code()}")
    }

    suspend fun inMyList(id: Int, series: Boolean): Result<Boolean> = call {
        val r = api.checkMyList(id.toString(), listType(series))
        if (r.code() == 404) false else {
            if (!r.isSuccessful) error("HTTP ${r.code()}")
            r.body()?.inList ?: false
        }
    }

    suspend fun setInMyList(id: Int, series: Boolean, add: Boolean): Result<Unit> = call {
        val r = if (add) api.addToMyList(id.toString(), listType(series)) else api.removeFromMyList(id.toString(), listType(series))
        // Adding twice answers 500 "already exists": that's still "in the list".
        if (!r.isSuccessful && !(add && r.code() == 500)) error("HTTP ${r.code()}")
    }

    /** Series ids in My List, newest first. */
    suspend fun myListSeriesIds(): Result<List<Int>> = call {
        val r = api.getMyListEntries()
        if (!r.isSuccessful) error("HTTP ${r.code()}")
        r.body().orEmpty().filter { it.mediaType == "series" && it.mediaId > 0 }.map { it.mediaId }
    }

    /** Personalised rows; [kind] = "movie" / "series" for the Movies / Shows pages. No title repeats across rows. */
    suspend fun forYou(limit: Int = 20, kind: String? = null): Result<TasteResultDto> = call {
        val r = api.getForYou(limit, kind, 1)
        if (!r.isSuccessful) error("HTTP ${r.code()}")
        r.body() ?: TasteResultDto()
    }

    /** Top 10 by what's actually being watched. */
    suspend fun top10(kind: String = "all"): Result<com.homeflix.tv.data.remote.dto.ChartDto> = call {
        val r = api.getTop10(kind, 10)
        if (!r.isSuccessful) error("HTTP ${r.code()}")
        r.body() ?: com.homeflix.tv.data.remote.dto.ChartDto()
    }
}
