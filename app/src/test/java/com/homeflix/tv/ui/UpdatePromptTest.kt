package com.homeflix.tv.ui

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.presentation.shell.UpdatePrompt
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.update.AppUpdater
import com.homeflix.tv.update.UpdateInfo
import com.homeflix.tv.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration

/** The update popup must take the D-pad focus and keep it until the user answers. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class UpdatePromptTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun pump(ms: Long = 300) {
        var t = 0L
        while (t < ms) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.mainClock.advanceTimeBy(50)
            t += 50
        }
        rule.waitForIdle()
    }

    private fun focused(): String {
        val n = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull() ?: return "<none>"
        return if (n.config.contains(SemanticsProperties.Text)) n.config[SemanticsProperties.Text].joinToString()
        else n.children.firstNotNullOfOrNull { c ->
            if (c.config.contains(SemanticsProperties.Text)) c.config[SemanticsProperties.Text].joinToString() else null
        } ?: "node#${n.id}"
    }

    private fun press(key: Key) {
        rule.onNode(isFocused()).performKeyInput { pressKey(key) }
        pump()
    }

    @Test
    fun popupTakesFocus_andKeepsIt_untilLater() {
        val updater = AppUpdater(ApplicationProvider.getApplicationContext(), CoroutineScope(SupervisorJob() + Dispatchers.IO))
        updater.setStateForTest(UpdateState.Ready(UpdateInfo(versionCode = 99, versionName = "9.9"), File("x.apk")))
        val behind = FocusRequester()
        rule.setContent {
            Column {
                PillButton("Behind", {}, focusRequester = behind)
                PillButton("Behind 2", {})
                UpdatePrompt(updater)
            }
        }
        pump(600)
        assertTrue("popup not shown", rule.onAllNodesWithTag("update_prompt").fetchSemanticsNodes().isNotEmpty())
        assertEquals("Update now", focused())
        press(Key.DirectionRight)
        assertEquals("Later", focused())
        // Nothing behind the popup can be reached.
        for (k in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionLeft, Key.DirectionRight, Key.DirectionRight)) {
            press(k)
            val f = focused()
            assertTrue("focus escaped the popup: $f", f == "Update now" || f == "Later")
        }
        if (focused() != "Later") press(Key.DirectionRight)
        press(Key.Enter)
        pump(600)
        assertTrue("popup still shown", rule.onAllNodesWithTag("update_prompt").fetchSemanticsNodes().isEmpty())
    }
}
