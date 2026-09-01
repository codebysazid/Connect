/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2021 Ilmaz Gumerov <ilmaz1309@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.helpers.ThreadHelper.execute
import org.kde.kdeconnect_tp.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardListener private constructor(ctx: Context) {
    enum class ClipboardContentType {
        Text,
        Password,
    }

    interface ClipboardObserver {
        fun clipboardChanged(content: String, contentType: ClipboardContentType)
    }

    private val observers: HashSet<ClipboardObserver> = HashSet()

    private val context: Context = ctx.applicationContext
    var currentContent: String? = null
        private set
    var currentContentType: ClipboardContentType = ClipboardContentType.Text
        private set
    var updateTimestamp: Long = 0
        private set

    private lateinit var cm: ClipboardManager
    @Volatile
    private var isLogcatRunning = false

    init {
        Handler(Looper.getMainLooper()).post {
            cm = ContextCompat.getSystemService(context, ClipboardManager::class.java)!!
            cm.addPrimaryClipChangedListener { this.onClipboardChanged() }
        }
        startLogcatListenerIfNeeded()
    }

    fun startLogcatListenerIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ClipboardPlugin.canSyncAutomatically(context)) {
            if (isLogcatRunning) return
            isLogcatRunning = true
            execute {
                while (isLogcatRunning) {
                    try {
                        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                        val process = Runtime.getRuntime().exec(arrayOf(
                            "logcat",
                            "-b", "system",
                            "-T", timeStamp,
                            "ClipboardService:E",
                            "*:S"
                        ))
                        val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                        bufferedReader.forEachLine { line ->
                            if (line.contains("org.kde.kdeconnect") || line.contains(BuildConfig.APPLICATION_ID)) {
                                try {
                                    val intent = ClipboardFloatingActivity.getIntent(context, false)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("ClipboardListener", "Error starting ClipboardFloatingActivity", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ClipboardListener", "Logcat stream error", e)
                    }
                    try {
                        Thread.sleep(1000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    fun registerObserver(observer: ClipboardObserver) {
        observers.add(observer)
        startLogcatListenerIfNeeded()
    }

    fun removeObserver(observer: ClipboardObserver) {
        observers.remove(observer)
    }

    fun onClipboardChanged() {
        try {
            if (!this::cm.isInitialized) {
                cm = ContextCompat.getSystemService(context, ClipboardManager::class.java) ?: return
            }
            val clip = cm.primaryClip ?: return
            val item = clip.getItemAt(0)
            val content = item.coerceToText(context).toString()
            val contentType = detectContentType(clip)

            if (content == currentContent && contentType == currentContentType) {
                return
            }
            updateTimestamp = System.currentTimeMillis()
            currentContent = content
            currentContentType = contentType

            for (observer in observers) {
                observer.clipboardChanged(content, contentType)
            }
        } catch (_: Exception) {
            // Probably clipboard was not text
        }
    }

    @Suppress("deprecation")
    fun setText(text: String?) {
        if (this::cm.isInitialized) {
            updateTimestamp = System.currentTimeMillis()
            currentContent = text
            currentContentType = ClipboardContentType.Text
            cm.text = text
        }
    }

    companion object {
        private var _instance: ClipboardListener? = null

        @JvmStatic
        fun instance(context: Context): ClipboardListener {
            return _instance ?: ClipboardListener(context).also { _instance = it }
        }

        @JvmStatic
        fun detectContentType(clip: ClipData?): ClipboardContentType {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return ClipboardContentType.Text
            }
            if (clip?.description?.extras
                    ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            ) {
                return ClipboardContentType.Password
            }
            return ClipboardContentType.Text
        }
    }
}
