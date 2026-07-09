package com.homeflix.tv.util

import com.homeflix.tv.BuildConfig
import com.homeflix.tv.domain.model.Media

object ApiUtils {
    
    fun getBaseUrl(): String {
        // Runtime server selection: LAN IP when reachable, domain otherwise.
        // Keeps image/stream URLs on the same server as API calls.
        return ServerConfig.activeApiBase
    }
    
    /**
     * Root URL without /api path - for endpoints served at root level
     * e.g. /logos/{filename}, /static/backdrops, etc.
     */
    fun getRootUrl(): String {
        return getBaseUrl().removeSuffix("/api")
    }
    
    fun getThumbnailUrl(media: Media): String {
        return if (!media.thumbnailPath.isNullOrEmpty()) {
            if (media.thumbnailPath.startsWith("http")) {
                media.thumbnailPath
            } else {
                "${getBaseUrl()}/thumbnails/${media.id}"
            }
        } else {
            "${getBaseUrl()}/thumbnails/${media.id}"
        }
    }
    
    fun getPosterUrl(media: Media): String {
        // For TV series episodes, use series poster endpoint
        media.seriesId?.let { seriesId ->
            return "${getBaseUrl()}/series/$seriesId/poster"
        }
        return if (!media.posterPath.isNullOrEmpty()) {
            if (media.posterPath.startsWith("http")) {
                media.posterPath
            } else {
                "${getBaseUrl()}/posters/${media.id}"
            }
        } else {
            "${getBaseUrl()}/posters/${media.id}"
        }
    }
    
    fun getBannerUrl(media: Media): String {
        return getBackdropUrl(media)
    }
    
    fun getBackdropUrl(media: Media): String {
        return when {
            // Local banner path(highest priority) - use /api/admin/assets/{filename} endpoint
            !media.bannerPath.isNullOrEmpty() && media.bannerPath.trim().isNotEmpty() -> {
                if (media.bannerPath.startsWith("http")) {
                    media.bannerPath
                } else {
                    val fileName = media.bannerPath.split("/").lastOrNull()
                    if (!fileName.isNullOrEmpty() && fileName.trim().isNotEmpty()) {
                        "${getBaseUrl()}/admin/assets/$fileName"
                    } else {
                        "${getBaseUrl()}/thumbnails/${media.id}"
                    }
                }
            }

            // TMDB backdrop URL (second priority)
            !media.tmdbBackdropUrl.isNullOrEmpty() && media.tmdbBackdropUrl.trim().isNotEmpty() -> {
                media.tmdbBackdropUrl
            }

            // Fallback to thumbnail
            else -> {
                if (!media.thumbnailPath.isNullOrEmpty()) {
                    if (media.thumbnailPath.startsWith("http")) {
                        media.thumbnailPath
                    } else {
                        "${getBaseUrl()}/thumbnails/${media.id}"
                    }
                } else {
                    "${getBaseUrl()}/thumbnails/${media.id}"
                }
            }
        }
    }
    
    fun getLogoUrl(media: Media): String? {
        // Movies: use logo_path from API if available (matching web frontend)
        // Web frontend pattern: `${apiUrl}/api/${movie.logo_path}`
        val logoPath = media.logoPath
        if (!logoPath.isNullOrEmpty() && logoPath.trim().isNotEmpty()) {
            return if (logoPath.startsWith("http")) {
                logoPath
            } else {
                "${getBaseUrl()}/$logoPath"
            }
        }
        // No logo_path available — return null so UI shows text fallback
        return null
    }
    
    fun getSeriesLogoUrl(seriesId: Int): String {
        return "${getBaseUrl()}/series/$seriesId/logo"
    }
    
    fun getImageUrl(media: Media, preferBanner: Boolean = false): String {
        return if (preferBanner) {
            getBannerUrl(media)
        } else {
            getPosterUrl(media)
        }
    }
    
