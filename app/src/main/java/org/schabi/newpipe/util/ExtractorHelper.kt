package org.schabi.newpipe.util

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.net.URL
import java.util.Locale.getDefault
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.Info
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.Localization.DOT_SEPARATOR
import org.schabi.newpipe.util.text.TextLinkifier
import org.schabi.newpipe.util.text.TextLinkifier.SET_LINK_MOVEMENT_METHOD

object ExtractorHelper {
    private val TAG = ExtractorHelper::class.java.simpleName
    private val CACHE = InfoCache.getInstance()

    private fun checkServiceId(serviceId: Int) {
        if (serviceId == NO_SERVICE_ID) {
            throw IllegalArgumentException("serviceId is NO_SERVICE_ID")
        }
    }

    @JvmStatic
    fun searchFor(
        serviceId: Int,
        searchString: String?,
        contentFilter: List<String>,
        sortFilter: String
    ): Single<SearchInfo> {
        checkServiceId(serviceId)
        checkNotNull(searchString)

        return Single.fromCallable {
            SearchInfo.getInfo(
                NewPipe.getService(serviceId),
                NewPipe.getService(serviceId)
                    .searchQHFactory
                    .fromQuery(searchString, contentFilter, sortFilter)
            )
        }
    }

    @JvmStatic
    fun getMoreSearchItems(
        serviceId: Int,
        searchString: String?,
        contentFilter: List<String>,
        sortFilter: String,
        page: Page
    ): Single<InfoItemsPage<InfoItem>> {
        checkServiceId(serviceId)
        checkNotNull(searchString)
        return Single.fromCallable {
            SearchInfo.getMoreItems(
                NewPipe.getService(serviceId),
                NewPipe.getService(serviceId)
                    .searchQHFactory
                    .fromQuery(searchString, contentFilter, sortFilter),
                page
            )
        }
    }

    @JvmStatic
    fun suggestionsFor(
        serviceId: Int,
        query: String
    ): Single<List<String>> {
        checkServiceId(serviceId)
        return Single.fromCallable { getSuggestionsFor(serviceId, query) }
    }

    private fun getSuggestionsFor(
        serviceId: Int,
        query: String
    ): List<String> {
        val extractor = NewPipe.getService(serviceId).suggestionExtractor
        return extractor?.suggestionList(query) ?: emptyList()
    }

    @JvmStatic
    fun getStreamInfo(
        serviceId: Int,
        url: String?,
        forceLoad: Boolean
    ): Single<StreamInfo> = checkCache(
        forceLoad,
        serviceId,
        url,
        InfoCache.Type.STREAM,
        Single.fromCallable {
            StreamInfo.getInfo(NewPipe.getService(serviceId), url)
        }
    )

    @JvmStatic
    fun getChannelInfo(
        serviceId: Int,
        url: String?,
        forceLoad: Boolean
    ): Single<ChannelInfo> = checkCache(
        forceLoad,
        serviceId,
        url,
        InfoCache.Type.CHANNEL,
        Single.fromCallable {
            ChannelInfo.getInfo(NewPipe.getService(serviceId), url)
        }
    )

    @JvmStatic
    fun getChannelTab(
        serviceId: Int,
        listLinkHandler: ListLinkHandler,
        forceLoad: Boolean
    ): Single<ChannelTabInfo> = checkCache(
        forceLoad,
        serviceId,
        listLinkHandler.url,
        InfoCache.Type.CHANNEL_TAB,
        Single.fromCallable {
            ChannelTabInfo.getInfo(NewPipe.getService(serviceId), listLinkHandler)
        }
    )

    @JvmStatic
    fun getMoreChannelTabItems(
        serviceId: Int,
        listLinkHandler: ListLinkHandler,
        nextPage: Page
    ): Single<InfoItemsPage<InfoItem>> {
        checkServiceId(serviceId)
        return Single.fromCallable {
            ChannelTabInfo.getMoreItems(
                NewPipe.getService(serviceId),
                listLinkHandler,
                nextPage
            )
        }
    }

