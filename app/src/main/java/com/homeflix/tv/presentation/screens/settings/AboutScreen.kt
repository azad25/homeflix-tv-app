package com.homeflix.tv.presentation.screens.settings

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeflix.tv.BuildConfig
import com.homeflix.tv.presentation.ui.PillButton
import com.homeflix.tv.presentation.ui.Tv

/** Who built HomeFlix — shown in About and Settings. */
object Credits {
    const val DEVELOPER = "Ferdous Azad"
    const val ROLE = "Developer of HomeFlix"
    const val EMAIL = "ferdousazad12@gmail.com"
    const val GITHUB = "github.com/azad25"
    const val DONATE = "buymeacoffee.com/ferdousazad"
    val REPOS = listOf("github.com/azad25/homeflix-wifi", "github.com/azad25/homeflix-tv-app")
}

/** About HomeFlix: developer credit, contact, open-source links and version. */
@Composable
fun AboutScreen() {
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF3A0A0C), Tv.Background), radius = 1400f))
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 96.dp).width(560.dp)) {
            Text("HOMEFLIX", color = Tv.Red, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("Your movies and shows, on your own server.", color = Tv.TextSecondary, fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(30.dp))

            Text("DEVELOPED BY", color = Tv.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Tv.Red)
                ) { Text("FA", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(Credits.DEVELOPER, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(Credits.ROLE, color = Tv.TextSecondary, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoLine(Icons.Filled.Email, "Contact", Credits.EMAIL)
                InfoLine(Icons.Filled.Person, "GitHub", Credits.GITHUB)
                InfoLine(Icons.Filled.Code, "Open source", Credits.REPOS.joinToString("   ·   "))
            }
            // Donation: a TV can't open a browser, so show the address big enough to type on a phone.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 18.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFDD00)).padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Filled.Coffee, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Enjoying HomeFlix? Buy me a coffee", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(Credits.DONATE, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("HomeFlix TV ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Tv.TextMuted, fontSize = 13.sp)
            Text("© ${Credits.DEVELOPER}. Thanks for using and contributing to HomeFlix.", color = Tv.TextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp))
            PillButton("Back", { back?.onBackPressed() }, icon = Icons.AutoMirrored.Filled.ArrowBack, focusRequester = focus,
                modifier = Modifier.padding(top = 24.dp))
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x1FFFFFFF))
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, color = Tv.TextMuted, fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
