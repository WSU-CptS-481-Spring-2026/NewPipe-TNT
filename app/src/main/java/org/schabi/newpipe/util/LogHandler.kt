package org.schabi.newpipe.util

import android.util.Log
import org.schabi.newpipe.MainActivity

class LogHandler {
    companion object {
        private val DEBUG = MainActivity.DEBUG

        @JvmStatic
        public fun LogInDebugMode(tag: String, msg: String) {
            if (DEBUG) {
                Log.d(
                    tag,
                    msg
                )
            }
        }
    }
}