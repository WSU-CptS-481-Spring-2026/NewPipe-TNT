package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import android.icu.text.CompactDecimalFormat
import android.os.Build
import android.text.BidiFormatter
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.math.MathUtils
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.stream.Collectors
import kotlin.math.max
import org.ocpsoft.prettytime.PrettyTime
import org.ocpsoft.prettytime.units.Decade
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.util.Localization.likeCount

object Localization {
    val TAG: String = Localization::class.java.toString()
    const val DOT_SEPARATOR: String = " • "
    var prettyTime: PrettyTime? = null

    /**
     * Gets a string like you would normally do with {@link Context#getString}, except that when
     * Context is not an AppCompatActivity the correct locale is still used. The latter step uses
     * {@link ContextCompat#getString}, which might fail if the Locale system service is not
     * available (e.g. inside of Compose previews). In that case this method falls back to plain old
     * {@link Context#getString}.
     * <p>This method also supports format args (see {@link #compatGetString(Context, int,
     * Object...)}, unlike {@link ContextCompat#getString}.</p>
     *
     * @param context any Android context, even the App context
     * @param resId the string resource to resolve
     * @return the resolved string
     */
    fun compatGetString(context: Context, @StringRes resId: Int): String {
        return try {
            ContextCompat.getString(context, resId)
        } catch (e: Throwable) {
            context.getString(resId)
        }
    }

    /**
     * @see .compatGetString
     * @param context any Android context, even the App context
     * @param resId the string resource to resolve
     * @param formatArgs the formatting arguments
     * @return the resolved string
     */
    fun compatGetString(
        context: Context,
        @StringRes resId: Int,
        vararg formatArgs: Any?
    ): String {
        return try {
            // ContextCompat.getString() with formatArgs does not exist, so we just
            // replicate its source code but with formatArgs
            ContextCompat.getContextForLanguage(context).getString(resId, *formatArgs)
        } catch (e: Throwable) {
            context.getString(resId, *formatArgs)
        }
    }

    fun concatenateStrings(vararg strings: String?): String {
        return Localization.concatenateStrings(DOT_SEPARATOR, *strings)
    }

    fun concatenateStrings(delimiter: String, vararg strings: String?): String {
        return strings.filterNot { it.isNullOrEmpty() }.joinToString(delimiter)
    }

    /**
     * Localize a user name like `@foobar`.
     *
     * Will correctly handle right-to-left usernames by using a [BidiFormatter].
     * For right-to-left usernames, it will put the @ on the right side to read more naturally.
     *
     * @param plainName username, with an optional leading `@`
     * @return a usernames that can include RTL-characters
     */
    fun localizeUserName(plainName: String?): String {
        return BidiFormatter.getInstance().unicodeWrap(plainName)
    }

    fun getPreferredLocalization(
        context: Context
    ): org.schabi.newpipe.extractor.localization.Localization {
        return org.schabi.newpipe.extractor.localization.Localization
            .fromLocale(Localization.getPreferredLocale(context) ?: Locale.getDefault())
    }

    fun getPreferredContentCountry(context: Context): ContentCountry {
        val contentCountry = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(
                context.getString(R.string.content_country_key),
                context.getString(R.string.default_localization_key)
            )
        if (contentCountry == context.getString(R.string.default_localization_key)) {
            return ContentCountry(Locale.getDefault().getCountry())
        }
        return ContentCountry(contentCountry!!)
    }

    fun getPreferredLocale(context: Context): Locale? {
        return Localization.getLocaleFromPrefs(context, R.string.content_language_key)
    }

    fun getAppLocale(): Locale {
        val customLocale = AppCompatDelegate.getApplicationLocales().get(0)
        return customLocale ?: Locale.getDefault()
    }

    fun localizeNumber(number: Long): String {
        return localizeNumber(number.toDouble())
    }

    fun localizeNumber(number: Double): String {
        return NumberFormat.getInstance(getAppLocale()).format(number)
    }

