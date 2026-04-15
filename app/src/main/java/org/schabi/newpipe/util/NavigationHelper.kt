package org.schabi.newpipe.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.preference.PreferenceManager
import com.jakewharton.processphoenix.ProcessPhoenix
import java.util.Optional
import java.util.function.Function
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.NewPipeDatabase.close
import org.schabi.newpipe.R
import org.schabi.newpipe.RouterActivity
import org.schabi.newpipe.about.AboutActivity
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.download.DownloadActivity
import org.schabi.newpipe.error.ErrorUtil.Companion.showUiErrorSnackbar
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.LinkType
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.fragments.MainFragment
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.fragments.detail.VideoDetailFragment.Companion.getInstance
import org.schabi.newpipe.fragments.detail.VideoDetailFragment.Companion.getInstanceInCollapsedState
import org.schabi.newpipe.fragments.list.channel.ChannelFragment
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment
import org.schabi.newpipe.fragments.list.search.SearchFragment
import org.schabi.newpipe.ktx.findFragmentActivity
import org.schabi.newpipe.local.bookmark.BookmarkFragment
import org.schabi.newpipe.local.feed.FeedFragment.Companion.newInstance
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment
import org.schabi.newpipe.local.subscription.SubscriptionFragment
import org.schabi.newpipe.local.subscription.SubscriptionsImportFragment
import org.schabi.newpipe.player.PlayQueueActivity
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerIntentType
import org.schabi.newpipe.player.PlayerService
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.TimestampChangeData
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHolder.isPlaying
import org.schabi.newpipe.player.helper.PlayerHolder.type
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.settings.SettingsActivity
import org.schabi.newpipe.settings.SettingsV2Activity
import org.schabi.newpipe.util.external_communication.ShareUtils

object NavigationHelper {
    const val MAIN_FRAGMENT_TAG: String = "main_fragment_tag"
    const val SEARCH_FRAGMENT_TAG: String = "search_fragment_tag"

    private val TAG: String = NavigationHelper::class.java.getSimpleName()

    /*//////////////////////////////////////////////////////////////////////////
    // Players
    ////////////////////////////////////////////////////////////////////////// */
    /* INTENT */
    @JvmStatic
    fun <T> getPlayerIntent(
        context: Context,
        targetClazz: Class<T>,
        playQueue: PlayQueue?,
        playerIntentType: PlayerIntentType
    ): Intent {
        val cacheKey = Optional.ofNullable<PlayQueue?>(playQueue)
            .map<String?>(
                Function { queue: PlayQueue? ->
                    SerializedCache.getInstance().put<PlayQueue?>(queue!!, PlayQueue::class.java)
                }
            )
            .orElse(null)
        return Intent(context, targetClazz)
            .putExtra(Player.PLAY_QUEUE_KEY, cacheKey)
            .putExtra(Player.PLAYER_TYPE, PlayerType.MAIN)
            .putExtra(PlayerService.SHOULD_START_FOREGROUND_EXTRA, true)
            .putExtra(Player.PLAYER_INTENT_TYPE, playerIntentType)
    }

    @JvmStatic
    fun getPlayerTimestampIntent(
        context: Context,
        data: TimestampChangeData
    ): Intent {
        return Intent(context, PlayerService::class.java)
            .putExtra(Player.PLAYER_INTENT_TYPE, PlayerIntentType.TimestampChange)
            .putExtra(Player.PLAYER_INTENT_DATA, data)
    }

    fun <T> getPlayerEnqueueNextIntent(
        context: Context,
        targetClazz: Class<T>,
        playQueue: PlayQueue?
    ): Intent {
        return getPlayerIntent<T>(
            context,
            targetClazz,
            playQueue,
            PlayerIntentType.EnqueueNext
        ) // see comment in `getPlayerEnqueueIntent` as to why `resumePlayback` is false
            .putExtra(Player.RESUME_PLAYBACK, false)
    }

    /* PLAY */
    fun playOnMainPlayer(
        activity: AppCompatActivity,
        playQueue: PlayQueue
    ) {
        val item = playQueue.item
        if (item != null) {
            openVideoDetailFragment(
                activity,
                activity.getSupportFragmentManager(),
                item.serviceId,
                item.url,
                item.title,
                playQueue,
                false
            )
        }
    }

