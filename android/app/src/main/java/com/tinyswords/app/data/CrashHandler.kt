package com.tinyswords.app.data

import android.content.Context
import android.content.Intent
import android.os.Process
import com.tinyswords.app.MainActivity

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    companion object {
        private const val PREFS_NAME = "tinyswords_crash"
        private const val KEY_CRASH_TRACE = "crash_trace"
        private const val KEY_HAS_CRASH = "has_crash"

        fun getCrashTrace(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return if (prefs.getBoolean(KEY_HAS_CRASH, false)) prefs.getString(KEY_CRASH_TRACE, null) else null
        }

        fun clearCrash(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val trace = buildString {
            append("Thread: ${thread.name}\n")
            append("Time: ${System.currentTimeMillis()}\n\n")
            append(throwable.stackTraceToString())
            var cause = throwable.cause
            while (cause != null) {
                append("\nCaused by:\n")
                append(cause.stackTraceToString())
                cause = cause.cause
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CRASH_TRACE, trace)
            .putBoolean(KEY_HAS_CRASH, true)
            .commit()

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        Process.killProcess(Process.myPid())
    }
}