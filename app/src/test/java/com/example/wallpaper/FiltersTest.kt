package com.example.wallpaper

import com.example.wallpaper.data.Filters
import org.junit.Assert.*
import org.junit.Test

class FiltersTest {
    @Test fun everySearchIsSfwWithoutAnApiKey() {
        for(sort in listOf("toplist", "date_added", "random", "relevance")) {
            val parameters = Filters(sorting = sort).parameters("nature", 1, null)
            assertEquals("100", parameters["purity"])
            assertEquals("111", parameters["categories"])
            assertFalse(parameters.containsKey("apikey"))
        }
    }
    @Test fun randomPaginationKeepsSeed() {
        val parameters = Filters(sorting = "random").parameters("", 3, "abc123")
        assertEquals("3", parameters["page"])
        assertEquals("abc123", parameters["seed"])
        assertFalse(parameters.containsKey("topRange"))
    }
    @Test fun nonRandomSearchDoesNotCarrySeed() {
        val parameters = Filters().parameters("+nature -people", 1, "abc123")
        assertFalse(parameters.containsKey("seed"))
        assertEquals("1M", parameters["topRange"])
        assertEquals("+nature -people", parameters["q"])
    }
    @Test fun resetOmitsOptionalFilters() {
        val filtered = Filters(color = "663399", ratio = "9x16", resolution = "1920x1080").parameters("", 1, null)
        assertEquals("663399", filtered["colors"])
        assertEquals("9x16", filtered["ratios"])
        assertEquals("1920x1080", filtered["atleast"])
        val reset = Filters().parameters("", 1, null)
        for(key in listOf("colors", "ratios", "atleast", "q")) assertFalse(reset.containsKey(key))
    }
}
