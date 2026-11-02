package com.goncalocosta.lumen.home

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class GlassGlyphType {
    Lumen,
    Meo,
    Stremio,
    YouTube,
    PlayStore,
    Downloader,
    DevTools,
    Netflix,
    PrimeVideo,
    DisneyPlus,
    Max,
    Spotify,
    Vlc,
    RtpPlay,
    MoreApps,
    DynamicSilhouette
}

/**
 * Immutable launcher entry. Equality is based on identity-relevant metadata only
 * (package, activity, label, update time), so reloading an unchanged catalogue produces
 * *equal* lists: StateFlow conflates them and Compose skips recomposition entirely.
 */
@Immutable
class HomeApp internal constructor(
    val packageName: String,
    val activityName: String,
    val category: String,
    val label: String,
    /** Full-colour square icon (grid / menus). */
    val icon: ImageBitmap,
    /** Full-colour 16:9 TV banner when the app provides a real one. */
    val banner: ImageBitmap?,
    val glyphType: GlassGlyphType,
    val canUninstall: Boolean,
    val versionStamp: Long,
    private val visuals: CachedVisuals
) {
    /** White monochrome silhouette used by the dock (computed lazily, once per app version). */
    val glyph: ImageBitmap get() = visuals.glyph

    fun launchIntent(): Intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(category)
        setClassName(packageName, activityName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HomeApp) return false
        return packageName == other.packageName &&
            activityName == other.activityName &&
            label == other.label &&
            versionStamp == other.versionStamp &&
            canUninstall == other.canUninstall
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + activityName.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + versionStamp.hashCode()
        return result
    }

    override fun toString(): String = "HomeApp($packageName, $label)"
}

/** Decoded bitmaps for one package version; reused across reloads. */
internal class CachedVisuals(
    val label: String,
    val icon: ImageBitmap,
    val banner: ImageBitmap?,
    private val glyphSource: Bitmap
) {
    val glyph: ImageBitmap by lazy {
        toWhiteMonochromeGlyph(glyphSource).asImageBitmap().also { it.prepareToDraw() }
    }
}

internal const val LUMEN_TV = "com.goncalocosta.lumen.tv"

private const val ICON_SIZE = 128
private const val BANNER_W = 320
private const val BANNER_H = 180

private val PreferredOrder = listOf(
    "com.goncalocosta.lumen.tv",
    "com.alticelabs.meo.androidtv",
    "com.google.android.youtube.tv",
    "com.stremio.one",
    "com.android.vending",
    "com.esaba.downloader",
    "com.roysolberg.android.developertools"
)

/**
 * Loads launchable apps, caching decoded icons per `package/activity@lastUpdateTime`
 * so only new or updated packages are ever decoded again. Not thread-safe: callers
 * serialise access (HomeViewModel uses a Mutex).
 */
class AppLoader(private val pm: PackageManager, private val selfPackage: String) {
    private val cache = HashMap<String, CachedVisuals>()

