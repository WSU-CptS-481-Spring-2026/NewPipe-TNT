package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

object ChannelTabHelper {

    private val STREAMS_TABS = setOf(
        ChannelTabs.VIDEOS,
        ChannelTabs.TRACKS,
        ChannelTabs.LIKES,
        ChannelTabs.SHORTS,
        ChannelTabs.LIVESTREAMS
    )

    private val SHOW_TAB_KEYS = mapOf(
        ChannelTabs.VIDEOS to R.string.show_channel_tabs_videos,
        ChannelTabs.TRACKS to R.string.show_channel_tabs_tracks,
        ChannelTabs.SHORTS to R.string.show_channel_tabs_shorts,
        ChannelTabs.LIVESTREAMS to R.string.show_channel_tabs_livestreams,
        ChannelTabs.CHANNELS to R.string.show_channel_tabs_channels,
        ChannelTabs.PLAYLISTS to R.string.show_channel_tabs_playlists,
        ChannelTabs.ALBUMS to R.string.show_channel_tabs_albums,
        ChannelTabs.LIKES to R.string.show_channel_tabs_likes
    )

    private val FETCH_FEED_TAB_KEYS = mapOf(
        ChannelTabs.VIDEOS to R.string.fetch_channel_tabs_videos,
        ChannelTabs.TRACKS to R.string.fetch_channel_tabs_tracks,
        ChannelTabs.SHORTS to R.string.fetch_channel_tabs_shorts,
        ChannelTabs.LIVESTREAMS to R.string.fetch_channel_tabs_livestreams,
        ChannelTabs.LIKES to R.string.fetch_channel_tabs_likes
    )

    private val TRANSLATION_KEYS = mapOf(
        ChannelTabs.VIDEOS to R.string.channel_tab_videos,
        ChannelTabs.TRACKS to R.string.channel_tab_tracks,
        ChannelTabs.SHORTS to R.string.channel_tab_shorts,
        ChannelTabs.LIVESTREAMS to R.string.channel_tab_livestreams,
        ChannelTabs.CHANNELS to R.string.channel_tab_channels,
        ChannelTabs.PLAYLISTS to R.string.channel_tab_playlists,
        ChannelTabs.ALBUMS to R.string.channel_tab_albums,
        ChannelTabs.LIKES to R.string.channel_tab_likes
    )

    @JvmStatic
    fun isStreamsTab(tab: String): Boolean {
        return STREAMS_TABS.contains(tab)
    }

    @JvmStatic
    fun isStreamsTab(tab: ListLinkHandler): Boolean {
        val filter = getFirstFilter(tab)
        if (filter == null) return false
        return isStreamsTab(filter)
    }

    @JvmStatic
    @StringRes
    private fun getShowTabKey(tab: String): Int {
        return SHOW_TAB_KEYS[tab] ?: -1
    }

    @JvmStatic
    @StringRes
    private fun getFetchFeedTabKey(tab: String): Int {
        return FETCH_FEED_TAB_KEYS[tab] ?: -1
    }

    @JvmStatic
    @StringRes
    fun getTranslationKey(tab: String): Int {
        return TRANSLATION_KEYS[tab] ?: R.string.unknown_content
    }

    @JvmStatic
    fun showChannelTab(context: Context, sharedPreferences: SharedPreferences, @StringRes key: Int): Boolean {
        return isPrefEnabled(context, sharedPreferences, key, R.string.show_channel_tabs_key)
    }

    @JvmStatic
    fun showChannelTab(context: Context, sharedPreferences: SharedPreferences, tab: String): Boolean {
        val key = getShowTabKey(tab)
        return isPrefEnabled(context, sharedPreferences, key, R.string.show_channel_tabs_key)
    }

    @JvmStatic
    fun fetchFeedChannelTab(context: Context, sharedPreferences: SharedPreferences, tab: ListLinkHandler): Boolean {
        val filter = getFirstFilter(tab)
        if (filter == null) return false

        val key = getFetchFeedTabKey(filter)
        return isPrefEnabled(context, sharedPreferences, key, R.string.feed_fetch_channel_tabs_key)
    }

    // ==========================================
    // THE METRIC BUSTER HELPERS
    // ==========================================

    private fun getFirstFilter(tab: ListLinkHandler): String? {
        val filters = tab.contentFilters
        if (filters.isEmpty()) return null
        return filters[0]
    }

    private fun isPrefEnabled(context: Context, sharedPreferences: SharedPreferences, key: Int, prefKey: Int): Boolean {
        if (key == -1) return false

        val enabledTabs = sharedPreferences.getStringSet(context.getString(prefKey), null)
        if (enabledTabs == null) return true

        return enabledTabs.contains(context.getString(key))
    }
}