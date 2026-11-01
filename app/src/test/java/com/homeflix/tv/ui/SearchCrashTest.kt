package com.homeflix.tv.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.data.remote.api.HomeFlixApiService
import com.homeflix.tv.data.repository.MediaRepository
import com.homeflix.tv.presentation.screens.search.SearchContent
import com.homeflix.tv.presentation.screens.search.SearchViewModel
import com.homeflix.tv.presentation.shell.ShellController
import com.homeflix.tv.presentation.shell.ShellTab
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileActions
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The real Search ViewModel + UI against a server that returns duplicates and
 * broken responses. Before the fix this crashed the app (duplicate lazy keys /
 * an exception escaping the ViewModel's coroutine).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class SearchCrashTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var server: MockWebServer

    private fun movie(id: Int, title: String) =
        """{"id":$id,"uuid":"u$id","title":"$title","type":"movie","file_path":"/m/$id.mkv","file_size":1,"duration":5400,"genre_names":["Action"]}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                return when {
                    // Same title listed twice (seen from the real server).
                    p.startsWith("/api/media/genre/") -> MockResponse().setBody("[${movie(1, "Heat")},${movie(1, "Heat")},${movie(2, "Ronin")}]")
                    p.startsWith("/api/media/movies") -> MockResponse().setBody("[${movie(3, "Inception")},${movie(3, "Inception")}]")
                    p.startsWith("/api/media/search") -> MockResponse().setBody("{not json")       // broken
                    p.startsWith("/api/recommendations/trending") -> MockResponse().setResponseCode(500)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() { server.shutdown() }

    private fun idle(ms: Long = 1500) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            rule.waitForIdle()
            Thread.sleep(25)
        }
    }

    @Test
    fun duplicatesAndBrokenResponses_neverCrashSearch() {
        val api = Retrofit.Builder().baseUrl(server.url("/api/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(HomeFlixApiService::class.java)
        val vm = SearchViewModel(MediaRepository(api))
        val noActions = object : TileActions {
            override fun open(tile: Tile) {}
            override fun play(tile: Tile) {}
        }
        rule.setContent {
            val state by vm.state.collectAsState()
            SearchContent(state, ShellController(ShellTab.SEARCH), noActions,
                onType = vm::type, onBackspace = vm::backspace, onClear = vm::clear, onGenre = vm::selectGenre)
        }
        idle()
        // Recommendations load even though trending failed; the duplicate movie shows once.
        assertEquals(1, vm.state.value.results.count { it.id == 3 })

        // Genre results contain a duplicate title → shown once, no crash.
        rule.runOnUiThread { vm.selectGenre("Action") }
        idle()
        assertEquals(listOf("Heat", "Ronin"), vm.state.value.results.map { it.title }.take(2))
        assertEquals(2, rule.onAllNodesWithText("Action").fetchSemanticsNodes().size) // genre item + results title
        rule.onNodeWithText("Ronin").assertDoesNotExist() // grid shows posters; titles only on focus

        // Typing while the search endpoint returns garbage → "No results", no crash.
        rule.runOnUiThread { vm.type("x") }
        idle()
        assertFalse(vm.state.value.loading)
        assertEquals("No results for “x”", vm.state.value.title)
    }
}
