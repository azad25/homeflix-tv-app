package com.homeflix.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.homeflix.tv.presentation.screens.search.SearchContent
import com.homeflix.tv.presentation.screens.search.SearchState
import com.homeflix.tv.presentation.shell.ShellController
import com.homeflix.tv.presentation.shell.ShellTab
import com.homeflix.tv.presentation.ui.Tile
import com.homeflix.tv.presentation.ui.TileActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class SearchTypingTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var query by mutableStateOf("")

    private fun press(keys: List<Key>) = keys.forEach {
        rule.onRoot().performKeyInput { pressKey(it) }
        rule.waitForIdle()
    }

    @Test
    fun hardwareKeyboard_andRemoteDigits_typeIntoQuery_dpadStillWorks() {
        val noActions = object : TileActions {
            override fun open(tile: Tile) {}
            override fun play(tile: Tile) {}
        }
        rule.setContent {
            SearchContent(
                state = SearchState(query = query),
                shell = ShellController(ShellTab.SEARCH),
                actions = noActions,
                onType = { query += it },
                onBackspace = { query = query.dropLast(1) },
                onClear = { query = "" },
                onGenre = {}
            )
        }
        rule.waitForIdle() // focus starts on the on-screen "a" key

        // Physical keyboard while an on-screen key has focus.
        press(listOf(Key.I, Key.N, Key.C, Key.E, Key.P, Key.T, Key.I, Key.O, Key.N))
        assertEquals("inception", query)
        rule.onNodeWithText("inception").assertExists()

        // Shift + letter, space and remote/keyboard digits.
        rule.onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.A) } }
        press(listOf(Key.Spacebar, Key.Two))
        assertEquals("inceptiona 2", query)

        // Backspace deletes; Ctrl+Backspace clears.
        press(listOf(Key.Backspace))
        assertEquals("inceptiona ", query)
        rule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Backspace) } }
        assertEquals("", query)

        // The on-screen keyboard still works with the D-pad: → then OK types "b".
        press(listOf(Key.DirectionRight, Key.DirectionCenter))
        assertEquals("b", query)
    }
}
