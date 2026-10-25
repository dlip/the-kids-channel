package com.thekidschannel.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelOrderTest {
    private val channels = listOf(
        ChannelFolder("root-1", "stories", "Stories", 300),
        ChannelFolder("root-2", "songs-10", "Songs 10", 100),
        ChannelFolder("root-1", "songs-2", "Songs 2", 200),
        ChannelFolder("root-2", "cartoons", "Cartoons"),
    )
    private val watched = mapOf("stories" to 60_000L, "songs-10" to 120_000L, "songs-2" to 120_000L)

    @Test fun sortsMostAndLeastWatchedAcrossRootsWithAlphabeticalTies() {
        assertOrder(ChannelOrder.WATCH_TIME, true, "songs-2", "songs-10", "stories", "cartoons")
        assertOrder(ChannelOrder.WATCH_TIME, false, "cartoons", "stories", "songs-2", "songs-10")
    }

    @Test fun sortsNamesNaturallyInBothDirections() {
        assertOrder(ChannelOrder.ALPHABETICAL, false, "cartoons", "songs-2", "songs-10", "stories")
        assertOrder(ChannelOrder.ALPHABETICAL, true, "stories", "songs-10", "songs-2", "cartoons")
    }

    @Test fun sortsOldestAndNewestDirectoriesWithUnknownDatesLast() {
        assertOrder(ChannelOrder.DIRECTORY_AGE, false, "songs-10", "songs-2", "stories", "cartoons")
        assertOrder(ChannelOrder.DIRECTORY_AGE, true, "stories", "songs-2", "songs-10", "cartoons")
    }

    @Test fun usesAlphabeticalOrderWhenDatesOrWatchTimesAreMissing() {
        val undated = channels.map { it.copy(lastModifiedMs = null) }
        val expected = listOf("cartoons", "songs-2", "songs-10", "stories")
        for (descending in listOf(false, true)) {
            assertEquals(expected, sortChannels(undated, emptyMap(), ChannelOrder.WATCH_TIME, descending).map { it.uri })
            assertEquals(expected, sortChannels(undated, watched, ChannelOrder.DIRECTORY_AGE, descending).map { it.uri })
        }
    }

    @Test fun resolvesIdenticalNamesByUriWithoutChangingTheInput() {
        val identical = listOf(ChannelFolder("root-2", "b", "Songs"), ChannelFolder("root-1", "a", "Songs"))
        val sorted = sortChannels(identical, emptyMap(), ChannelOrder.WATCH_TIME, true)
        assertEquals(listOf("a", "b"), sorted.map { it.uri })
        assertEquals(listOf("b", "a"), identical.map { it.uri })
        assertEquals(emptyList<ChannelFolder>(), sortChannels(emptyList(), watched, ChannelOrder.WATCH_TIME, true))
    }

    private fun assertOrder(order: ChannelOrder, descending: Boolean, vararg expected: String) {
        assertEquals(expected.toList(), sortChannels(channels, watched, order, descending).map { it.uri })
    }
}
