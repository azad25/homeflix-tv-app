package com.homeflix.tv.data.repository

import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.data.remote.dto.toDomain
import com.homeflix.tv.domain.model.StreamInfo
import com.homeflix.tv.domain.model.SubtitleTrack
import com.homeflix.tv.domain.model.AudioTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingRepository @Inject constructor(
    private val apiService: HomeFlixApiService
) {
    
    fun getStreamUrl(mediaId: String): Flow<Result<StreamInfo>> = flow {
        try {
            val response = apiService.getStreamUrl(mediaId)
            if (response.isSuccessful) {
                val streamInfo = response.body()?.toDomain()
                if (streamInfo != null) {
                    emit(Result.success(streamInfo))
                } else {
                    emit(Result.failure(Exception("Stream info not found")))
                }
            } else {
                emit(Result.failure(Exception("Failed to get stream URL: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    
    fun getPreviewClip(mediaId: String): Flow<Result<StreamInfo>> = flow {
        try {
            val response = apiService.getPreviewClip(mediaId)
            if (response.isSuccessful) {
                val streamInfo = response.body()?.toDomain()
                if (streamInfo != null) {
                    emit(Result.success(streamInfo))
                } else {
                    emit(Result.failure(Exception("Preview clip not found")))
                }
            } else {
                emit(Result.failure(Exception("Failed to get preview clip: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    
    fun getSubtitleTracks(mediaId: String): Flow<Result<List<SubtitleTrack>>> = flow {
        try {
            val response = apiService.getSubtitleTracks(mediaId)
            if (response.isSuccessful) {
                val subtitles = response.body()?.map { it.toDomain() } ?: emptyList()
                emit(Result.success(subtitles))
            } else {
                emit(Result.failure(Exception("Failed to get subtitle tracks: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    
    fun getAudioTracks(mediaId: String): Flow<Result<List<AudioTrack>>> = flow {
        try {
            val response = apiService.getAudioTracks(mediaId)
            if (response.isSuccessful) {
                val audioTracks = response.body()?.map { it.toDomain() } ?: emptyList()
                emit(Result.success(audioTracks))
            } else {
                emit(Result.failure(Exception("Failed to get audio tracks: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    
    fun getSubtitleFile(mediaId: String, trackId: String): Flow<Result<String>> = flow {
        try {
            val response = apiService.getSubtitleFile(mediaId, trackId)
            if (response.isSuccessful) {
                val subtitleContent = response.body()
                if (subtitleContent != null) {
                    emit(Result.success(subtitleContent))
                } else {
                    emit(Result.failure(Exception("Subtitle file not found")))
                }
            } else {
                emit(Result.failure(Exception("Failed to get subtitle file: ${response.message()}")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }
    
    suspend fun trackView(mediaId: String): Result<Unit> {
        return try {
            val response = apiService.trackView(mediaId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to track view: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}