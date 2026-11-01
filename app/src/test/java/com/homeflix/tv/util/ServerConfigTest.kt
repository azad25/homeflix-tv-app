package com.homeflix.tv.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ServerConfigTest {
    @Test
    fun normalizesWhatPeopleType() {
        assertEquals("http://192.168.0.109:8252/api", ServerConfig.normalize("192.168.0.109"))
        assertEquals("http://192.168.1.50:8252/api", ServerConfig.normalize("192.168.1.50:8252"))
        assertEquals("http://192.168.1.50:9000/api", ServerConfig.normalize("http://192.168.1.50:9000/"))
        assertEquals("http://homeflix.local:8252/api", ServerConfig.normalize(" homeflix.local:8252/api "))
        assertEquals("https://flix.example.com/api", ServerConfig.normalize("https://flix.example.com"))
        assertNull(ServerConfig.normalize(""))
        assertNull(ServerConfig.normalize("http://"))
    }
}