    @JvmStatic
    fun getPlaylistInfo(
        serviceId: Int,
        url: String?,
        forceLoad: Boolean
    ): Single<PlaylistInfo> = checkCache(
        forceLoad,
        serviceId,
        url,
        InfoCache.Type.PLAYLIST,
        Single.fromCallable {
            PlaylistInfo.getInfo(NewPipe.getService(serviceId), url)
        }
    )

    @JvmStatic
    fun getMorePlaylistItems(
        serviceId: Int,
        url: String?,
        nextPage: Page
    ): Single<InfoItemsPage<StreamInfoItem>> {
        checkServiceId(serviceId)
        checkNotNull(url)
        return Single.fromCallable {
            PlaylistInfo.getMoreItems(
                NewPipe.getService(serviceId),
                url,
                nextPage
            )
        }
    }

    @JvmStatic
    fun getKioskInfo(
        serviceId: Int,
        url: String?,
        forceLoad: Boolean
    ): Single<KioskInfo> = checkCache(
        forceLoad,
        serviceId,
        url,
        InfoCache.Type.KIOSK,
        Single.fromCallable {
            KioskInfo.getInfo(NewPipe.getService(serviceId), url)
        }
    )

    @JvmStatic
    fun getMoreKioskItems(
        serviceId: Int,
        url: String?,
        nextPage: Page
    ): Single<InfoItemsPage<StreamInfoItem>> {
        checkServiceId(serviceId)
        checkNotNull(url)
        return Single.fromCallable {
            KioskInfo.getMoreItems(
                NewPipe.getService(serviceId),
                url,
                nextPage
            )
        }
    }

    // //////////////////////////////////////////////////////////////////////////
    // Cache
    // //////////////////////////////////////////////////////////////////////////

    /**
     * Check if we can load it from the cache (forceLoad parameter), if we can't,
     * load from the network (Single loadFromNetwork)
     * and put the results in the cache.
     *
     * @param <I>             the item type's class that extends {@link Info}
     * @param forceLoad       whether to force loading from the network instead of from the cache
     * @param serviceId       the service to load from
     * @param url             the URL to load
     * @param cacheType       the {@link InfoCache.Type} of the item
     * @param loadFromNetwork the {@link Single} to load the item from the network
     * @return a {@link Single} that loads the item
     */
    @Suppress("UNCHECKED_CAST")
    private fun <I : Info> checkCache(
        forceLoad: Boolean,
        serviceId: Int,
        url: String?,
        cacheType: InfoCache.Type,
        loadFromNetwork: Single<I>
    ): Single<I> {
        checkServiceId(serviceId)
        checkNotNull(url)

        val actualLoadFromNetwork = loadFromNetwork
            .doOnSuccess { info -> CACHE.putInfo(serviceId, url, info, cacheType) }

        if (forceLoad) {
            CACHE.removeInfo(serviceId, url, cacheType)
            return actualLoadFromNetwork
        }

        return loadFromCache<I>(serviceId, url, cacheType)
            .switchIfEmpty(actualLoadFromNetwork.toMaybe())
            .toSingle()
    }

