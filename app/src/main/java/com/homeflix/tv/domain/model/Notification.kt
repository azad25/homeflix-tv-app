package com.homeflix.tv.domain.model

/**
 * A backend-generated notification. The TV app shows ONLY notifications that
 * reference LOCAL library content (movies/series/episodes present on the
 * server) and lets the user select one to open that item.
 */
data class Notification(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val logoUrl: String? = null,
    val rating: Double = 0.0,
    val year: Int? = null,
    val overview: String? = null,
    val category: String? = null,
    val timestampSeconds: Long = 0L,
    val read: Boolean = false,
    // Local library references (used for filtering + navigation)
    val movieIds: List<Int> = emptyList(),
    val seriesId: Int = 0,
    val episodeIds: List<Int> = emptyList()
) {
    /** True when this notification points at content in the local library. */
    val isLocal: Boolean
        get() = seriesId > 0 || movieIds.isNotEmpty() || episodeIds.isNotEmpty()

    /** Navigation target: "series" or "movie", with the id to open. */
    val targetType: String?
        get() = when {
            seriesId > 0 -> "series"
            movieIds.isNotEmpty() -> "movie"
            episodeIds.isNotEmpty() -> "movie" // Details route loads any media id
            else -> null
        }

    val targetId: Int?
        get() = when {
            seriesId > 0 -> seriesId
            movieIds.isNotEmpty() -> movieIds.first()
            episodeIds.isNotEmpty() -> episodeIds.first()
            else -> null
        }
}
