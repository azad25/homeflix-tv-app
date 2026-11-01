package com.homeflix.tv.presentation.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.homeflix.tv.BuildConfig
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.presentation.ui.Tv
import com.homeflix.tv.update.AppUpdater
import com.homeflix.tv.update.UpdateState

/**
 * "HomeFlix 5.2 is ready" — shown over the browse screens (never over the
 * player) once an update has been downloaded and verified. "Later" hides it
 * until the next launch.
 */
@Composable
fun UpdatePrompt(updater: AppUpdater) {
    val state by updater.state.collectAsState()
    var dismissedVersion by rememberSaveable { mutableLongStateOf(-1L) }
    var needsPermission by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Back from the "Install unknown apps" screen: if allowed now, carry on.
        if (needsPermission && updater.canInstall()) { needsPermission = false; updater.install() }
    }

    val info = when (val s = state) {
        is UpdateState.Ready -> s.info
        is UpdateState.Installing -> s.info
        else -> null
    } ?: return
    if (info.versionCode == dismissedVersion) return
    val installing = state is UpdateState.Installing

    val focus = remember { FocusRequester() }
    LaunchedEffect(info.versionCode) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    BackHandler { dismissedVersion = info.versionCode }

    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp).clip(RoundedCornerShape(12.dp)).background(Tv.Surface).padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Update available", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "HomeFlix ${info.versionName ?: info.versionCode.toString()} is ready to install " +
                    "(you have ${BuildConfig.VERSION_NAME}). It takes a few seconds and keeps your settings and progress.",
                color = Tv.TextSecondary, fontSize = 17.sp, lineHeight = 24.sp
            )
            info.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Tv.TextPrimary, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            if (needsPermission) {
                Text(
                    "If Android asks, allow HomeFlix to install apps (Settings → Apps → Security & restrictions " +
                        "→ Unknown sources → HomeFlix), then press Update now again.",
                    color = Color(0xFFE5B109), fontSize = 15.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 10.dp)) {
                PillButton(
                    text = if (installing) "Installing…" else "Update now",
                    onClick = {
                        if (installing) return@PillButton
                        needsPermission = !updater.canInstall()
                        updater.install()
                    },
                    icon = Icons.Filled.SystemUpdate,
                    focusRequester = focus
                )
                PillButton("Later", { dismissedVersion = info.versionCode })
            }
        }
    }
}