    @JvmStatic
    fun playOnMainPlayer(
        context: Context,
        playQueue: PlayQueue,
        switchingPlayers: Boolean
    ) {
        val item = playQueue.item
        if (item != null) {
            openVideoDetail(
                context,
                item.serviceId,
                item.url,
                item.title,
                playQueue,
                switchingPlayers
            )
        }
    }

    @JvmStatic
    fun playOnPopupPlayer(
        context: Context,
        queue: PlayQueue?,
        resumePlayback: Boolean
    ) {
        if (!PermissionHelper.isPopupEnabledElseAsk(context)) {
            return
        }

        Toast.makeText(context, R.string.popup_playing_toast, Toast.LENGTH_SHORT).show()

        val intent = NavigationHelper.getPlayerIntent<PlayerService>(
            context,
            PlayerService::class.java,
            queue,
            PlayerIntentType.AllOthers
        )
            .putExtra(Player.PLAYER_TYPE, PlayerType.POPUP)
            .putExtra(Player.RESUME_PLAYBACK, resumePlayback)
        ContextCompat.startForegroundService(context, intent)
    }

    @JvmStatic
    fun playOnBackgroundPlayer(
        context: Context,
        queue: PlayQueue?,
        resumePlayback: Boolean
    ) {
        Toast.makeText(context, R.string.background_player_playing_toast, Toast.LENGTH_SHORT)
            .show()

        val intent = NavigationHelper.getPlayerIntent<PlayerService>(
            context,
            PlayerService::class.java,
            queue,
            PlayerIntentType.AllOthers
        )
            .putExtra(Player.PLAYER_TYPE, PlayerType.AUDIO)
            .putExtra(Player.RESUME_PLAYBACK, resumePlayback)
        ContextCompat.startForegroundService(context, intent)
    }

    /* ENQUEUE */
    fun enqueueOnPlayer(
        context: Context,
        queue: PlayQueue?,
        playerType: PlayerType?
    ) {
        if (playerType == PlayerType.POPUP && !PermissionHelper.isPopupEnabledElseAsk(context)) {
            return
        }

        Toast.makeText(context, R.string.enqueued, Toast.LENGTH_SHORT).show()

        // when enqueueing `resumePlayback` is always `false` since:
        // - if there is a video already playing, the value of `resumePlayback` just doesn't make
        //   any difference.
        // - if there is nothing already playing, it is useful for the enqueue action to have a
        //   slightly different behaviour than the normal play action: the latter resumes playback,
        //   the former doesn't. (note that enqueue can be triggered when nothing is playing only
        //   by long pressing the video detail fragment, playlist or channel controls
        val intent = NavigationHelper.getPlayerIntent<PlayerService>(
            context,
            PlayerService::class.java,
            queue,
            PlayerIntentType.Enqueue
        )
            .putExtra(Player.RESUME_PLAYBACK, false)
            .putExtra(Player.PLAYER_TYPE, playerType)
        ContextCompat.startForegroundService(context, intent)
    }

    @JvmStatic
    fun enqueueOnPlayer(context: Context, queue: PlayQueue?) {
        var playerType = type
        if (playerType == null) {
            Log.e(TAG, "Enqueueing but no player is open; defaulting to background player")
            playerType = PlayerType.AUDIO
        }

        enqueueOnPlayer(context, queue, playerType)
    }

