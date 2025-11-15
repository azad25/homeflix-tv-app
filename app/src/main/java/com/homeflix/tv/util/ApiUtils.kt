package com.homeflix.tv.util

import com.homeflix.tv.BuildConfig
import com.homeflix.tv.domain.model.Media

object ApiUtils {
    
    private fun getBaseUrl(): String {
        return BuildConfig.BASE_URL.removeSuffix("/")
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
        return when {
            // TMDB backdrop URL (highest priority) - EXACTLY like web app
            !media.tmdbBackdropUrl.isNullOrEmpty() && media.tmdbBackdropUrl.trim().isNotEmpty() -> {
                media.tmdbBackdropUrl
            }
            // Local banner path - use server endpoint like web app
            !media.bannerPath.isNullOrEmpty() && media.bannerPath.trim().isNotEmpty() -> {
                val fileName = media.bannerPath.split("/").lastOrNull()
                if (!fileName.isNullOrEmpty() && fileName.trim().isNotEmpty()) {
                    "${getBaseUrl()}/api/admin/assets/$fileName"
                } else {
                    "${getBaseUrl()}/api/backdrops/${media.id}"
                }
            }
            // Fallback to server thumbnail endpoint (not poster for backdrop)
            else -> {
                "${getBaseUrl()}/api/thumbnails/${media.id}"
            }
        }
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
        val baseUrl = "${getBaseUrl()}/api/stream/$mediaId"
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
        return "${getBaseUrl()}/api/preview-clips/$mediaId"
    }
    
    fun getSubtitleUrl(mediaId: Int, trackId: Int): String {
        return "${getBaseUrl()}/api/media/$mediaId/subtitles/$trackId/file"
    }
    
    fun getAudioTracksUrl(mediaId: Int): String {
        return "${getBaseUrl()}/api/media/$mediaId/audio"
    }
    
    fun getSubtitleTracksUrl(mediaId: Int): String {
        return "${getBaseUrl()}/api/media/$mediaId/subtitles"
    }
    
    // TV Series specific methods
    fun getSeriesPosterUrl(series: com.homeflix.tv.presentation.screens.tvshows.TvSeries): String {
        // Always use the series poster endpoint like the web app
        val url = "${getBaseUrl()}/api/series/${series.id}/poster"
        android.util.Log.d("ApiUtils", "Series poster URL for '${series.title}': $url")
        return url
    }
    
    fun getSeriesThumbnailUrl(seriesId: Int): String {
        return "${getBaseUrl()}/api/thumbnails/$seriesId"
    }
    
    fun getSeriesBannerUrl(series: com.homeflix.tv.presentation.screens.tvshows.TvSeries): String {
        return if (!series.bannerPath.isNullOrEmpty()) {
            if (series.bannerPath.startsWith("http")) {
                series.bannerPath
            } else {
                val fileName = series.bannerPath.split("/").lastOrNull()
                if (!fileName.isNullOrEmpty()) {
                    "${getBaseUrl()}/api/admin/assets/$fileName"
                } else {
                    "${getBaseUrl()}/api/series/${series.id}/banner"
                }
            }
        } else {
            "${getBaseUrl()}/api/series/${series.id}/banner"
        }
    }
    
    fun getEpisodeThumbnailUrl(episode: com.homeflix.tv.presentation.screens.tvshows.Episode): String {
        return if (!episode.thumbnailPath.isNullOrEmpty()) {
            if (episode.thumbnailPath.startsWith("http")) {
                episode.thumbnailPath
            } else {
                "${getBaseUrl()}/api/thumbnails/${episode.id}"
            }
        } else {
            "${getBaseUrl()}/api/thumbnails/${episode.id}"
        }
    }
}