    /**
     * ULTRA-INSTANT LAN STREAMING URL GENERATOR
     * 
     * Generates optimized streaming URLs for sub-millisecond response on LAN networks.
     * Matches the web frontend VideoPlayer.tsx getStreamUrl functionality.
     */
    fun getStreamUrl(
        mediaId: Int,
        quality: String? = null,
        format: String? = null,
        seekTime: Long? = null,
        // LAN optimization parameters
        optimize: String? = null,
        buffer: String? = null,
        latency: String? = null,
        preload: String? = null,
        network: String? = null,
        streaming: String? = null,
        cache: String? = null,
        io: String? = null,
        tcp: String? = null,
        response: String? = null
    ): String {
        val baseUrl = "${getBaseUrl()}/stream/$mediaId"
        val params = mutableListOf<String>()
        
        // ULTRA-INSTANT LAN STREAMING PARAMETERS - Sub-millisecond response
        optimize?.let { params.add("optimize=$it") }
        buffer?.let { params.add("buffer=$it") }
        latency?.let { params.add("latency=$it") }
        preload?.let { params.add("preload=$it") }
        network?.let { params.add("network=$it") }
        streaming?.let { params.add("streaming=$it") }
        cache?.let { params.add("cache=$it") }
        io?.let { params.add("io=$it") }
        tcp?.let { params.add("tcp=$it") }
        response?.let { params.add("response=$it") }
        
        // Maximum quality for LAN - no bandwidth limitations
        if (quality != null) {
            params.add("quality=$quality")
        } else {
            params.add("quality=4k-ultra") // 4K Ultra quality for LAN
        }
        
        // Force MP4 container for maximum compatibility and instant seeking
        if (format != null) {
            params.add("format=$format")
        } else {
            params.add("format=mp4-optimized")
        }
        
        // Enhanced seeking parameters for instant response
        if (seekTime != null && seekTime > 0) {
            params.add("t=${seekTime / 1000}") // Convert ms to seconds
            params.add("seek=${seekTime / 1000}")
            params.add("seek_mode=instant")
            params.add("buffer_ahead=60") // 60 seconds buffer ahead
        }
        
        // Additional LAN optimizations
        params.add("chunk_size=ultra-large")
        params.add("connection=keep-alive-optimized")
        params.add("compression=none") // No compression for LAN
        params.add("priority=ultra-high")
        
        return if (params.isNotEmpty()) {
            "$baseUrl?${params.joinToString("&")}"
        } else {
            baseUrl
        }
    }
    
    fun getPreviewClipUrl(mediaId: Int): String {
        return "${getBaseUrl()}/preview-clips/$mediaId"
    }
    
    fun getSubtitleUrl(mediaId: Int, trackId: Int): String {
        return "${getBaseUrl()}/media/$mediaId/subtitles/$trackId/file"
    }
    
    fun getAudioTracksUrl(mediaId: Int): String {
        return "${getBaseUrl()}/media/$mediaId/audio"
    }
    
    fun getSubtitleTracksUrl(mediaId: Int): String {
        return "${getBaseUrl()}/media/$mediaId/subtitles"
    }
    
    // TV Series specific methods - matching web app exactly
    fun getSeriesPosterUrl(series: com.homeflix.tv.presentation.screens.tvshows.TvSeries): String {
        // First try TMDB poster URL if available
        series.tmdbPosterUrl?.let { tmdbUrl ->
            if (tmdbUrl.startsWith("http") && tmdbUrl.trim().isNotEmpty()) {
                return tmdbUrl
            }
        }
        
        // Use local series poster endpoint: /api/series/{id}/poster
        return "${getBaseUrl()}/series/${series.id}/poster"
    }
    
    fun getSeriesBackdropUrl(series: com.homeflix.tv.presentation.screens.tvshows.TvSeries): String {
        // First try TMDB backdrop if available (high priority for backdrop)
        series.tmdbBackdropUrl?.let { tmdbUrl ->
            if (tmdbUrl.startsWith("http") && tmdbUrl.trim().isNotEmpty()) {
                return tmdbUrl
            }
        }
        
        // Use local series backdrop endpoint: /api/series/{id}/backdrop
        return "${getBaseUrl()}/series/${series.id}/backdrop"
    }
    
    fun getSeriesThumbnailUrl(seriesId: Int): String {
        return "${getBaseUrl()}/thumbnails/$seriesId"
    }
    
    fun getSeriesBannerUrl(series: com.homeflix.tv.presentation.screens.tvshows.TvSeries): String {
        return getSeriesBackdropUrl(series) // Use same logic as backdrop
    }
    
    /**
     * Episode still URL. ALWAYS points at the /episode-stills/{id} endpoint,
     * which serves the stored still OR auto-downloads it from TMDB on first
     * request. We must NOT gate this on episodeStillPath being present: the
     * backend omits that field (omitempty) when the DB value is empty, so
     * gating would mean the auto-download endpoint never runs (the bug).
     * Use getEpisodeThumbnailUrl(episode) as the Coil error/fallback.
     */
    fun getEpisodeStillUrl(episodeId: Int): String {
        return "${getBaseUrl()}/episode-stills/$episodeId"
    }

    fun getEpisodeThumbnailUrl(episode: com.homeflix.tv.presentation.screens.tvshows.Episode): String {
        return if (!episode.thumbnailPath.isNullOrEmpty()) {
            if (episode.thumbnailPath.startsWith("http")) {
                episode.thumbnailPath
            } else {
                "${getBaseUrl()}/thumbnails/${episode.id}"
            }
        } else {
            "${getBaseUrl()}/thumbnails/${episode.id}"
        }
    }
}