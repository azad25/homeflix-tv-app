package com.homeflix.tv.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.data.repository.StreamingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Regression: v5 reads repository flows with .first(). The repositories wrapped
 * emit() in catch (e: Exception), which swallowed the AbortFlowException that
 * first() uses to stop the flow and then emitted again → "Flow exception
 * transparency is violated" on every load (seen on the TV as "Can't load from …").
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoryFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: MediaRepository
    private lateinit var streaming: StreamingRepository

    private val movie = """{"id":42,"uuid":"u42","title":"Inception","type":"movie","file_path":"/m/i.mkv",
        "file_size":1,"duration":8880,"year":2010,"genre_names":["Sci-Fi"]}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/api/media/movies") -> MockResponse().setBody("[$movie]")
                    path.startsWith("/api/media/search") -> MockResponse().setBody("[$movie]")
                    path.startsWith("/api/media/genre/") -> MockResponse().setBody("[$movie]")
                    path.startsWith("/api/media/42/subtitles") -> MockResponse().setBody("[]")
                    path.startsWith("/api/media/42") -> MockResponse().setBody(movie)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/api/"))
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(HomeFlixApiService::class.java)
        repo = MediaRepository(api)
        streaming = StreamingRepository(api)
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun firstOnRepositoryFlows_returnsTheValue() = runBlocking {
        val movies = repo.getMovies(limit = 120).first()
        assertTrue("getMovies failed: ${movies.exceptionOrNull()}", movies.isSuccess)
        assertEquals("Inception", movies.getOrThrow().single().title)

        val one = repo.getMediaById("42").first()
        assertTrue("getMediaById failed: ${one.exceptionOrNull()}", one.isSuccess)
        assertEquals(42, one.getOrThrow().id)

        assertTrue(repo.searchMedia("inc", 40).first().isSuccess)
        assertTrue(repo.getMediaByGenre("Sci-Fi", 20, 0).first().isSuccess)
        assertTrue(streaming.getSubtitleTracks("42").first().isSuccess)
    }
}
