package com.homeflix.tv.cast

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Persistent cast identity + preferences for this TV. */
@Singleton
class CastSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("cast_receiver", Context.MODE_PRIVATE)

    /** Stable per install; reconnecting with it replaces the previous server-side connection. */
    val deviceId: String = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_ID, it).apply()
    }

    private val _name = MutableStateFlow(prefs.getString(KEY_NAME, null) ?: defaultName())
    val name: StateFlow<String> = _name.asStateFlow()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setName(value: String) {
        val clean = value.trim().take(80).ifBlank { defaultName() }
        prefs.edit().putString(KEY_NAME, clean).apply()
        _name.value = clean
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    private fun defaultName(): String {
        val device = runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
        return (device?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "HomeFlix TV").take(80)
    }

    private companion object {
        const val KEY_ID = "device_id"
        const val KEY_NAME = "name"
        const val KEY_ENABLED = "enabled"
    }
}
