package com.homeflix.tv.util

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class HttpTest {
    @Test fun clientsShareOneConnectionPool() {
        val api = Http.shared.newBuilder().build()
        val images = Http.images()
        assertSame(Http.shared.connectionPool, api.connectionPool)
        assertSame(Http.shared.connectionPool, images.connectionPool)
    }

    @Test fun artworkHasItsOwnRequestQueue() {
        // Posters must not use up the per-host slots that API calls need.
        assertNotSame(Http.shared.dispatcher, Http.images().dispatcher)
    }
}
