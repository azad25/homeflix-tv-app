package com.homeflix.tv.presentation.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LifecycleEventEffect
import com.homeflix.tv.BuildConfig
import com.homeflix.tv.cast.CastConnection
import com.homeflix.tv.cast.CastReceiverService
import com.homeflix.tv.cast.CastSettings
import com.homeflix.tv.presentation.ui.Tv
import com.homeflix.tv.util.ServerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val cast: CastSettings,
    val connection: CastConnection,
    val updater: com.homeflix.tv.update.AppUpdater
) : ViewModel() {
    /** "OK — reached http://…" / "Couldn't reach …" after saving the address. */
    val serverCheck = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun saveServer(input: String) {
        val base = ServerConfig.setLocal(context, input)
        if (base == null) { serverCheck.value = "That doesn't look like a server address"; return }
        serverCheck.value = "Checking ${base.removeSuffix("/api")}…"
        viewModelScope.launch(Dispatchers.IO) { checkServer(base) }
    }

    private fun checkServer(base: String) {
        val ok = runCatching {
            okhttp3.OkHttpClient.Builder().connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS).build()
                .newCall(okhttp3.Request.Builder().url("$base/genres").build()).execute().use { it.code }
        }
        serverCheck.value = ok.fold(
            onSuccess = { code -> if (code in 200..499) "Connected to ${base.removeSuffix("/api")}" else "Server answered HTTP $code" },
            onFailure = { e -> "Couldn't reach ${base.removeSuffix("/api")} (${e.javaClass.simpleName})" }
        )
    }

    fun setCastEnabled(on: Boolean) {
        cast.setEnabled(on)
        if (on) CastReceiverService.start(context) else CastReceiverService.stop(context)
    }
}

