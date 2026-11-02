package com.homeflix.tv.launcher

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.homeflix.tv.R
import com.homeflix.tv.core.ApplicationScope
import com.homeflix.tv.presentation.ui.Feed
import com.homeflix.tv.presentation.ui.TileKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts HomeFlix on the TV's home screen, like Netflix and Prime Video:
 *  - a "HomeFlix" row (Android TV preview channel; on Google TV it shows on the
 *    home/Apps tab) with what you're watching, then new and recommended titles;
 *  - your in-progress titles in the system "Continue watching" row (Watch Next).
 * Every card opens the title (or resumes playback) in the app.
 */
@Singleton
class HomeScreenPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val prefs by lazy { context.getSharedPreferences("home_screen", Context.MODE_PRIVATE) }
    private var pending: Job? = null

    /** Publish soon, off the UI's critical path (coalesces rapid feed refreshes). */
    fun publishLater(feed: Feed) {
        if (!supported) return
        pending?.cancel()
        pending = scope.launch {
            delay(2_500)
            publish(feed)
        }
    }

    suspend fun publish(feed: Feed) {
        if (!supported) return
        mutex.withLock {
            runCatching { publishCards(HomeCards.pick(feed)) }
                .onFailure { Log.w(TAG, "home screen update failed", it) }
        }
    }

    private suspend fun publishCards(cards: List<HomeCard>) {
        if (cards.isEmpty()) return
        val art = HomeArt(context)
        val withArt = cards.mapNotNull { card -> runCatching { art.ensure(card) }.getOrNull()?.let { card to it } }
        if (withArt.isEmpty()) return // server unreachable: keep what's on screen
        val channelId = channel() ?: return
        syncPrograms(channelId, withArt)
        syncWatchNext(withArt.filter { it.first.isContinue })
        art.prune(withArt.map { art.fileName(it.first) }.toSet())
    }

    // ------------------------------------------------------------------ channel

    private fun channel(): Long? {
        val resolver = context.contentResolver
        val saved = prefs.getLong(KEY_CHANNEL, -1L)
        if (saved > 0) {
            val exists = runCatching {
                resolver.query(TvContractCompat.buildChannelUri(saved), null, null, null, null)?.use { it.moveToFirst() }
            }.getOrNull() == true
            if (exists) return saved
        }
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName("HomeFlix")
            .setAppLinkIntentUri(appLink(HomeLink.Home))
            .build()
        val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues()) ?: return null
        val id = ContentUris.parseId(uri)
        prefs.edit().putLong(KEY_CHANNEL, id).remove(KEY_PROGRAMS).apply()
        logo()?.let { runCatching { ChannelLogoUtils.storeChannelLogo(context, id, it) } }
        // The app's first channel is shown without asking the user.
        runCatching { TvContractCompat.requestChannelBrowsable(context, id) }
        return id
    }

    private fun logo(): Bitmap? = runCatching {
        val d = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
        val size = 160
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(it))
        }
    }.getOrNull()

    // ----------------------------------------------------------------- programs

    @SuppressLint("RestrictedApi")
    private fun syncPrograms(channelId: Long, cards: List<Pair<HomeCard, Uri>>) {
        val resolver = context.contentResolver
        // internal id -> program id, for programs we published earlier.
        val existing = HashMap<String, Long>()
        runCatching {
            resolver.query(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val p = PreviewProgram.fromCursor(c)
                    p.internalProviderId?.let { existing[it] = p.id }
                }
            }
        }
        val keep = HashSet<Long>()
        cards.forEachIndexed { i, (card, art) ->
            val values = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(programType(card))
                .setTitle(card.title)
                .setDescription(card.description)
                .setPosterArtUri(art)
                .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
                .setIntentUri(appLink(card.link))
                .setInternalProviderId(card.id)
                .setWeight(cards.size - i) // keeps our order: higher weight first
                .build()
                .toContentValues()
            val pid = existing[card.id]
            if (pid != null && resolver.update(TvContractCompat.buildPreviewProgramUri(pid), values, null, null) > 0) {
                keep += pid
            } else {
                resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, values)?.let { keep += ContentUris.parseId(it) }
            }
        }
        existing.values.filter { it !in keep }.forEach {
            runCatching { resolver.delete(TvContractCompat.buildPreviewProgramUri(it), null, null) }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun syncWatchNext(cards: List<Pair<HomeCard, Uri>>) {
        val resolver = context.contentResolver
        data class Row(val id: Long, val browsable: Boolean, val position: Int)
        val existing = HashMap<String, Row>()
        runCatching {
            resolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val p = WatchNextProgram.fromCursor(c)
                    p.internalProviderId?.let { existing[it] = Row(p.id, p.isBrowsable, p.lastPlaybackPositionMillis) }
                }
            }
        }
        val now = System.currentTimeMillis()
        val keep = HashSet<Long>()
        cards.forEachIndexed { i, (card, art) ->
            val position = card.resumeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val old = existing[card.id]
            // The user removed it from "Continue watching": leave it out until they watch more.
            if (old != null && !old.browsable && kotlin.math.abs(old.position - position) < 60_000) { keep += old.id; return@forEachIndexed }
            val values = WatchNextProgram.Builder()
                .setType(programType(card))
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(now - i * 60_000L) // newest first
                .setTitle(card.title)
                .setDescription(card.description)
                .setPosterArtUri(art)
                .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
                .setLastPlaybackPositionMillis(position)
                .setDurationMillis(card.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                .setIntentUri(appLink(card.link))
                .setInternalProviderId(card.id)
                .build()
                .toContentValues()
            if (old != null && old.browsable && resolver.update(TvContractCompat.buildWatchNextProgramUri(old.id), values, null, null) > 0) {
                keep += old.id
            } else {
                if (old != null) runCatching { resolver.delete(TvContractCompat.buildWatchNextProgramUri(old.id), null, null) }
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)?.let { keep += ContentUris.parseId(it) }
            }
        }
        // Finished or replaced titles leave the system row.
        existing.values.filter { it.id !in keep }.forEach {
            runCatching { resolver.delete(TvContractCompat.buildWatchNextProgramUri(it.id), null, null) }
        }
    }

    private fun programType(card: HomeCard): Int = when (card.kind) {
        TileKind.MOVIE -> TvContractCompat.PreviewProgramColumns.TYPE_MOVIE
        TileKind.SERIES -> TvContractCompat.PreviewProgramColumns.TYPE_TV_SERIES
        TileKind.EPISODE -> TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE
    }

    private fun appLink(link: HomeLink): Uri = Uri.parse(
        Intent(Intent.ACTION_VIEW, link.toUri()).setPackage(context.packageName).toUri(Intent.URI_INTENT_SCHEME)
    )

    companion object {
        private const val TAG = "HomeScreen"
        private const val KEY_CHANNEL = "channel_id"
        private const val KEY_PROGRAMS = "programs"

        /** Home-screen channels exist from Android 8 (every Android TV / Google TV since 2017). */
        val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}
