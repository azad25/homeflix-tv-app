package com.homeflix.tv.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlin.math.sin

/**
 * Made-up artwork for README screenshots (no real titles or posters): a dusk
 * sky, a sun or moon and layered hills, coloured from the URL. Logos fail so
 * the UI shows its text title, as it does for titles without a logo.
 */
object MockArt {
    fun loader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .dispatcher(Dispatchers.Unconfined)
        .components { add(Interceptor { chain -> intercept(chain) }) }
        .build()

    private fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data.toString()
        if (url.contains("logo") || url.isBlank() || url == "null") {
            return ErrorResult(null, chain.request, IllegalStateException("no logo"))
        }
        val w = chain.size.width.pxOrElse { 480 }.coerceIn(16, 1920)
        val h = chain.size.height.pxOrElse { 270 }.coerceIn(16, 1080)
        val bmp = paint(url, w, h)
        // No crossfade: Coil times it on the real clock, the screenshot test runs a virtual one.
        val request = chain.request.newBuilder().crossfade(false).build()
        return SuccessResult(BitmapDrawable(request.context.resources, bmp), request, DataSource.MEMORY)
    }

    private val palettes = listOf(
        intArrayOf(0xFF0D0B1E.toInt(), 0xFF3B2A5A.toInt(), 0xFFE9A46A.toInt()),
        intArrayOf(0xFF07131C.toInt(), 0xFF1B4057.toInt(), 0xFF9FD3E6.toInt()),
        intArrayOf(0xFF140808.toInt(), 0xFF5A1E1E.toInt(), 0xFFF08A4B.toInt()),
        intArrayOf(0xFF061208.toInt(), 0xFF1E4A2C.toInt(), 0xFFC9E39A.toInt()),
        intArrayOf(0xFF120A16.toInt(), 0xFF4A2350.toInt(), 0xFFF3B7D3.toInt()),
        intArrayOf(0xFF0B0F17.toInt(), 0xFF2C3550.toInt(), 0xFFD8D6C8.toInt()),
        intArrayOf(0xFF161007.toInt(), 0xFF56401C.toInt(), 0xFFF2D27A.toInt()),
        intArrayOf(0xFF061416.toInt(), 0xFF164B4E.toInt(), 0xFF8FE3D2.toInt())
    )

    fun paint(seed: String, w: Int, h: Int): Bitmap {
        val hash = seed.hashCode().let { if (it < 0) -it else it }
        val p = palettes[hash % palettes.size]
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), p[0], p[1], Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        // Stars
        paint.color = Color.argb(140, 255, 255, 255)
        for (i in 0 until 40) {
            val x = ((hash / (i + 1)) * 7919L % w).toFloat(); val y = ((hash / (i + 3)) * 104729L % (h / 2)).toFloat()
            c.drawCircle(x, y, (w / 600f).coerceAtLeast(0.8f), paint)
        }
        // Sun / moon
        paint.color = p[2]
        val r = minOf(w, h) * (0.12f + (hash % 7) / 70f)
        c.drawCircle(w * (0.25f + (hash % 50) / 100f), h * (0.38f + (hash % 13) / 100f), r, paint)
        // Hills
        for (layer in 0 until 3) {
            val base = h * (0.62f + layer * 0.12f)
            val amp = h * (0.08f - layer * 0.015f)
            val path = Path().apply {
                moveTo(0f, h.toFloat())
                var x = 0f
                while (x <= w) {
                    lineTo(x, base + amp * sin((x / w) * (3 + layer + hash % 3) + layer * 1.7f + hash % 5).toFloat())
                    x += w / 60f
                }
                lineTo(w.toFloat(), h.toFloat()); close()
            }
            // Nearer hills are darker (t stays below 1 so colours never wrap).
            paint.color = blend(p[1], Color.BLACK, 0.45f + layer * 0.17f)
            c.drawPath(path, paint)
        }
        return bmp
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    )
}