/** Settings → casting from the HomeFlix web app, plus app info. */
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val name by vm.cast.name.collectAsState()
    val enabled by vm.cast.enabled.collectAsState()
    val connState by vm.connection.state.collectAsState()
    var overlayGranted by remember { mutableStateOf(canDrawOverlays(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { overlayGranted = canDrawOverlays(context) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Tv.Background)) {
        Column(Modifier.padding(start = 72.dp, top = 48.dp).width(620.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Settings", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Cast to this TV", color = Tv.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp))

            // Status line
            val (dot, status) = when {
                !enabled -> Tv.TextMuted to "Casting is turned off"
                connState == CastConnection.State.CONNECTED -> Color(0xFF46D369) to "Ready to cast"
                connState == CastConnection.State.CONNECTING -> Color(0xFFE5B109) to "Connecting to the server…"
                else -> Tv.Red to "Not connected to the server"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                Text(status, color = Color.White, fontSize = 17.sp, modifier = Modifier.padding(start = 10.dp))
            }

            SettingRow(
                title = "Allow casting to this TV",
                value = if (enabled) "On" else "Off",
                onClick = { vm.setCastEnabled(!enabled) },
                modifier = Modifier.focusRequester(first)
            )
            NameField(name = name, onSave = vm.cast::setName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                SettingRow(
                    title = "Allow HomeFlix to open from casting",
                    value = if (overlayGranted) "Allowed" else "Not allowed",
                    subtitle = "Lets a cast start playback while another app is open (“Display over other apps”).",
                    onClick = { openOverlaySettings(context) }
                )
            }

            ServerSection(vm)
            UpdatesSection(vm.updater)

            Text("About", color = Tv.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp))
            Text("Developed by ${Credits.DEVELOPER} · ${Credits.ROLE}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Contact: ${Credits.EMAIL} · ${Credits.GITHUB}", color = Tv.TextSecondary, fontSize = 14.sp)
            Text("Support HomeFlix: ${Credits.DONATE}", color = Color(0xFFFFDD00), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Server: ${ServerConfig.activeApiBase}", color = Tv.TextMuted, fontSize = 15.sp)
            Text("HomeFlix TV ${BuildConfig.VERSION_NAME}", color = Tv.TextMuted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ServerSection(vm: SettingsViewModel) {
    val check by vm.serverCheck.collectAsState()
    var text by remember { mutableStateOf(ServerConfig.LOCAL.removeSuffix("/api")) }
    var focused by remember { mutableStateOf(false) }
    Text("Server", color = Tv.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp))
    Column {
        Text("Server address (e.g. 192.168.0.109:8252) — press OK on the remote keyboard to save",
            color = Tv.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(120) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            keyboardActions = KeyboardActions(onDone = { vm.saveServer(text) }),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onFocusChanged {
                    if (focused && !it.isFocused && text.isNotBlank()) vm.saveServer(text)
                    focused = it.isFocused
                }
                .clip(RoundedCornerShape(8.dp))
                .background(Tv.Surface)
                .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        )
        check?.let { Text(it, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
    if (ServerConfig.LOCAL != ServerConfig.DEFAULT_LOCAL) {
        SettingRow(title = "Reset to default server", value = ServerConfig.DEFAULT_LOCAL.removeSuffix("/api"),
            onClick = { text = ServerConfig.DEFAULT_LOCAL.removeSuffix("/api"); vm.saveServer(text) })
    }
}

@Composable
private fun UpdatesSection(updater: com.homeflix.tv.update.AppUpdater) {
    val state by updater.state.collectAsState()
    var canInstall by remember { mutableStateOf(updater.canInstall()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canInstall = updater.canInstall() }
    Text("App updates", color = Tv.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp))
    val status = when (val s = state) {
        com.homeflix.tv.update.UpdateState.Idle -> "Updates install from your HomeFlix server"
        com.homeflix.tv.update.UpdateState.Checking -> "Checking for updates…"
        is com.homeflix.tv.update.UpdateState.UpToDate -> "HomeFlix is up to date"
        is com.homeflix.tv.update.UpdateState.Downloading -> "Downloading ${s.info.versionName ?: ""}… ${(s.progress * 100).toInt()}%"
        is com.homeflix.tv.update.UpdateState.Ready -> "Version ${s.info.versionName ?: s.info.versionCode} is ready to install"
        is com.homeflix.tv.update.UpdateState.Installing -> "Installing…"
        is com.homeflix.tv.update.UpdateState.Failed -> s.message
    }
    Text(status, color = Color.White, fontSize = 17.sp)
    val ready = state as? com.homeflix.tv.update.UpdateState.Ready
    if (ready != null) {
        SettingRow(
            title = "Install update",
            value = ready.info.versionName ?: ready.info.versionCode.toString(),
            onClick = { if (updater.canInstall()) updater.install() else updater.openInstallPermissionSettings() }
        )
    } else {
        SettingRow(title = "Check for updates", value = "", onClick = updater::checkNow)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SettingRow(
            title = "Allow HomeFlix to install updates",
            value = if (canInstall) "Allowed" else "Not allowed",
            subtitle = "Needed once. If this doesn't open it: Settings → Apps → Security & restrictions → Unknown sources → HomeFlix.",
            onClick = updater::openInstallPermissionSettings
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White else Tv.Surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (focused) Color.Black else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, color = if (focused) Color.DarkGray else Tv.TextMuted, fontSize = 13.sp) }
        }
        Text(value, color = if (focused) Color.Black else Tv.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NameField(name: String, onSave: (String) -> Unit) {
    var text by remember(name) { mutableStateOf(name) }
    var focused by remember { mutableStateOf(false) }
    Column {
        Text("Cast name", color = Tv.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(80) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) }),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .onFocusChanged {
                    if (focused && !it.isFocused && text != name) onSave(text)
                    focused = it.isFocused
                }
                .clip(RoundedCornerShape(8.dp))
                .background(Tv.Surface)
                .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }
}

private fun canDrawOverlays(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

private fun openOverlaySettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Some Android TV builds hide this screen; fall back to the app's settings page.
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
