package com.homeflix.tv.launcher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.security.MessageDigest

/**
 * Draws the 16:9 cards shown on the TV home screen: the title's backdrop with
 * its logo (or name), a small label like "Continue Watching" and a red
 * progress bar — the look Netflix and Prime use there.
 *
 * Cards are saved in the app's files and handed to the launcher as
 * content:// URIs ([HomeArtProvider]), so they show even if the launcher
 * can't reach the HomeFlix server itself.
 */
class HomeArt(private val context: Context) {
    private val dir = File(context.filesDir, DIR).apply { mkdirs() }

    /** File name for [card]'s art; changes whenever what's drawn changes. */
    fun fileName(card: HomeCard): String {
        val bucket = card.progress?.let { (it * 50).toInt() } ?: -1 // redraw every 2%
        val sig = "${card.imageUrl}|${card.logoUrl}|${card.title}|${card.label}|$bucket|$VERSION"
        return card.id.replace(Regex("[^A-Za-z0-9_-]"), "_") + "-" + sha1(sig).take(10) + ".jpg"
    }

    fun uriFor(name: String): Uri = Uri.parse("content://${context.packageName}$AUTHORITY_SUFFIX/$name")

    /** Renders [card] unless it's already on disk. Returns its URI, or null if the image couldn't load. */
    suspend fun ensure(card: HomeCard): Uri? {
        val name = fileName(card)
        val file = File(dir, name)
        if (file.length() > 0) return uriFor(name)
        val backdrop = loader(card.imageUrl, W, H) ?: return null
        val logo = card.logoUrl?.let { loader(it, W / 2, H / 3) }
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), card, backdrop, logo)
        val tmp = File(dir, "$name.tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        tmp.renameTo(file)
        return uriFor(name)
    }

    /** Image source (Coil); tests swap it for a local decoder. */
    internal var loader: suspend (url: String, w: Int, h: Int) -> Bitmap? = ::load

    /** Deletes art no card uses any more. */
    fun prune(keep: Set<String>) {
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
    }

    private suspend fun load(url: String, w: Int, h: Int): Bitmap? {
        if (url.isBlank()) return null
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(url).size(w, h).allowHardware(false).build()
        )
        return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    }

    private fun draw(c: Canvas, card: HomeCard, backdrop: Bitmap, logo: Bitmap?) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawColor(Color.BLACK)
        // Backdrop, center-cropped to 16:9.
        val scale = maxOf(W.toFloat() / backdrop.width, H.toFloat() / backdrop.height)
        val sw = (W / scale).toInt(); val sh = (H / scale).toInt()
        val sx = (backdrop.width - sw) / 2; val sy = (backdrop.height - sh) / 2
        c.drawBitmap(backdrop, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, W, H), p)
        // Bottom scrim so the logo and label read on any image.
        p.shader = LinearGradient(0f, H * 0.35f, 0f, H.toFloat(), Color.TRANSPARENT, Color.argb(225, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, H * 0.35f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        val pad = 24f
        val bottom = H - (if (card.progress != null) 22f else 18f) - (if (card.label != null && card.progress != null) 30f else 0f)
        if (logo != null && logo.width > 0 && logo.height > 0) {
            val ls = minOf(W * 0.46f / logo.width, H * 0.30f / logo.height)
            val lw = logo.width * ls; val lh = logo.height * ls
            c.drawBitmap(logo, null, RectF(pad, bottom - lh, pad + lw, bottom), p)
        } else {
            p.color = Color.WHITE
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 38f
            p.setShadowLayer(8f, 0f, 2f, Color.BLACK)
            val text = ellipsize(card.title, p, W - pad * 2)
            c.drawText(text, pad, bottom - 6f, p)
            p.clearShadowLayer()
        }

        if (card.progress != null) {
            // "Continue Watching" over a red progress bar, like Netflix.
            card.label?.let { label ->
                p.color = Color.WHITE
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textSize = 22f
                p.setShadowLayer(6f, 0f, 1f, Color.BLACK)
                c.drawText(label, (W - p.measureText(label)) / 2f, H - 22f, p)
                p.clearShadowLayer()
            }
            p.color = Color.argb(110, 255, 255, 255)
            c.drawRect(0f, H - 8f, W.toFloat(), H.toFloat(), p)
            p.color = Color.rgb(229, 9, 20)
            c.drawRect(0f, H - 8f, W * card.progress.coerceIn(0.03f, 1f), H.toFloat(), p)
        } else card.label?.let { label ->
            // Badge in the top-right corner ("NEW", "NEW EPISODE").
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 20f
            val text = label.uppercase()
            val tw = p.measureText(text)
            val r = RectF(W - pad - tw - 20f, 16f, W - pad, 48f)
            p.color = Color.argb(235, 255, 255, 255)
            c.drawRoundRect(r, 5f, 5f, p)
            p.color = Color.BLACK
            c.drawText(text, r.left + 10f, r.bottom - 9f, p)
        }
    }

    private fun ellipsize(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var t = text
        while (t.length > 1 && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    companion object {
        const val W = 640
        const val H = 360
        const val DIR = "home-art"
        const val AUTHORITY_SUFFIX = ".homeart"
        private const val VERSION = 1

        private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

/** Serves the home-screen card art to the TV launcher (read-only, images only). */
class HomeArtProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val name = uri.lastPathSegment ?: return null
        if (!NAME.matches(name) || mode != "r") throw SecurityException("not available")
        val file = File(File(context!!.filesDir, HomeArt.DIR), name)
        if (!file.isFile) throw java.io.FileNotFoundException(name)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        val NAME = Regex("^[A-Za-z0-9_-]+\\.jpg$")
    }
}
