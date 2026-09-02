/*
 * SPDX-FileCopyrightText: 2026 KDE Connect Contributors
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.lockdevice

import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginFactory

@PluginFactory.LoadablePlugin
class LockDevicePlugin : Plugin() {
    override val displayName: String = "Lock Device"
    override val description: String = "Send lock/unlock requests to the PC"
    
    override val outgoingPacketTypes: Array<String> = arrayOf("kdeconnect.lock.request")
    
    override val supportedPacketTypes: Array<String> = arrayOf("kdeconnect.lock")

    @Volatile
    var isLocked: Boolean = false
        private set

    override fun onCreate(): Boolean {
        // Request the initial lock state from the PC as soon as we connect
        val packet = NetworkPacket("kdeconnect.lock.request")
        packet.set("requestLocked", true)
        device.sendPacket(packet)
        return true
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.has("isLocked")) {
            val newState = np.getBoolean("isLocked")
            if (isLocked != newState) {
                isLocked = newState
                org.kde.kdeconnect.plugins.customwidget.forceRefreshWidgets(context)
            }
        }
        return true
    }
}
