package com.goncalocosta.lumen.home

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun main(): List<String> {
        val raw = prefs.getString(KEY_MAIN, null) ?: prefs.getString(KEY_ORDER, "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    fun saveMain(packageNames: List<String>) {
        prefs.edit()
            .putString(KEY_MAIN, packageNames.joinToString("\n"))
            .putBoolean(KEY_MIGRATED, true)
            .apply()
    }

    fun migrated(): Boolean = prefs.getBoolean(KEY_MIGRATED, false)

    fun markMigrated() {
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    fun wallpaperBundled(): BundledWallpaper =
        BundledWallpaper.fromId(prefs.getString(KEY_WP_BUNDLED, null))

    fun setWallpaperBundled(wallpaper: BundledWallpaper) {
        prefs.edit().putString(KEY_WP_BUNDLED, wallpaper.id).apply()
    }

    fun wallpaperDaily(): Boolean = prefs.getBoolean(KEY_WP_DAILY, false)

    fun setWallpaperDaily(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WP_DAILY, enabled).apply()
    }

    /** yyyy-MM-dd of the last successful daily image download. */
    fun dailyDate(): String? = prefs.getString(KEY_WP_DATE, null)

    fun dailyCaption(): String? = prefs.getString(KEY_WP_CAPTION, null)

    fun dailyUrl(): String? = prefs.getString(KEY_WP_URL, null)

    fun dailyLastCheck(): Long = prefs.getLong(KEY_WP_LAST_CHECK, 0L)

    fun saveDailyCheckTime(timeMs: Long) {
        prefs.edit().putLong(KEY_WP_LAST_CHECK, timeMs).apply()
    }

    fun saveDaily(date: String, caption: String?, imageUrl: String? = null) {
        val editor = prefs.edit()
            .putString(KEY_WP_DATE, date)
            .putString(KEY_WP_CAPTION, caption)
        if (imageUrl != null) {
            editor.putString(KEY_WP_URL, imageUrl)
        }
        editor.apply()
    }

    fun vpnPackage(): String? = prefs.getString(KEY_VPN_PACKAGE, null)

    fun setVpnPackage(packageName: String?) {
        val editor = prefs.edit()
        if (packageName != null) {
            editor.putString(KEY_VPN_PACKAGE, packageName)
        } else {
            editor.remove(KEY_VPN_PACKAGE)
        }
        editor.apply()
    }

    fun screensaverTimeoutMinutes(): Int = prefs.getInt(KEY_SCREENSAVER_TIMEOUT, 3)

    fun setScreensaverTimeoutMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_SCREENSAVER_TIMEOUT, minutes).apply()
    }

    fun remoteKey(color: ColorKey): String? = prefs.getString(KEY_REMOTE_PREFIX + color.name, null)

    fun setRemoteKey(color: ColorKey, packageName: String?) {
        val editor = prefs.edit()
        val key = KEY_REMOTE_PREFIX + color.name
        if (packageName != null) {
            editor.putString(key, packageName)
        } else {
            editor.remove(key)
        }
        editor.apply()
    }

    companion object {
        private const val KEY_SCREENSAVER_TIMEOUT = "screensaver_timeout_minutes"
        private const val KEY_REMOTE_PREFIX = "remote_key_"
        private const val KEY_VPN_PACKAGE = "vpn_package"
        private const val KEY_WP_BUNDLED = "wallpaper_bundled"
        private const val KEY_WP_DAILY = "wallpaper_daily"
        private const val KEY_WP_DATE = "wallpaper_daily_date"
        private const val KEY_WP_CAPTION = "wallpaper_daily_caption"
        private const val KEY_WP_URL = "wallpaper_daily_url"
        private const val KEY_WP_LAST_CHECK = "wallpaper_daily_last_check"
        private const val KEY_ORDER = "order"
        private const val KEY_MAIN = "main_dock_v3"
        private const val KEY_MIGRATED = "migrated_dock_v3"
    }
}
