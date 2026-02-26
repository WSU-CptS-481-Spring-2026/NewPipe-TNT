package org.schabi.newpipe.local.playlist

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.database.LocalItem
import org.schabi.newpipe.database.playlist.PlaylistLocalItem

class LocalPlaylistSearchTest {
    data class TestPlaylistItem(
        override val orderingName: String,
        override val uid: Long = 0,
        override val thumbnailUrl: String? = "",
        override val displayIndex: Long? = 0,
        override val localItemType: LocalItem.LocalItemType =
            LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM
    ) : PlaylistLocalItem

    @Test
    fun testNoPlaylists() {
        val search = LocalPlaylistSearch<TestPlaylistItem>()
        val results = search.search("abcd")
        assert(results.isEmpty())
    }

    @Test
    fun testEmptyQuery() {
        val search = LocalPlaylistSearch<TestPlaylistItem>()

        val playlists = listOf(
            TestPlaylistItem("armadillo"),
            TestPlaylistItem("buffalo"),
            TestPlaylistItem("chinchilla")
        )

        search.receivePlaylists(playlists)

        val resultEmpty = search.search("")
        assertEquals(playlists, resultEmpty)

        val resultSpaceOnly = search.search(" \t\n\r")
        assertEquals(playlists, resultSpaceOnly)
    }

    @Test
    fun testMultiWordMismatch() {
        val search = LocalPlaylistSearch<TestPlaylistItem>()

        val playlists = listOf(
            TestPlaylistItem("armadillo buffalo"),
            TestPlaylistItem("chinchilla dolphin")
        )

        search.receivePlaylists(playlists)

        val results = search.search("armadillo dolphin")
        assert(results.isEmpty())
    }

    @Test
    fun testIgnoreCaseMatch() {
        val search = LocalPlaylistSearch<TestPlaylistItem>()

        val validPlaylists = listOf(
            TestPlaylistItem("abcd defg"),
            TestPlaylistItem("AbCd DeFg"),
            TestPlaylistItem("ABCD DEFG")
        )

        val invalidPlaylists = listOf(
            TestPlaylistItem("defg hijk"),
            TestPlaylistItem("DeFg HiJk"),
            TestPlaylistItem("DEFG HIJK")
        )

        val allPlaylists = validPlaylists + invalidPlaylists

        search.receivePlaylists(allPlaylists)

        // In all cases, any with abcd in any case should match.
        // Since FuzzyScore doesn't take into account case, ABCD DEFG gets sorted to top due to
        // alphabetical fallback.
        val resultsLower = search.search("abcd")
        assert(validPlaylists.all { it in resultsLower })
        assert(resultsLower.all { it in validPlaylists })
        assert(invalidPlaylists.all { it !in resultsLower })
        assertEquals("abcd defg", resultsLower[0].orderingName)

        val resultsUpper = search.search("ABCD")
        assert(validPlaylists.all { it in resultsUpper })
        assert(resultsUpper.all { it in validPlaylists })
        assert(invalidPlaylists.all { it !in resultsUpper })
        assertEquals("ABCD DEFG", resultsUpper[0].orderingName)

        val resultsMixedMatched = search.search("AbCd")
        assert(validPlaylists.all { it in resultsMixedMatched })
        assert(resultsMixedMatched.all { it in validPlaylists })
        assert(invalidPlaylists.all { it !in resultsMixedMatched })
        assertEquals("AbCd DeFg", resultsMixedMatched[0].orderingName)

        val resultsMixedUnmatched = search.search("aBcD")
        assert(validPlaylists.all { it in resultsMixedUnmatched })
        assert(resultsMixedUnmatched.all { it in validPlaylists })
        assert(invalidPlaylists.all { it !in resultsMixedUnmatched })
        assertEquals("ABCD DEFG", resultsMixedUnmatched[0].orderingName)
    }

    @Test
    fun testFuzzyMatching() {
        val search = LocalPlaylistSearch<TestPlaylistItem>()

        val playlistAbcd = TestPlaylistItem("abcd defg")
        val playlistDefg = TestPlaylistItem("defg hijk")
        val playlistAbcdefghijk = TestPlaylistItem("abcdefghijk")
        val playlistWxyz = TestPlaylistItem("wxyz")

        val playlists = listOf(
            playlistAbcd,
            playlistDefg,
            playlistAbcdefghijk,
            playlistWxyz
        )

        search.receivePlaylists(playlists)

        val resultsAbcd = search.search("abcd")
        assert(playlistAbcd in resultsAbcd)
        assert(playlistAbcdefghijk in resultsAbcd)
        assert(playlistDefg !in resultsAbcd)
        assert(playlistWxyz !in resultsAbcd)
        assertEquals(playlistAbcd, resultsAbcd[0])
        assertEquals(playlistAbcdefghijk, resultsAbcd[1])

        val resultsDefg = search.search("defg")
        assert(playlistAbcd in resultsDefg)
        assert(playlistDefg in resultsDefg)
        assert(playlistAbcdefghijk in resultsDefg)
        assert(playlistWxyz !in resultsDefg)
        assertEquals(playlistDefg, resultsDefg[0])
        assertEquals(playlistAbcd, resultsDefg[1])
        assertEquals(playlistAbcdefghijk, resultsDefg[2])

        val resultsHijk = search.search("hijk")
        assert(playlistAbcd !in resultsHijk)
        assert(playlistDefg in resultsHijk)
        assert(playlistAbcdefghijk in resultsHijk)
        assert(playlistWxyz !in resultsHijk)
        assertEquals(playlistDefg, resultsHijk[0])
        assertEquals(playlistAbcdefghijk, resultsHijk[1])
    }
}
