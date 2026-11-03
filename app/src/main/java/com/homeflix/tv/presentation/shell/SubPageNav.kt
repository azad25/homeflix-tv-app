package com.homeflix.tv.presentation.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeflix.tv.presentation.navigation.AppNavigator
import com.homeflix.tv.presentation.ui.NavScrim
import com.homeflix.tv.presentation.ui.Tv
import kotlinx.coroutines.flow.MutableStateFlow

/** Cross-screen requests for the browse shell (tab to open on return, bell badge). */
object ShellRequests {
    val tab = MutableStateFlow<ShellTab?>(null)
    val unread = MutableStateFlow(0)
}

/**
 * The same top bar as the browse screens, for pages outside the shell
 * (Details, Series, Notifications, Settings, About), so the menu is always one
 * ▲ away. Tabs open on OK (not on focus, which would leave the page while just
 * passing over them).
 *
 * @param visible focusable only while the page is at its top, so ▲ inside the
 *   page moves between sections before it reaches the bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SubPageTopBar(navigator: AppNavigator, visible: Boolean = true) {
    val unread by ShellRequests.unread.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val requesters = remember { ShellTab.entries.associateWith { FocusRequester() } }
    Box(
        Modifier
            .fillMaxWidth()
            .height(Tv.NavHeight + 24.dp)
            .background(NavScrim)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(Tv.NavHeight)
                .padding(horizontal = Tv.Keyline)
                .focusProperties { enter = { requesters.getValue(ShellTab.HOME) } }
                .focusGroup()
        ) {
            com.homeflix.tv.presentation.ui.BrandWordmark()
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                ShellTab.entries.forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = false,
                        requester = requesters.getValue(tab),
                        canFocus = visible,
                        down = null,
                        onFocused = {},
                        onClick = { navigator.goTab(tab) }
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            NavBell(unread = unread, canFocus = visible, onClick = { navigator.go(com.homeflix.tv.presentation.navigation.Screen.Notifications.route) })
            Spacer(Modifier.width(10.dp))
            NavIconButton(Icons.Filled.Person, "Profile", canFocus = visible, onClick = { menuOpen = true })
        }
    }
    if (menuOpen) {
        ProfileMenu(
            onDismiss = { menuOpen = false },
            onNotifications = { menuOpen = false; navigator.go(com.homeflix.tv.presentation.navigation.Screen.Notifications.route) },
            onSettings = { menuOpen = false; navigator.go(com.homeflix.tv.presentation.navigation.Screen.Settings.route) },
            onAbout = { menuOpen = false; navigator.go(com.homeflix.tv.presentation.navigation.Screen.About.route) },
            onRefresh = { menuOpen = false; navigator.goTab(ShellTab.HOME) },
            onExit = { menuOpen = false; navigator.goTab(ShellTab.HOME) }
        )
    }
}
