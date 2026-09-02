/*
 * SPDX-FileCopyrightText: 2026 KDE Connect Contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.customwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.ui.list.DeviceItem
import org.kde.kdeconnect.ui.list.ListAdapter
import org.kde.kdeconnect_tp.R

class Custom2x3WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        setResult(RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)

        setContentView(R.layout.widget_custom_2x3_config)

        val deviceList = findViewById<ListView>(R.id.custom_widget_device_list)
        val noDevices = findViewById<TextView>(R.id.no_devices)

        val pairedDevices = KdeConnect.getInstance().devices.values
            .asSequence()
            .filter(Device::isPaired)
            .toList()

        val list = ListAdapter(this, pairedDevices.map { DeviceItem(it, ::deviceClicked) })
        deviceList.adapter = list
        deviceList.emptyView = noDevices
    }

    private fun deviceClicked(device: Device) {
        val deviceId = device.deviceId
        saveCustomWidgetDeviceIdPref(this, appWidgetId, deviceId)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        updateAppWidget(this, appWidgetManager, appWidgetId)

        val resultValue = Intent()
        resultValue.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

private const val PREFS_NAME = "org.kde.kdeconnect_tp.Custom2x3WidgetProvider"
private const val PREF_PREFIX_KEY = "custom_widget_device_"

internal fun saveCustomWidgetDeviceIdPref(context: Context, appWidgetId: Int, deviceId: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        putString(PREF_PREFIX_KEY + appWidgetId, deviceId)
    }
}

internal fun loadCustomWidgetDeviceIdPref(context: Context, appWidgetId: Int): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
}

internal fun deleteCustomWidgetDeviceIdPref(context: Context, appWidgetId: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        remove(PREF_PREFIX_KEY + appWidgetId)
    }
}
