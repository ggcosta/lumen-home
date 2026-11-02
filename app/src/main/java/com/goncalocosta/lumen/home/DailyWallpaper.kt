package com.goncalocosta.lumen.home

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fetches Bing's "Image of the Day" once per day and caches it in app storage. */
object DailyWallpaper {
    private const val HOST = "https://www.bing.com"
    private const val API = "$HOST/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-WW"
    private const val TIMEOUT_MS = 10_000
    private const val MAX_DECODE_WIDTH = 1920
    private const val THROTTLE_MS = 15 * 60 * 1000L // 15 minutes between metadata polls when date hasn't changed on Bing

    data class Entry(val imageUrl: String, val caption: String?, val date: String? = null)

    fun file(context: Context) = File(context.filesDir, "daily.jpg")

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Extracts the first image URL and its copyright line from the HPImageArchive response. */
    fun parse(json: String): Entry? = runCatching {
        val img = JSONObject(json).getJSONArray("images").getJSONObject(0)
        val url = img.getString("url")
        val absolute = if (url.startsWith("http")) url else HOST + url
        val rawDate = img.optString("startdate").takeIf { it.isNotBlank() }
        val formattedDate = rawDate?.let { s ->
            if (s.length == 8) "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}" else s
        }
        Entry(absolute, img.optString("copyright").takeIf { it.isNotBlank() }, formattedDate)
    }.getOrNull()

    /** True when a fetch is required: no cached file, or the last fetch was on a different day. */
    fun needsRefresh(lastDate: String?, cacheExists: Boolean, today: String = today()): Boolean =
        !cacheExists || lastDate != today

    /** Blocking; call off the main thread. Returns true when a fresh image was stored. */
    fun refreshIfNeeded(context: Context, prefs: Prefs, force: Boolean = false): Boolean {
        val target = file(context)
        val cacheExists = target.exists()
        val lastDate = prefs.dailyDate()
        val lastUrl = prefs.dailyUrl()
        val dateChanged = needsRefresh(lastDate, cacheExists)

        if (!force && !dateChanged && lastUrl != null) {
            return false
        }

        val now = System.currentTimeMillis()
        val lastCheck = prefs.dailyLastCheck()
        if (!force && cacheExists && lastUrl != null && (now - lastCheck) < THROTTLE_MS) {
            return false
        }

        return runCatching {
            prefs.saveDailyCheckTime(now)
            val entry = parse(download(API).toString(Charsets.UTF_8)) ?: return false
            val imageDate = entry.date ?: today()

            // If the image currently on disk is already this exact image URL, no need to re-download image bytes.
            if (cacheExists && entry.imageUrl == lastUrl) {
                prefs.saveDaily(imageDate, entry.caption, entry.imageUrl)
                return false
            }

            val bytes = download(entry.imageUrl)
            // Make sure it really is an image before replacing the cached one.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return false

            val tmp = File(context.filesDir, "daily.jpg.tmp")
            tmp.writeBytes(bytes)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            prefs.saveDaily(imageDate, entry.caption, entry.imageUrl)
            true
        }.getOrDefault(false)
    }

    fun decode(context: Context): ImageBitmap? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DECODE_WIDTH) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()?.also { it.prepareToDraw() }
    }.getOrNull()

    private fun download(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
