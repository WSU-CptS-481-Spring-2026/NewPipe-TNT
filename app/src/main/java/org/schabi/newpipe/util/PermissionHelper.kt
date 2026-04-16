package org.schabi.newpipe.util

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import org.schabi.newpipe.App.Companion.instance
import org.schabi.newpipe.R
import org.schabi.newpipe.settings.NewPipeSettings

object PermissionHelper {
    const val POST_NOTIFICATIONS_REQUEST_CODE: Int = 779
    const val DOWNLOAD_DIALOG_REQUEST_CODE: Int = 778
    const val DOWNLOADS_REQUEST_CODE: Int = 777

    @JvmStatic
    fun checkStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        if (NewPipeSettings.useStorageAccessFramework(activity)) return true

        return checkReadStoragePermissions(activity, requestCode) &&
                checkWriteStoragePermissions(activity, requestCode)
    }

    @JvmStatic
    fun checkReadStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                requestCode
            )
            return false
        }
        return true
    }

    @JvmStatic
    fun checkWriteStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                requestCode
            )
            return false
        }
        return true
    }

    @JvmStatic
    fun checkPostNotificationsPermission(activity: Activity, requestCode: Int): Boolean {
        if (hasNotificationPermission(activity) || instance.notificationsRequested) {
            return true
        }
        executeNotificationRequest(activity, requestCode)
        return false
    }

    private fun hasNotificationPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun executeNotificationRequest(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            requestCode
        )
        instance.setNotificationsRequested()
    }

    /**
     * Checks if the app has permission to draw over other apps.
     * Complexity drastically reduced by extracting the permission logic into helper methods.
     */
    @JvmStatic
    fun checkSystemAlertWindowPermission(context: Context): Boolean {
        // Guard Clause: Return immediately if permission is already granted
        if (Settings.canDrawOverlays(context)) return true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestLegacyOverlayPermission(context)
        } else {
            showModernOverlayPermissionDialog(context)
        }
        return false
    }

    // Helper 1: Extracted legacy logic
    private fun requestLegacyOverlayPermission(context: Context) {
        val i = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(i)
        } catch (ignored: ActivityNotFoundException) {
        }
    }

    // Helper 2: Extracted modern dialog logic
    private fun showModernOverlayPermissionDialog(context: Context) {
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val title = context.getString(R.string.permission_display_over_apps)
        val permissionName = context.getString(R.string.permission_display_over_apps_permission_name)
        val message = context.getString(
            R.string.permission_display_over_apps_message,
            "<i>$appName</i>",
            "<i>$permissionName</i>"
        )

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_COMPACT))
            .setPositiveButton("OK") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                try {
                    context.startActivity(intent)
                } catch (ignored: ActivityNotFoundException) {
                }
            }
            .setCancelable(true)
            .show()
    }

    @JvmStatic
    fun isPopupEnabledElseAsk(context: Context): Boolean {
        if (checkSystemAlertWindowPermission(context)) return true

        Toast.makeText(context, R.string.msg_popup_permission, Toast.LENGTH_LONG).show()
        return false
    }
}