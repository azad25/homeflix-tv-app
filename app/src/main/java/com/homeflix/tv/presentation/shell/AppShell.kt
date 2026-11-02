package com.homeflix.tv.presentation.shell

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.homeflix.tv.core.PreviewPlayer
import com.homeflix.tv.presentation.ui.LocalPreviewPlayer
import com.homeflix.tv.presentation.ui.NavScrim
import com.homeflix.tv.presentation.ui.TileActions
import com.homeflix.tv.presentation.ui.Tv
import kotlinx.coroutines.delay

enum class ShellTab(val label: String) { SEARCH("Search"), HOME("Home"), SHOWS("Shows"), MOVIES("Movies"), MINE("My HomeFlix") }

/** Shared state between the top nav and the tab pages. */
@Stable
class ShellController(initialTab: ShellTab) {
    var selected by mutableStateOf(initialTab)
    var navFocused by mutableStateOf(false)
    /** The visible page reports whether its first section is focused. */
    var pageAtTop by mutableStateOf(true)
    /** True until the visible page has put focus somewhere sensible (launch / back from a screen). */
    var restoreFocusPending by mutableStateOf(true)
    /** When focus enters the page from the nav (OK on a tab), start at the top instead of the saved tile. */
    var enterAtTop by mutableStateOf(false)
    /** Keeps the nav focusable while we move focus up into it from deep in a page. */
    internal var forceNav by mutableStateOf(false)
    internal var navFocusRequest by mutableIntStateOf(0)
    /** Where ▼ from the nav lands (the page's billboard / first item). */
    var pageEntry: FocusRequester? by mutableStateOf(null)

    val navVisible: Boolean get() = navFocused || pageAtTop || forceNav

    fun focusNav() { forceNav = true; navFocusRequest++ }
}

/**
 * Top-level browse experience: a Netflix-style top navigation over the
 * selected tab's page. Each tab keeps its own scroll/focus state.
 *
 * Back: content → top nav → Home tab → exit.
 */
