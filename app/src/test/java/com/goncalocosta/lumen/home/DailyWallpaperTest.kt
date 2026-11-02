package com.goncalocosta.lumen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyWallpaperTest {
    @Test
    fun parsesBingResponse() {
        val json = """{"images":[{"url":"/th?id=OHR.Foo_EN-US1_1920x1080.jpg&rf=x","copyright":"A place (© Someone)"}]}"""
        val entry = DailyWallpaper.parse(json)!!
        assertEquals("https://www.bing.com/th?id=OHR.Foo_EN-US1_1920x1080.jpg&rf=x", entry.imageUrl)
        assertEquals("A place (© Someone)", entry.caption)
        assertNull(entry.date)
    }

    @Test
    fun parsesBingResponseWithStartDate() {
        val json = """{"images":[{"startdate":"20261028","url":"/th?id=OHR.Kunetice_1920x1080.jpg","copyright":"Castle (© Author)"}]}"""
        val entry = DailyWallpaper.parse(json)!!
        assertEquals("https://www.bing.com/th?id=OHR.Kunetice_1920x1080.jpg", entry.imageUrl)
        assertEquals("Castle (© Author)", entry.caption)
        assertEquals("2026-10-28", entry.date)
    }

    @Test
    fun badJsonReturnsNull() {
        assertNull(DailyWallpaper.parse("not json"))
        assertNull(DailyWallpaper.parse("""{"images":[]}"""))
    }

    @Test
    fun refreshOnlyWhenDateChangedOrNoCache() {
        assertFalse(DailyWallpaper.needsRefresh("2026-10-27", cacheExists = true, today = "2026-10-27"))
        assertTrue(DailyWallpaper.needsRefresh("2026-10-26", cacheExists = true, today = "2026-10-27"))
        assertTrue(DailyWallpaper.needsRefresh("2026-10-27", cacheExists = false, today = "2026-10-27"))
        assertTrue(DailyWallpaper.needsRefresh(null, cacheExists = true, today = "2026-10-27"))
    }

    @Test
    fun unknownBundledFallsBackToMist() {
        assertEquals(BundledWallpaper.Mist, BundledWallpaper.fromId("nope"))
        assertEquals(BundledWallpaper.Aurora, BundledWallpaper.fromId("aurora"))
        assertEquals(BundledWallpaper.Peaks, BundledWallpaper.fromId("Peaks"))
    }
}
