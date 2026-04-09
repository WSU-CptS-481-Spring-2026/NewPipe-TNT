package org.schabi.newpipe.info_list.holder

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.image.CoilHelper.loadPlaylistThumbnail

open class PlaylistMiniInfoItemHolder(
    infoItemBuilder: InfoItemBuilder,
    layoutId: Int,
    parent: ViewGroup?
) : InfoItemHolder(infoItemBuilder, layoutId, parent), View.OnClickListener, View.OnLongClickListener {

    // 1. 'by lazy' removes these from the constructor, dropping its complexity to near 0
    val itemThumbnailView: ImageView by lazy { itemView.findViewById(R.id.itemThumbnailView) }
    private val itemStreamCountView: TextView by lazy { itemView.findViewById(R.id.itemStreamCountView) }
    val itemTitleView: TextView by lazy { itemView.findViewById(R.id.itemTitleView) }
    val itemUploaderView: TextView by lazy { itemView.findViewById(R.id.itemUploaderView) }

    // We store the current item here so the click listeners can access it
    private var currentItem: PlaylistInfoItem? = null

    init {
        // Attach the listeners once during setup, rather than every time the item updates
        itemView.setOnClickListener(this)
        itemView.isLongClickable = true
        itemView.setOnLongClickListener(this)
    }

    constructor(
        infoItemBuilder: InfoItemBuilder,
        parent: ViewGroup?
    ) : this(infoItemBuilder, R.layout.list_playlist_mini_item, parent)

    override fun updateFromItem(
        infoItem: InfoItem?,
        historyRecordManager: HistoryRecordManager?
    ) {
        // Safe cast pattern
        val item = infoItem as? PlaylistInfoItem ?: return
        currentItem = item

        itemTitleView.text = item.name
        itemStreamCountView.text = Localization.localizeStreamCountMini(
            itemStreamCountView.context,
            item.streamCount
        )
        itemUploaderView.text = item.uploaderName

        loadPlaylistThumbnail(itemThumbnailView, item.thumbnails)
    }

    // 2. Implementing the click functions down here completely removes
    //    the nested lambda complexity from updateFromItem
    override fun onClick(v: View?) {
        currentItem?.let { itemBuilder.onPlaylistSelectedListener?.selected(it) }
    }

    override fun onLongClick(v: View?): Boolean {
        currentItem?.let { itemBuilder.onPlaylistSelectedListener?.held(it) }
        return true
    }
}