    fun formatDate(offsetDateTime: OffsetDateTime): String {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(getAppLocale())
            .format(offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()))
    }

    fun localizeUploadDate(
        context: Context,
        offsetDateTime: OffsetDateTime
    ): String {
        return context.getString(R.string.upload_date_text, formatDate(offsetDateTime))
    }

    fun localizeViewCount(context: Context, viewCount: Long): String {
        return Localization.getQuantity(
            context,
            R.plurals.views,
            R.string.no_views,
            viewCount,
            localizeNumber(viewCount)
        )
    }

    fun localizeStreamCount(
        context: Context,
        streamCount: Long
    ): String {
        when (streamCount.toInt()) {
            ListExtractor.ITEM_COUNT_UNKNOWN.toInt() -> return ""

            ListExtractor.ITEM_COUNT_INFINITE.toInt() -> return context.getString(R.string.infinite_videos)

            ListExtractor.ITEM_COUNT_MORE_THAN_100.toInt() -> return context.getString(R.string.more_than_100_videos)

            else -> return getQuantity(
                context,
                R.plurals.videos,
                R.string.no_videos,
                streamCount,
                localizeNumber(streamCount)
            )
        }
    }

    fun localizeStreamCountMini(
        context: Context,
        streamCount: Long
    ): String {
        when (streamCount.toInt()) {
            ListExtractor.ITEM_COUNT_UNKNOWN.toInt() -> return ""
            ListExtractor.ITEM_COUNT_INFINITE.toInt() -> return context.getString(R.string.infinite_videos_mini)
            ListExtractor.ITEM_COUNT_MORE_THAN_100.toInt() -> return context.getString(R.string.more_than_100_videos_mini)
            else -> return streamCount.toString()
        }
    }

    fun localizeWatchingCount(
        context: Context,
        watchingCount: Long
    ): String {
        return getQuantity(
            context,
            R.plurals.watching,
            R.string.no_one_watching,
            watchingCount,
            localizeNumber(watchingCount)
        )
    }

    fun shortCount(context: Context, count: Long): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            CompactDecimalFormat.getInstance(
                getAppLocale(),
                CompactDecimalFormat.CompactStyle.SHORT
            ).format(count)
        } else {
            when {
                count >= 1000000000 -> getLocalizedShortValue(
                    context,
                    count,
                    1000000000,
                    R.string.short_billion
                )

                count >= 1000000 -> getLocalizedShortValue(
                    context,
                    count,
                    1000000,
                    R.string.short_million
                )

                count >= 1000 -> getLocalizedShortValue(
                    context,
                    count,
                    1000,
                    R.string.short_thousand
                )

                else -> localizeNumber(count.toDouble())
            }
        }
    }

    private fun getLocalizedShortValue(
        context: Context,
        value: Long,
        countUnit: Long,
        @StringRes unit: Int
    ): String {
        val shortenedValue = value.toDouble() / countUnit
        val scale = if (shortenedValue >= 100) 0 else 1
        return context.getString(
            unit,
            localizeNumber(round(shortenedValue, scale))
        )
    }

    fun listeningCount(context: Context, listeningCount: Long): String {
        return getQuantity(
            context,
            R.plurals.listening,
            R.string.no_one_listening,
            listeningCount,
            shortCount(context, listeningCount)
        )
    }

    fun shortWatchingCount(
        context: Context,
        watchingCount: Long
    ): String {
        return getQuantity(
            context,
            R.plurals.watching,
            R.string.no_one_watching,
            watchingCount,
            shortCount(context, watchingCount)
        )
    }

    fun shortViewCount(context: Context, viewCount: Long): String {
        return getQuantity(
            context,
            R.plurals.views,
            R.string.no_views,
            viewCount,
            shortCount(context, viewCount)
        )
    }

    fun shortSubscriberCount(
        context: Context,
        subscriberCount: Long
    ): String {
        return getQuantity(
            context,
            R.plurals.subscribers,
            R.string.no_subscribers,
            subscriberCount,
            shortCount(context, subscriberCount)
        )
    }

    fun downloadCount(context: Context, downloadCount: Int): String {
        return getQuantity(
            context,
            R.plurals.download_finished_notification,
            0,
            downloadCount.toLong(),
            shortCount(context, downloadCount.toLong())
        )
    }

    fun deletedDownloadCount(
        context: Context,
        deletedCount: Int
    ): String {
        return getQuantity(
            context,
            R.plurals.deleted_downloads_toast,
            0,
            deletedCount.toLong(),
            shortCount(context, deletedCount.toLong())
        )
    }

    /**
     * @param context the Android context
     * @param likeCount the like count, possibly negative if unknown
     * @return if `likeCount` is smaller than `0`, the string `"-"`, otherwise
     * the result of calling [.shortCount] on the like count
     */
    fun likeCount(context: Context, likeCount: Int): String {
        return if (likeCount < 0) {
            "-"
        } else {
            shortCount(context, likeCount.toLong())
        }
    }

    /**
     * Get a readable text for a duration in the format `hours:minutes:seconds`.
     *
     * @param duration the duration in seconds
     * @return a formatted duration String or `00:00` if the duration is zero.
     */
    fun getDurationString(duration: Long): String {
        return DateUtils.formatElapsedTime(max(duration, 0))
    }

    /**
     * Get a readable text for a duration in the format `hours:minutes:seconds+`. If the given
     * duration is incomplete, a plus is appended to the duration string.
     *
     * @param duration the duration in seconds
     * @param isDurationComplete whether the given duration is complete or whether info is missing
     * @param showDurationPrefix whether the duration-prefix shall be shown
     * @return a formatted duration String or `00:00` if the duration is zero.
     */
    fun getDurationString(
        duration: Long,
        isDurationComplete: Boolean,
        showDurationPrefix: Boolean
    ): String {
        val output = getDurationString(duration)
        val durationPrefix = if (showDurationPrefix) "⏱ " else ""
        val durationPostfix = if (isDurationComplete) "" else "+"
        return durationPrefix + output + durationPostfix
    }

    /**
     * Localize an amount of seconds into a human readable string.
     *
     *
     * The seconds will be converted to the closest whole time unit.
     *
     * For example, 60 seconds would give "1 minute", 119 would also give "1 minute".
     *
     * @param context        used to get plurals resources.
     * @param durationInSecs an amount of seconds.
     * @return duration in a human readable string.
     */
    fun localizeDuration(
        context: Context,
        durationInSecs: Int
    ): String {
        require(durationInSecs >= 0) { "duration can not be negative" }

        val days = getDays(durationInSecs)
        val hours = getHours(durationInSecs)
        val minutes = getMinutes(durationInSecs)
        val seconds = getSeconds(durationInSecs)

        val resources = context.resources

        return when {
            days > 0 -> resources.getQuantityString(R.plurals.days, days, days)
            hours > 0 -> resources.getQuantityString(R.plurals.hours, hours, hours)
            minutes > 0 -> resources.getQuantityString(R.plurals.minutes, minutes, minutes)
            else -> resources.getQuantityString(R.plurals.seconds, seconds, seconds)
        }
    }

    private const val DAY_IN_SECONDS = 24 * 60 * 60
    private const val HOUR_IN_SECONDS = 60 * 60
    private const val MINUTE_IN_SECONDS = 60

    private fun getDays(durationInSecs: Int): Int = durationInSecs / DAY_IN_SECONDS
    private fun getHours(durationInSecs: Int): Int = durationInSecs % DAY_IN_SECONDS / HOUR_IN_SECONDS
    private fun getMinutes(durationInSecs: Int): Int = durationInSecs % HOUR_IN_SECONDS / MINUTE_IN_SECONDS
    private fun getSeconds(durationInSecs: Int): Int = durationInSecs % MINUTE_IN_SECONDS

    /**
     * Get the localized name of an audio track.
     *
     *
     * Examples of results returned by this method:
     *
     *  * English (original)
     *  * English (descriptive)
     *  * Spanish (Spain) (dubbed)
     *
     *
     * @param context the context used to get the app language
     * @param track   an [AudioStream] of the track
     * @return the localized name of the audio track
     */
    fun audioTrackName(context: Context, track: AudioStream): String? {
        val name = getTrackName(context, track)

        if (track.audioTrackType != null) {
            val trackType = audioTrackType(context, track.audioTrackType!!)
            return context.getString(R.string.audio_track_name, name, trackType)
        }

        return name
    }

    private fun getTrackName(context: Context, track: AudioStream): String? {
        return if (track.audioLocale != null) {
            track.audioLocale!!.displayName
        } else {
            track.audioTrackName ?: context.getString(R.string.unknown_audio_track)
        }
    }

    private fun audioTrackType(
        context: Context,
        trackType: AudioTrackType
    ): String {
        return when (trackType) {
            AudioTrackType.ORIGINAL -> context.getString(R.string.audio_track_type_original)
            AudioTrackType.DUBBED -> context.getString(R.string.audio_track_type_dubbed)
            AudioTrackType.DESCRIPTIVE -> context.getString(R.string.audio_track_type_descriptive)
            AudioTrackType.SECONDARY -> context.getString(R.string.audio_track_type_secondary)
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Pretty Time
    ////////////////////////////////////////////////////////////////////////// */
    fun initPrettyTime(time: PrettyTime) {
        prettyTime = time
        // Do not use decades as YouTube doesn't either.
        prettyTime!!.removeUnit<Decade?>(Decade::class.java)
    }

    fun resolvePrettyTime(): PrettyTime {
        return PrettyTime(getAppLocale())
    }

    fun relativeTime(offsetDateTime: OffsetDateTime): String? {
        return prettyTime!!.formatUnrounded(offsetDateTime)
    }

    /**
     * @param context the Android context; if `null` then even if in debug mode and the
     * setting is enabled, `textual` will not be shown next to `parsed`
     * @param parsed  the textual date or time ago parsed by NewPipeExtractor, or `null` if
     * the extractor could not parse it
     * @param textual the original textual date or time ago string as provided by services
     * @return [.relativeTime] is used if `parsed != null`, otherwise
     * `textual` is returned. If in debug mode, `context != null`,
     * `parsed != null` and the relevant setting is enabled, `textual` will
     * be appended to the returned string for debugging purposes.
     */
    fun relativeTimeOrTextual(
        context: Context?,
        parsed: DateWrapper?,
        textual: String?
    ): String? {
        if (parsed == null) {
            return textual
        }

        val parsedRelativeTime = relativeTime(parsed.offsetDateTime())

        return if (shouldShowOriginalTimeAgo(context)) {
            "$parsedRelativeTime ($textual)"
        } else {
            parsedRelativeTime
        }
    }

    private fun shouldShowOriginalTimeAgo(context: Context?): Boolean {
        return if (MainActivity.DEBUG && context != null) {
            PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_original_time_ago_key), false)
        } else {
            false
        }
    }

    private fun getLocaleFromPrefs(
        context: Context,
        @StringRes prefKey: Int
    ): Locale? {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val defaultKey = context.getString(R.string.default_localization_key)
        // TODO: Once SharedPreferences is in Kotlin we can remove the `?: defaultKey`
        val languageCode = sp.getString(context.getString(prefKey), defaultKey) ?: defaultKey

        return if (languageCode == defaultKey) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageCode)
        }
    }

    private fun round(value: Double, scale: Int): Double {
        return BigDecimal(value).setScale(scale, RoundingMode.HALF_UP).toDouble()
    }

    private fun getQuantity(
        context: Context,
        @PluralsRes pluralId: Int,
        @StringRes zeroCaseStringId: Int,
        count: Long,
        formattedCount: String?
    ): String {
        if (count == 0L) {
            return context.getString(zeroCaseStringId)
        }

        // As we use the already formatted count
        // is not the responsibility of this method handle long numbers
        // (it probably will fall in the "other" category,
        // or some language have some specific rule... then we have to change it)
        val safeCount = MathUtils.clamp(
            count,
            Int.Companion.MIN_VALUE.toLong(),
            Int.Companion.MAX_VALUE.toLong()
        ).toInt()
        return context.getResources().getQuantityString(pluralId, safeCount, formattedCount)
    }

    // Starting with pull request #12093, NewPipe exclusively uses Android's
    // public per-app language APIs to read and set the UI language for NewPipe.
    // The following code will migrate any existing custom app language in SharedPreferences to
    // use the public per-app language APIs instead.
    // For reference, see
    // https://android-developers.googleblog.com/2022/11/per-app-language-preferences-part-1.html
    fun migrateAppLanguageSettingIfNecessary(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val appLanguageKey = context.getString(R.string.app_language_key)
        val appLanguageValue = sp.getString(appLanguageKey, null) ?: return

        migrateAppLanguageSetting(context, sp, appLanguageKey, appLanguageValue)
    }

    private fun migrateAppLanguageSetting(
        context: Context,
        sp: SharedPreferences,
        appLanguageKey: String,
        appLanguageValue: String
    ) {
        // The app language key is used on Android versions < 33
        // for more info, see ContentSettingsFragment
        if (trySharedPrefsRemove(sp, appLanguageKey)) {
            return
        } else {
            val appLanguageDefaultValue =
                context.getString(R.string.default_localization_key)

            setAppLanguage(
                context,
                appLanguageValue,
                appLanguageDefaultValue
            )
        }
    }

    private fun trySharedPrefsRemove(sp: SharedPreferences, key: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sp.edit { remove(key) }
            true
        } else {
            false
        }
    }

    private fun setAppLanguage(context: Context, language: String, defaultLanguage: String) {
        if (language != defaultLanguage) {
            try {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(language)
                )
            } catch (e: RuntimeException) {
                Log.e(
                    TAG,
                    "Failed to migrate previous custom app language " +
                        "setting to public per-app language APIs"
                )
            }
        }
    }
}