    private fun <I : Info> loadFromCache(
        serviceId: Int,
        url: String,
        cacheType: InfoCache.Type
    ): Maybe<I> {
        checkServiceId(serviceId)
        return Maybe.defer { cacheLoad(serviceId, url, cacheType) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <I : Info> cacheLoad(
        serviceId: Int,
        url: String,
        cacheType: InfoCache.Type
    ): Maybe<I> {
        val info = CACHE.getFromKey(serviceId, url, cacheType) as I?
        logIfDebugging("loadFromCache() called, info > $info")
        return if (info != null) Maybe.just(info) else Maybe.empty()
    }

    private fun logIfDebugging(message: String) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, message)
        }
    }

    @JvmStatic
    fun isCached(serviceId: Int, url: String, cacheType: InfoCache.Type): Boolean {
        checkServiceId(serviceId)
        return CACHE.getFromKey(serviceId, url, cacheType) != null
    }

    // //////////////////////////////////////////////////////////////////////////
    // Utils
    // //////////////////////////////////////////////////////////////////////////

    /**
     * Formats the text contained in the meta info list as HTML and puts it into the text view,
     * while also making the separator visible. If the list is null or empty, or the user chose not
     * to see meta information, both the text view and the separator are hidden
     *
     * @param metaInfos         a list of meta information, can be null or empty
     * @param metaInfoTextView  the text view in which to show the formatted HTML
     * @param metaInfoSeparator another view to be shown or hidden accordingly to the text view
     * @param disposables       disposables created by the method are added here and their lifecycle
     *                          should be handled by the calling class
     */
    @JvmStatic
    fun showMetaInfoInTextView(
        metaInfos: List<MetaInfo>?,
        metaInfoTextView: TextView,
        metaInfoSeparator: View,
        disposables: CompositeDisposable
    ) {
        val context = metaInfoTextView.context

        if (shouldMetaInfoBeVisible(metaInfos, context)) {
            metaInfoTextView.visibility = View.VISIBLE
            metaInfoSeparator.visibility = View.VISIBLE
            TextLinkifier.fromHtml(
                metaInfoTextView,
                buildMetaInfosString(metaInfos),
                HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING,
                null,
                null,
                disposables,
                SET_LINK_MOVEMENT_METHOD
            )
        } else {
            metaInfoTextView.visibility = View.GONE
            metaInfoSeparator.visibility = View.GONE
        }
    }

    private fun shouldMetaInfoBeVisible(
        metaInfos: List<MetaInfo>?,
        context: Context
    ): Boolean {
        return !metaInfos.isNullOrEmpty() &&
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.show_meta_info_key), true)
    }

    private fun buildMetaInfosString(metaInfos: List<MetaInfo>?): String = buildString {
        metaInfos?.forEach { buildMetaInfo(it) }
    }

    private fun StringBuilder.buildMetaInfo(metaInfo: MetaInfo) {
        if (metaInfo.title.isNotEmpty()) {
            appendTag("b", metaInfo.title)
            append(DOT_SEPARATOR)
        }

        val content = metaInfo.content.content.trim().removeSuffix(".")
        append(content)

        var firstInfo = true
        for (urlInfo in getInfoUrls(metaInfo)) {
            if (firstInfo) {
                append(DOT_SEPARATOR)
                firstInfo = false
            } else {
                appendSelfClosingTag("br")
                appendSelfClosingTag("br")
            }

            addInfoUrlTag(urlInfo)
        }
    }

    private fun StringBuilder.addInfoUrlTag(urlInfo: URLInfo) {
        val attrs = HashMap<String, String>()
        attrs["href"] = urlInfo.url.toString()
        appendTag("a", capitalizeIfAllUppercase(urlInfo.text.trim()), attrs)
    }

    private data class URLInfo(val url: URL, val text: String)

    private fun getInfoUrls(metaInfo: MetaInfo): List<URLInfo> {
        return metaInfo.urls.zip(metaInfo.urlTexts) { url, text -> URLInfo(url, text) }
    }

    private fun StringBuilder.appendSelfClosingTag(tag: String) = append("<").append(tag).append("/>")

    private fun StringBuilder.appendTag(
        tag: String,
        content: String,
        attrs: Map<String, String> = emptyMap()
    ) {
        // Opening tag
        append("<").append(tag)
        if (attrs.isNotEmpty()) {
            // Attributes
            attrs.entries.joinTo(this, separator = " ", prefix = " ") { (key, value) ->
                """$key="$value""""
            }
        }
        append(">")

        // Content
        append(content)

        // Closing tag
        append("</").append(tag).append(">")
    }

    private fun capitalizeIfAllUppercase(text: String): String {
        return if (text.all { !it.isLowerCase() }) capitalize(text) else text
    }

    private fun capitalize(text: String): String {
        return if (text.isEmpty()) {
            text
        } else {
            text.substring(0, 1).uppercase(getDefault()) +
                text.substring(1).lowercase(getDefault())
        }
    }
}
