package org.schabi.newpipe.local.playlist

import java.util.Locale
import org.apache.commons.text.similarity.FuzzyScore
import org.schabi.newpipe.database.playlist.PlaylistLocalItem

class LocalPlaylistSearch<T : PlaylistLocalItem> {
    private var receivedPlaylists: List<T> = emptyList()

    fun receivePlaylists(playlists: List<T>) {
        receivedPlaylists = playlists
    }

    fun search(query: String): List<T> {
        if (query.isBlank()) return receivedPlaylists

        val filtered = filter(query)
        return sort(filtered, query)
    }

    private fun filter(query: String): List<T> {
        val searchQueryElements: List<String> = query
            .split(WORD_REGEX)
            .filter { it.isNotBlank() }
            .map { it.lowercase(Locale.ROOT) }

        return receivedPlaylists
            .filter { playlist ->
                val name = playlist.orderingName?.lowercase(Locale.ROOT) ?: return@filter false
                searchQueryElements.all { element -> name.contains(element) }
            }
    }

    private fun sort(playlists: List<T>, query: String): List<T> {
        return playlists
            .map {
                MatchScoreData(
                    it,
                    it.orderingName == query,
                    it.orderingName?.split(WORD_REGEX)?.any { word ->
                        word.startsWith(query)
                    } ?: false,
                    FUZZY_SCORE.fuzzyScore(it.orderingName, query)
                )
            }
            .sortedWith(
                compareByDescending<MatchScoreData<T>> { it.isExactMatch }
                    .thenByDescending { it.wordStartsWithQuery }
                    .thenByDescending { it.fuzzyScore }
                    .thenBy { it.item.orderingName }
            )
            .map { it.item }
    }

    private data class MatchScoreData<T>(
        val item: T,
        val isExactMatch: Boolean,
        val wordStartsWithQuery: Boolean,
        val fuzzyScore: Int
    )

    companion object {
        private val FUZZY_SCORE = FuzzyScore(Locale.ROOT)
        private val WORD_REGEX = Regex("\\s+")
    }
}