@UnstableApi
@Composable
fun AppShell(
    previewPlayer: PreviewPlayer,
    actions: TileActions,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    unreadNotifications: Int = 0,
    updater: com.homeflix.tv.update.AppUpdater? = null,
    /** Opens full-screen Live TV; null hides the menu item (server has Live TV off). */
    onOpenLiveTv: (() -> Unit)? = null,
    page: @Composable (ShellTab, ShellController) -> Unit
) {
    var savedTab by rememberSaveable { mutableStateOf(ShellTab.HOME) }
    val shell = remember { ShellController(savedTab) }
    LaunchedEffect(shell.selected) { savedTab = shell.selected }
    val holder = rememberSaveableStateHolder()
    // A tab picked from a sub-page's top bar (Details, Series, Settings…).
    val requestedTab by ShellRequests.tab.collectAsState()
    LaunchedEffect(requestedTab) {
        val t = requestedTab ?: return@LaunchedEffect
        shell.selected = t
        shell.enterAtTop = true
        shell.restoreFocusPending = true
        ShellRequests.tab.value = null
    }
    LaunchedEffect(unreadNotifications) { ShellRequests.unread.value = unreadNotifications }
    val activity = LocalContext.current as? Activity
    var menuOpen by remember { mutableStateOf(false) }

    // Preview video follows this screen's lifecycle (Home button, player opening).
    com.homeflix.tv.presentation.ui.PreviewLifecycle(previewPlayer)

    BackHandler(enabled = !menuOpen) {
        when {
            !shell.navFocused -> shell.focusNav()
            shell.selected != ShellTab.HOME -> { shell.selected = ShellTab.HOME; shell.focusNav() }
            else -> activity?.finish()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalPreviewPlayer provides previewPlayer) {
        Box(Modifier.fillMaxSize().background(Tv.Background)) {
            Box(Modifier.fillMaxSize()) {
                holder.SaveableStateProvider(shell.selected.name) {
                    page(shell.selected, shell)
                }
            }
            TopNav(
                shell = shell,
                menuOpen = menuOpen,
                unread = unreadNotifications,
                onNotifications = onOpenNotifications,
                onToggleMenu = { menuOpen = !menuOpen },
                onLiveTv = onOpenLiveTv
            )
            if (!menuOpen && updater != null) UpdatePrompt(updater)
            CrashNotice()
            if (menuOpen) {
                ProfileMenu(
                    onDismiss = { menuOpen = false; shell.focusNav() },
                    onNotifications = { menuOpen = false; onOpenNotifications() },
                    onSettings = { menuOpen = false; onOpenSettings() },
                    onAbout = { menuOpen = false; onOpenAbout() },
                    onRefresh = { menuOpen = false; activity?.recreate() },
                    onExit = { activity?.finish() }
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TopNav(
    shell: ShellController,
    menuOpen: Boolean,
    unread: Int,
    onNotifications: () -> Unit,
    onToggleMenu: () -> Unit,
    onLiveTv: (() -> Unit)? = null
) {
    val visible = shell.navVisible || menuOpen
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "navAlpha")
    val requesters = remember { ShellTab.entries.associateWith { FocusRequester() } }
    var focusedTab by remember { mutableStateOf<ShellTab?>(null) }

    // Netflix switches tabs as focus rests on them (debounced so sweeping across is cheap).
    LaunchedEffect(focusedTab) {
        val t = focusedTab ?: return@LaunchedEffect
        delay(280)
        // Focus can land on a tab by itself while the page is still restoring
        // its own focus (e.g. right after Back from a movie page): that is not
        // the user resting on the tab, so don't switch pages for it.
        if (shell.restoreFocusPending || !shell.navFocused) return@LaunchedEffect
        if (t != shell.selected) shell.selected = t
    }
    LaunchedEffect(shell.navFocusRequest) {
        if (shell.navFocusRequest == 0) return@LaunchedEffect
        withFrameNanos { } // let canFocus flip to true first
        val ok = runCatching { requesters.getValue(shell.selected).requestFocus() }.isSuccess
        if (!ok) shell.forceNav = false
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(Tv.NavHeight + 24.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = (1f - alpha) * -40f
            }
            .background(NavScrim)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(Tv.NavHeight)
                .padding(horizontal = Tv.Keyline)
                .onFocusChanged {
                    shell.navFocused = it.hasFocus
                    if (it.hasFocus) shell.forceNav = false
                }
                // Entering the nav from the page always lands on the current tab.
                .focusProperties { enter = { requesters.getValue(shell.selected) } }
                .focusGroup()
        ) {
            Text("HOMEFLIX", color = Tv.Red, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                ShellTab.entries.forEach { tab ->
                    NavItem(
                        tab = tab,
                        selected = shell.selected == tab,
                        requester = requesters.getValue(tab),
                        canFocus = visible,
                        down = shell.pageEntry,
                        onFocused = { focusedTab = tab },
                        onClick = {
                            shell.selected = tab
                            // OK moves into the page like Netflix, starting at its top.
                            shell.enterAtTop = true
                            shell.restoreFocusPending = true
                        }
                    )
                }
                // Live TV opens full screen on OK (it isn't a browse page, so resting on it does nothing).
                if (onLiveTv != null) NavLiveTv(canFocus = visible, down = shell.pageEntry, onClick = onLiveTv)
            }
            Spacer(Modifier.weight(1f))
            // Notifications stay on the nav: bell with a red dot while anything is unread.
            NavBell(unread = unread, canFocus = visible, onClick = onNotifications)
            Spacer(Modifier.width(10.dp))
            NavIconButton(Icons.Filled.Person, "Profile", canFocus = visible, onClick = onToggleMenu)
        }
    }
}

@Composable
internal fun NavItem(
    tab: ShellTab,
    selected: Boolean,
    requester: FocusRequester,
    canFocus: Boolean,
    down: FocusRequester?,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color.White
        selected -> Color(0x40FFFFFF)
        else -> Color.Transparent
    }
    val fg = when {
        focused -> Color.Black
        selected -> Color.White
        else -> Color(0xCCFFFFFF)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties {
                this.canFocus = canFocus
                if (down != null) this.down = down
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        if (tab == ShellTab.SEARCH) {
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = fg, modifier = Modifier.size(18.dp))
        } else {
            Text(tab.label, color = fg, fontSize = 14.sp, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
internal fun NavLiveTv(canFocus: Boolean, down: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .focusProperties {
                this.canFocus = canFocus
                if (down != null) this.down = down
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Tv.Red))
        Text("  Live TV", color = if (focused) Color.Black else Color(0xCCFFFFFF), fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
internal fun NavBell(unread: Int, canFocus: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = "Notifications",
            tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(19.dp))
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Tv.Red)
            )
        }
    }
}

@Composable
internal fun NavIconButton(icon: ImageVector, description: String, canFocus: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .focusProperties { this.canFocus = canFocus }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(4.dp))
            .background(if (focused) Color.White else Color(0xFF3A6FD8))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Icon(icon, contentDescription = description, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun ProfileMenu(onDismiss: () -> Unit, onNotifications: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit, onRefresh: () -> Unit, onExit: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss, indication = null,
        interactionSource = remember { MutableInteractionSource() })) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = Tv.NavHeight, end = Tv.Keyline)
                .width(260.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Tv.Surface)
                .padding(vertical = 8.dp)
        ) {
            MenuItem(Icons.Filled.Notifications, "Notifications", onNotifications, Modifier.focusRequester(first))
            MenuItem(Icons.Filled.Settings, "Settings", onSettings)
            MenuItem(Icons.Filled.Info, "About HomeFlix", onAbout)
            MenuItem(Icons.Filled.Refresh, "Refresh", onRefresh)
            MenuItem(Icons.AutoMirrored.Filled.ExitToApp, "Exit HomeFlix", onExit)
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = if (focused) Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** Small circular status dot used by pages (e.g. unread notifications). */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
internal fun FadeIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) { content() }
}