    /* ENQUEUE NEXT */
    @JvmStatic
    fun enqueueNextOnPlayer(context: Context, queue: PlayQueue?) {
        var playerType = type
        if (playerType == null) {
            Log.e(TAG, "Enqueueing next but no player is open; defaulting to background player")
            playerType = PlayerType.AUDIO
        }
        Toast.makeText(context, R.string.enqueued_next, Toast.LENGTH_SHORT).show()
        val intent = NavigationHelper.getPlayerEnqueueNextIntent<PlayerService>(
            context,
            PlayerService::class.java,
            queue
        )
            .putExtra(Player.PLAYER_TYPE, playerType)
        ContextCompat.startForegroundService(context, intent)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // External Players
    ////////////////////////////////////////////////////////////////////////// */
    @JvmStatic
    fun playOnExternalAudioPlayer(
        context: Context,
        info: StreamInfo
    ) {
        val audioStreams = info.getAudioStreams()
        if (audioStreams == null || audioStreams.isEmpty()) {
            Toast.makeText(context, R.string.audio_streams_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val audioStreamsForExternalPlayers =
            ListHelper.getUrlAndNonTorrentStreams<AudioStream?>(audioStreams)
        if (audioStreamsForExternalPlayers.isEmpty()) {
            Toast.makeText(
                context,
                R.string.no_audio_streams_available_for_external_players,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val index = ListHelper.getDefaultAudioFormat(context, audioStreamsForExternalPlayers)
        val audioStream = audioStreamsForExternalPlayers.get(index)

        playOnExternalPlayer(context, info.getName(), info.getUploaderName(), audioStream)
    }

    @JvmStatic
    fun playOnExternalVideoPlayer(
        context: Context,
        info: StreamInfo
    ) {
        val videoStreams = info.getVideoStreams()
        if (videoStreams == null || videoStreams.isEmpty()) {
            Toast.makeText(context, R.string.video_streams_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val videoStreamsForExternalPlayers =
            ListHelper.getSortedStreamVideosList(
                context,
                ListHelper.getUrlAndNonTorrentStreams<VideoStream?>(videoStreams),
                null,
                false,
                false
            )
        if (videoStreamsForExternalPlayers.isEmpty()) {
            Toast.makeText(
                context,
                R.string.no_video_streams_available_for_external_players,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val index = ListHelper.getDefaultResolutionIndex(
            context,
            videoStreamsForExternalPlayers
        )

        val videoStream = videoStreamsForExternalPlayers.get(index)
        playOnExternalPlayer(context, info.getName(), info.getUploaderName(), videoStream)
    }

    fun playOnExternalPlayer(
        context: Context,
        name: String?,
        artist: String?,
        stream: Stream
    ) {
        if (!stream.isUrl() || stream.getDeliveryMethod() == DeliveryMethod.TORRENT) {
            Toast.makeText(
                context,
                R.string.selected_stream_external_player_not_supported,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val mimeType = getMimeType(stream) ?: return

        val intent = Intent()
        intent.setAction(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.parse(stream.getContent()), mimeType)
        intent.putExtra(Intent.EXTRA_TITLE, name)
        intent.putExtra("title", name)
        intent.putExtra("artist", artist)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        resolveActivityOrAskToInstall(context, intent)
    }

    private fun getMimeType(stream: Stream): String? {
        return when (stream.getDeliveryMethod()) {
            DeliveryMethod.PROGRESSIVE_HTTP -> when {
                stream.getFormat() != null -> stream.getFormat()!!.getMimeType()
                stream is AudioStream -> "audio/*"
                stream is VideoStream -> "video/*"
                else -> null
            }

            DeliveryMethod.HLS -> "application/x-mpegURL"

            DeliveryMethod.DASH -> "application/dash+xml"

            DeliveryMethod.SS -> "application/vnd.ms-sstr+xml"

            else -> ""
        }
    }

    fun resolveActivityOrAskToInstall(
        context: Context,
        intent: Intent
    ) {
        if (!ShareUtils.tryOpenIntentInApp(context, intent)) {
            if (context is Activity) {
                AlertDialog.Builder(context)
                    .setMessage(R.string.no_player_found)
                    .setPositiveButton(
                        R.string.install,
                        DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                            ShareUtils.installApp(
                                context,
                                context.getString(R.string.vlc_package)
                            )
                        }
                    )
                    .setNegativeButton(
                        R.string.cancel,
                        DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                            Log.i(
                                "NavigationHelper",
                                "You unlocked a secret unicorn."
                            )
                        }
                    )
                    .show()
            } else {
                Toast.makeText(context, R.string.no_player_found_toast, Toast.LENGTH_LONG).show()
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Through FragmentManager
    ////////////////////////////////////////////////////////////////////////// */
    @SuppressLint("CommitTransaction")
    private fun defaultTransaction(fragmentManager: FragmentManager): FragmentTransaction {
        return fragmentManager.beginTransaction()
            .setCustomAnimations(
                R.animator.custom_fade_in,
                R.animator.custom_fade_out,
                R.animator.custom_fade_in,
                R.animator.custom_fade_out
            )
    }

    @JvmStatic
    fun gotoMainFragment(fragmentManager: FragmentManager) {
        val popped = fragmentManager.popBackStackImmediate(MAIN_FRAGMENT_TAG, 0)
        if (!popped) {
            openMainFragment(fragmentManager)
        }
    }

    @JvmStatic
    fun openMainFragment(fragmentManager: FragmentManager) {
        InfoCache.getInstance().trimCache()

        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, MainFragment())
            .addToBackStack(MAIN_FRAGMENT_TAG)
            .commit()
    }

    @JvmStatic
    fun tryGotoSearchFragment(fragmentManager: FragmentManager): Boolean {
        if (MainActivity.DEBUG) {
            for (i in 0..<fragmentManager.getBackStackEntryCount()) {
                Log.d(
                    "NavigationHelper",
                    (
                        "tryGoToSearchFragment() [" + i + "]" +
                            " = [" + fragmentManager.getBackStackEntryAt(i) + "]"
                        )
                )
            }
        }

        return fragmentManager.popBackStackImmediate(SEARCH_FRAGMENT_TAG, 0)
    }

    @JvmStatic
    fun openSearchFragment(
        fragmentManager: FragmentManager,
        serviceId: Int,
        searchString: String?
    ) {
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, SearchFragment.getInstance(serviceId, searchString))
            .addToBackStack(SEARCH_FRAGMENT_TAG)
            .commit()
    }

    fun expandMainPlayer(context: Context) {
        context.sendBroadcast(Intent(VideoDetailFragment.ACTION_SHOW_MAIN_PLAYER))
    }

    @JvmStatic
    fun sendPlayerStartedEvent(context: Context) {
        context.sendBroadcast(Intent(VideoDetailFragment.ACTION_PLAYER_STARTED))
    }

    @JvmStatic
    fun showMiniPlayer(fragmentManager: FragmentManager) {
        val instance = getInstanceInCollapsedState()
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_player_holder, instance)
            .runOnCommit(Runnable { sendPlayerStartedEvent(instance.requireActivity()) })
            .commitAllowingStateLoss()
    }

    @JvmStatic
    fun openVideoDetailFragment(
        context: Context,
        fragmentManager: FragmentManager,
        serviceId: Int,
        url: String?,
        title: String,
        playQueue: PlayQueue?,
        switchingPlayers: Boolean
    ) {
        val autoPlay: Boolean
        val playerType = type
        if (playerType == null) {
            // no player open
            autoPlay = PlayerHelper.isAutoplayAllowedByUser(context)
        } else if (switchingPlayers) {
            // switching player to main player
            autoPlay = isPlaying // keep play/pause state
        } else if (playerType == PlayerType.MAIN) {
            // opening new stream while already playing in main player
            autoPlay = PlayerHelper.isAutoplayAllowedByUser(context)
        } else {
            // opening new stream while already playing in another player
            autoPlay = false
        }

        val onVideoDetailFragmentReady =
            object : RunnableWithVideoDetailFragment {
                override fun run(detailFragment: VideoDetailFragment?) {
                    expandMainPlayer(detailFragment!!.requireActivity())
                    detailFragment.setAutoPlay(autoPlay)
                    if (switchingPlayers) {
                        // Situation when user switches from players to main player. All needed data is
                        // here, we can start watching (assuming newQueue equals playQueue).
                        // Starting directly in fullscreen if the previous player type was popup.
                        detailFragment.openVideoPlayer(
                            playerType == PlayerType.POPUP ||
                                PlayerHelper.isStartMainPlayerFullscreenEnabled(context)
                        )
                    } else {
                        detailFragment.selectAndLoadVideo(serviceId, url!!, title, playQueue)
                    }
                    detailFragment.scrollToTop()
                }
            }

        val fragment = fragmentManager.findFragmentById(R.id.fragment_player_holder)
        if (fragment is VideoDetailFragment && fragment.isVisible()) {
            onVideoDetailFragmentReady.run(fragment)
        } else {
            // Specify no url here, otherwise the VideoDetailFragment will start loading the
            // stream automatically if it's the first time it is being opened, but then
            // onVideoDetailFragmentReady will kick in and start another loading process.
            // See VideoDetailFragment.wasCleared() and its usage in doInitialLoadLogic().
            val instance = getInstance(serviceId, null, title, playQueue)
            instance.setAutoPlay(autoPlay)

            defaultTransaction(fragmentManager)
                .replace(R.id.fragment_player_holder, instance)
                .runOnCommit(Runnable { onVideoDetailFragmentReady.run(instance) })
                .commit()
        }
    }

    @JvmStatic
    fun openChannelFragment(
        fragmentManager: FragmentManager,
        serviceId: Int,
        url: String?,
        name: String
    ) {
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, ChannelFragment.getInstance(serviceId, url, name))
            .addToBackStack(null)
            .commit()
    }

    @JvmStatic
    fun openChannelFragment(
        activity: FragmentActivity,
        item: StreamInfoItem,
        uploaderUrl: String?
    ) {
        // For some reason `getParentFragmentManager()` doesn't work, but this does.
        openChannelFragment(
            activity.getSupportFragmentManager(),
            item.getServiceId(),
            uploaderUrl,
            item.getUploaderName()
        )
    }

    /**
     * Opens the comment author channel fragment, if the [CommentsInfoItem.getUploaderUrl]
     * of `comment` is non-null. Shows a UI-error snackbar if something goes wrong.
     *
     * @param context the context to use for opening the fragment
     * @param comment the comment whose uploader/author will be opened
     */
    fun openCommentAuthorIfPresent(
        context: Context,
        comment: CommentsInfoItem
    ) {
        if (TextUtils.isEmpty(comment.getUploaderUrl())) {
            return
        }
        try {
            val activity = context.findFragmentActivity()
            openChannelFragment(
                activity.getSupportFragmentManager(),
                comment.getServiceId(),
                comment.getUploaderUrl(),
                comment.getUploaderName()
            )
        } catch (e: Exception) {
            showUiErrorSnackbar(context, "Opening channel fragment", e)
        }
    }

    @JvmStatic
    fun openPlaylistFragment(
        fragmentManager: FragmentManager,
        serviceId: Int,
        url: String?,
        name: String
    ) {
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, PlaylistFragment.getInstance(serviceId, url, name))
            .addToBackStack(null)
            .commit()
    }

    @JvmStatic
    @JvmOverloads
    fun openFeedFragment(
        fragmentManager: FragmentManager,
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
        groupName: String? = null
    ) {
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, newInstance(groupId, groupName))
            .addToBackStack(null)
            .commit()
    }

    @JvmStatic
    fun openBookmarksFragment(fragmentManager: FragmentManager) {
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, BookmarkFragment())
            .addToBackStack(null)
            .commit()
    }

    @JvmStatic
    fun openSubscriptionFragment(fragmentManager: FragmentManager) {
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, SubscriptionFragment())
            .addToBackStack(null)
            .commit()
    }

    @JvmStatic
    @Throws(ExtractionException::class)
    fun openKioskFragment(
        fragmentManager: FragmentManager,
        serviceId: Int,
        kioskId: String?
    ) {
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, KioskFragment.getInstance(serviceId, kioskId))
            .addToBackStack(null)
            .commit()
    }

    @JvmStatic
    fun openLocalPlaylistFragment(
        fragmentManager: FragmentManager,
        playlistId: Long,
        name: String?
    ) {
        defaultTransaction(fragmentManager)
            .replace(
                R.id.fragment_holder,
                LocalPlaylistFragment.getInstance(
                    playlistId,
                    if (name == null) "" else name
                )
            )
            .addToBackStack(null)
            .commit()
    }

    @JvmStatic
    fun openStatisticFragment(fragmentManager: FragmentManager) {
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, StatisticsPlaylistFragment())
            .addToBackStack(null)
            .commit()
    }

    fun openSubscriptionsImportFragment(
        fragmentManager: FragmentManager,
        serviceId: Int
    ) {
        defaultTransaction(fragmentManager)
            .replace(R.id.fragment_holder, SubscriptionsImportFragment.getInstance(serviceId))
            .addToBackStack(null)
            .commit()
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Through Intents
    ////////////////////////////////////////////////////////////////////////// */
    @JvmStatic
    fun openSearch(
        context: Context,
        serviceId: Int,
        searchString: String?
    ) {
        val mIntent = Intent(context, MainActivity::class.java)
        mIntent.putExtra(KEY_SERVICE_ID, serviceId)
        mIntent.putExtra(KEY_SEARCH_STRING, searchString)
        mIntent.putExtra(KEY_OPEN_SEARCH, true)
        context.startActivity(mIntent)
    }

    @JvmStatic
    fun openVideoDetail(
        context: Context,
        serviceId: Int,
        url: String?,
        title: String,
        playQueue: PlayQueue?,
        switchingPlayers: Boolean
    ) {
        val intent = getStreamIntent(context, serviceId, url, title)
            .putExtra(VideoDetailFragment.KEY_SWITCHING_PLAYERS, switchingPlayers)

        if (playQueue != null) {
            val cacheKey =
                SerializedCache.getInstance().put<PlayQueue?>(playQueue, PlayQueue::class.java)
            if (cacheKey != null) {
                intent.putExtra(Player.PLAY_QUEUE_KEY, cacheKey)
            }
        }
        context.startActivity(intent)
    }

    /**
     * Opens [ChannelFragment].
     * Use this instead of [.openChannelFragment]
     * when no fragments are used / no FragmentManager is available.
     * @param context
     * @param serviceId
     * @param url
     * @param title
     */
    @JvmStatic
    fun openChannelFragmentUsingIntent(
        context: Context,
        serviceId: Int,
        url: String?,
        title: String
    ) {
        val intent = getOpenIntent(
            context,
            url,
            serviceId,
            LinkType.CHANNEL
        )
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(KEY_TITLE, title)

        context.startActivity(intent)
    }

    @JvmStatic
    fun openMainActivity(context: Context) {
        val mIntent = Intent(context, MainActivity::class.java)
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(mIntent)
    }

    @JvmStatic
    fun openRouterActivity(context: Context, url: String?) {
        val mIntent = Intent(context, RouterActivity::class.java)
        mIntent.setData(Uri.parse(url))
        context.startActivity(mIntent)
    }

    @JvmStatic
    fun openAbout(context: Context) {
        val intent = Intent(context, AboutActivity::class.java)
        context.startActivity(intent)
    }

    @JvmStatic
    fun openSettings(context: Context) {
        val settingsClass: Class<*> = if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(
                    Localization.compatGetString(
                        context,
                        R.string.settings_layout_redesign_key
                    ),
                    false
                )
        ) {
            SettingsV2Activity::class.java
        } else {
            SettingsActivity::class.java
        }

        val intent = Intent(context, settingsClass)
        context.startActivity(intent)
    }

    @JvmStatic
    fun openDownloads(activity: Activity) {
        if (PermissionHelper.checkStoragePermissions(
                activity,
                PermissionHelper.DOWNLOADS_REQUEST_CODE
            )
        ) {
            val intent = Intent(activity, DownloadActivity::class.java)
            activity.startActivity(intent)
        }
    }

    @JvmStatic
    fun getPlayQueueActivityIntent(context: Context?): Intent {
        val intent = Intent(context, PlayQueueActivity::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    fun openPlayQueue(context: Context) {
        val intent = Intent(context, PlayQueueActivity::class.java)
        context.startActivity(intent)
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Link handling
    ////////////////////////////////////////////////////////////////////////// */
    private fun getOpenIntent(
        context: Context?,
        url: String?,
        serviceId: Int,
        type: LinkType?
    ): Intent {
        val mIntent = Intent(context, MainActivity::class.java)
        mIntent.putExtra(KEY_SERVICE_ID, serviceId)
        mIntent.putExtra(KEY_URL, url)
        mIntent.putExtra(KEY_LINK_TYPE, type)
        return mIntent
    }

    @JvmStatic
    @Throws(ExtractionException::class)
    fun getIntentByLink(context: Context?, url: String?): Intent {
        return getIntentByLink(context, NewPipe.getServiceByUrl(url), url)
    }

    @JvmStatic
    @Throws(ExtractionException::class)
    fun getIntentByLink(
        context: Context?,
        service: StreamingService,
        url: String?
    ): Intent {
        val linkType = service.getLinkTypeByUrl(url)

        if (linkType == LinkType.NONE) {
            throw ExtractionException(
                (
                    "Url not known to service. service=" + service +
                        " url=" + url
                    )
            )
        }

        return getOpenIntent(context, url, service.getServiceId(), linkType)
    }

    fun getChannelIntent(
        context: Context?,
        serviceId: Int,
        url: String?
    ): Intent {
        return getOpenIntent(context, url, serviceId, LinkType.CHANNEL)
    }

    fun getStreamIntent(
        context: Context?,
        serviceId: Int,
        url: String?,
        title: String?
    ): Intent {
        return getOpenIntent(context, url, serviceId, LinkType.STREAM)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(KEY_TITLE, title)
    }

    /**
     * Finish this `Activity` as well as all `Activities` running below it
     * and then start `MainActivity`.
     *
     * @param activity the activity to finish
     */
    @JvmStatic
    fun restartApp(activity: Activity) {
        close()

        ProcessPhoenix.triggerRebirth(activity.getApplicationContext())
    }

    private interface RunnableWithVideoDetailFragment {
        fun run(detailFragment: VideoDetailFragment?)
    }
}