    /**
     * @param only when non-null, only these packages are materialised (fast first frame).
     * @param prewarmGlyphs packages whose dock glyph should be computed now, off the main thread.
     */
    fun load(only: Set<String>? = null, prewarmGlyphs: Collection<String> = emptyList()): List<HomeApp> {
        val byPackage = LinkedHashMap<String, Pair<ResolveInfo, String>>()
        for (info in query(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val pkg = info.activityInfo?.packageName ?: continue
            byPackage.putIfAbsent(pkg, info to Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        for (info in query(Intent.CATEGORY_LAUNCHER)) {
            val pkg = info.activityInfo?.packageName ?: continue
            byPackage.putIfAbsent(pkg, info to Intent.CATEGORY_LAUNCHER)
        }

        val usedKeys = HashSet<String>()
        val apps = byPackage.values.mapNotNull { (info, category) ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val pkg = activity.packageName
            if (only != null && pkg !in only) return@mapNotNull null
            if (shouldHide(pkg, activity.name, selfPackage)) return@mapNotNull null
            if (!activity.enabled || !activity.applicationInfo.enabled) return@mapNotNull null

            val stamp = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(0L)
            val key = "$pkg/${activity.name}@$stamp"
            usedKeys += key
            val visuals = cache[key] ?: runCatching { decode(info) }.getOrNull()?.also { cache[key] = it }
                ?: return@mapNotNull null

            val isSystem = activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            HomeApp(
                packageName = pkg,
                activityName = activity.name,
                category = category,
                label = visuals.label,
                icon = visuals.icon,
                banner = visuals.banner,
                glyphType = resolveGlyphType(pkg),
                canUninstall = !isSystem,
                versionStamp = stamp,
                visuals = visuals
            )
        }

        // Drop stale versions (only on full loads, a partial load doesn't see everything).
        if (only == null) cache.keys.retainAll(usedKeys)

        val prewarm = prewarmGlyphs.toHashSet()
        for (app in apps) {
            if (app.packageName in prewarm && app.glyphType == GlassGlyphType.DynamicSilhouette) app.glyph
        }

        return apps.sortedWith(
            compareBy<HomeApp> { app ->
                val idx = PreferredOrder.indexOf(app.packageName)
                if (idx >= 0) idx else 100
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
    }

    private fun decode(info: ResolveInfo): CachedVisuals {
        val activity = info.activityInfo
        val pkg = activity.packageName
        val label = cleanLabel(pkg, info.loadLabel(pm).toString().ifBlank { pkg })
        val bannerDrawable = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        val iconDrawable = activity.loadIcon(pm) ?: activity.applicationInfo.loadIcon(pm)

        val banner = bannerDrawable
            ?.takeIf { isWide(it) }
            ?.let { renderFit(it, BANNER_W, BANNER_H, crop = true) }
        val iconSource = iconDrawable ?: bannerDrawable ?: error("no icon")
        val icon = renderFit(iconSource, ICON_SIZE, ICON_SIZE, crop = false)
        // The silhouette looks best from a square banner when available (legacy behaviour).
        val glyphSource = if (bannerDrawable != null && isSquareish(bannerDrawable)) {
            renderFit(bannerDrawable, ICON_SIZE, ICON_SIZE, crop = true)
        } else {
            icon
        }

        return CachedVisuals(
            label = label,
            icon = icon.asImageBitmap().also { it.prepareToDraw() },
            banner = banner?.asImageBitmap()?.also { it.prepareToDraw() },
            glyphSource = glyphSource
        )
    }

    private fun query(category: String): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return pm.queryIntentActivities(intent, 0)
    }
}

private fun resolveGlyphType(pkg: String): GlassGlyphType {
    val lower = pkg.lowercase()
    return when {
        pkg == LUMEN_TV -> GlassGlyphType.Lumen
        "meo" in lower || "alticelabs" in lower -> GlassGlyphType.Meo
        "stremio" in lower -> GlassGlyphType.Stremio
        "youtube" in lower -> GlassGlyphType.YouTube
        pkg == "com.android.vending" -> GlassGlyphType.PlayStore
        "downloader" in lower || "esaba" in lower -> GlassGlyphType.Downloader
        "developertools" in lower -> GlassGlyphType.DevTools
        "netflix" in lower -> GlassGlyphType.Netflix
        "amazonvideo" in lower || "primevideo" in lower -> GlassGlyphType.PrimeVideo
        "disney" in lower -> GlassGlyphType.DisneyPlus
        "hbomax" in lower || "wbd" in lower -> GlassGlyphType.Max
        "spotify" in lower -> GlassGlyphType.Spotify
        "vlc" in lower -> GlassGlyphType.Vlc
        "rtp" in lower -> GlassGlyphType.RtpPlay
        else -> GlassGlyphType.DynamicSilhouette
    }
}

private fun cleanLabel(pkg: String, raw: String): String = when {
    pkg == LUMEN_TV -> "Lumen"
    "alticelabs.meo" in pkg -> "MEO"
    pkg == "com.android.vending" -> "Play Store"
    "developertools" in pkg.lowercase() -> "Dev Tools"
    else -> raw
}

private fun shouldHide(packageName: String, activityName: String, selfPackage: String): Boolean {
    if (packageName == selfPackage) return true
    if (packageName == "com.goncalocosta.lumen.launcher") return true
    if (packageName.startsWith("com.sdmc.")) return true
    if (packageName == "com.droidlogic.updater") return true
    if (packageName == "com.android.tv.settings" || packageName == "com.android.settings") return true
    val blob = "$packageName $activityName".lowercase()
    if ("factorytest" in blob || "factory.launcher" in blob || "factest" in blob) return true
    if ("setup" in blob && "settings" !in blob) return true
    if ("disclaimer" in blob) return true
    if ("observer" in blob) return true
    if (packageName.lowercase().endsWith(".provision")) return true
    return false
}

private fun isWide(drawable: Drawable): Boolean {
    val w = drawable.intrinsicWidth
    val h = drawable.intrinsicHeight
    return w > 0 && h > 0 && w.toFloat() / h > 1.4f
}

private fun isSquareish(drawable: Drawable): Boolean {
    val w = drawable.intrinsicWidth
    val h = drawable.intrinsicHeight
    if (w <= 0 || h <= 0) return false
    return abs(w - h).toFloat() / max(w, h) < 0.15f
}

/** Renders any drawable into a [w]x[h] software bitmap, fit-centred or centre-cropped. */
private fun renderFit(drawable: Drawable, w: Int, h: Int, crop: Boolean): Bitmap {
    val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: w
    val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: h
    val scale = if (crop) max(w.toFloat() / iw, h.toFloat() / ih) else min(w.toFloat() / iw, h.toFloat() / ih)
    val dw = iw * scale
    val dh = ih * scale
    val left = (w - dw) / 2f
    val top = (h - dh) / 2f

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val src = (drawable as? BitmapDrawable)?.bitmap
    if (src != null) {
        val soft = if (Build.VERSION.SDK_INT >= 26 && src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            src
        }
        canvas.drawBitmap(soft, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
    } else {
        drawable.setBounds(left.toInt(), top.toInt(), (left + dw).toInt(), (top + dh).toInt())
        drawable.draw(canvas)
    }
    return out
}

/**
 * Converts any arbitrary app icon bitmap into a clean frosted-white monochrome glyph
 * by stripping its outer background color and mapping foreground contrast to white alpha.
 */
private fun toWhiteMonochromeGlyph(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    // Sample corner/border color to identify background
    val inset = (w / 20).coerceAtLeast(1)
    val samplePoints = intArrayOf(
        pixels[inset * w + inset],
        pixels[inset * w + (w - 1 - inset)],
        pixels[(h - 1 - inset) * w + inset],
        pixels[(h - 1 - inset) * w + (w - 1 - inset)]
    )
    val bgR = samplePoints.map { (it ushr 16) and 0xFF }.average().toInt()
    val bgG = samplePoints.map { (it ushr 8) and 0xFF }.average().toInt()
    val bgB = samplePoints.map { it and 0xFF }.average().toInt()

    var minX = w
    var minY = h
    var maxX = -1
    var maxY = -1

    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            val px = pixels[i]
            val a = (px ushr 24) and 0xFF
            if (a < 25) {
                pixels[i] = 0x00000000
                continue
            }
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val dist = abs(r - bgR) + abs(g - bgG) + abs(b - bgB)
            val alpha = ((dist - 35) * 3).coerceIn(0, 255) * a / 255
            pixels[i] = (alpha shl 24) or 0x00FFFFFF
            if (alpha > 30) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }

    out.setPixels(pixels, 0, w, 0, 0, w, h)

    if (minX in 0..maxX && minY in 0..maxY && (maxX - minX >= 8) && (maxY - minY >= 8)) {
        val padX = ((maxX - minX) * 0.04f).toInt().coerceAtLeast(1)
        val padY = ((maxY - minY) * 0.04f).toInt().coerceAtLeast(1)
        val cropX = (minX - padX).coerceAtLeast(0)
        val cropY = (minY - padY).coerceAtLeast(0)
        val cropW = (maxX + padX - cropX + 1).coerceAtMost(w - cropX)
        val cropH = (maxY + padY - cropY + 1).coerceAtMost(h - cropY)
        return Bitmap.createBitmap(out, cropX, cropY, cropW, cropH)
    }

    return out
}
