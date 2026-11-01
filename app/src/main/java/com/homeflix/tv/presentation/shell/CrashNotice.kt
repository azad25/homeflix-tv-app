package com.homeflix.tv.presentation.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeflix.tv.core.CrashReporter
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.presentation.ui.Tv

/** After a crash, the next launch shows what happened (take a photo, send it over). */
@Composable
fun CrashNotice() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashReporter.lastCrash(context)) }
    val r = report ?: return
    val ok = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { ok.requestFocus() } }
    val dismiss = { CrashReporter.clear(context); report = null }
    BackHandler(onBack = dismiss)
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(760.dp).clip(RoundedCornerShape(12.dp)).background(Tv.Surface).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("HomeFlix closed unexpectedly last time", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("A photo of this screen helps fix it.", color = Tv.TextSecondary, fontSize = 14.sp)
            Text(CrashReporter.summary(r), color = Color(0xFFDDDDDD), fontSize = 11.sp, lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace)
            PillButton("OK", dismiss, focusRequester = ok, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
