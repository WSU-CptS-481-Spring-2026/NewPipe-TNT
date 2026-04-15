package org.schabi.newpipe.util

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.loader.content.Loader
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.nononsenseapps.filepicker.AbstractFilePickerFragment
import com.nononsenseapps.filepicker.FilePickerActivity
import com.nononsenseapps.filepicker.FilePickerFragment
import java.io.File
import org.schabi.newpipe.R

class FilePickerActivityHelper : FilePickerActivity() {
    private var currentFragment: CustomFilePickerFragment? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // If at top most level, default onBack behavior
            if (currentFragment!!.isBackTop()) {
                this.isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                return
            }

            currentFragment!!.goUp()
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (ThemeHelper.isLightThemeSelected(this)) {
            this.setTheme(R.style.FilePickerThemeLight)
        } else {
            this.setTheme(R.style.FilePickerThemeDark)
        }

        super.onCreate(savedInstanceState)
    }

    override fun getFragment(
        startPath: String?,
        mode: Int,
        allowMultiple: Boolean,
        allowCreateDir: Boolean,
        allowExistingFile: Boolean,
        singleClick: Boolean
    ): AbstractFilePickerFragment<File?> {
        val fragment = CustomFilePickerFragment()
        fragment.setArgs(
            startPath
                ?: Environment.getExternalStorageDirectory().path,
            mode,
            allowMultiple,
            allowCreateDir,
            allowExistingFile,
            singleClick
        )
        currentFragment = fragment
        return currentFragment!!
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Internal
    ////////////////////////////////////////////////////////////////////////// */
    inner class CustomFilePickerFragment : FilePickerFragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            return super.onCreateView(inflater, container, savedInstanceState)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerView.ViewHolder {
            val viewHolder = super.onCreateViewHolder(parent, viewType)

            val view = viewHolder.itemView.findViewById<TextView?>(android.R.id.text1)
            view?.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.file_picker_items_text_size)
            )

            return viewHolder
        }

        override fun onClickOk(view: View) {
            // default behavior check
            if (mode != MODE_NEW_FILE || newFileName.isNotEmpty()) {
                super.onClickOk(view)
                return
            }

            if (mToast != null) {
                mToast.cancel()
            }
            mToast = Toast.makeText(
                activity,
                R.string.file_name_empty_error,
                Toast.LENGTH_SHORT
            )
            mToast.show()
        }

        override fun isItemVisible(file: File): Boolean {
            return (file.isDirectory() && file.isHidden()) || super.isItemVisible(file)
        }

        val backTop: File
            get() {
                if (arguments == null) {
                    return Environment.getExternalStorageDirectory()
                }

                val path = requireArguments().getString(KEY_START_PATH, "/")
                if (path.contains(Environment.getExternalStorageDirectory().path)) {
                    return Environment.getExternalStorageDirectory()
                }

                return getPath(path)
            }

        fun isBackTop(): Boolean {
            return compareFiles(
                mCurrentPath,
                this.backTop
            ) == 0 || compareFiles(mCurrentPath, File("/")) == 0
        }

        override fun onLoadFinished(
            loader: Loader<SortedList<File?>?>,
            data: SortedList<File?>?
        ) {
            super.onLoadFinished(loader, data)
            layoutManager.scrollToPosition(0)
        }
    }

    companion object {
        @JvmStatic
        fun isOwnFileUri(context: Context, uri: Uri): Boolean {
            return uri.authority != null && uri.authority!!.startsWith(context.packageName)
        }
    }
}
