package com.loo.trafficwatch.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loo.trafficwatch.data.SettingsRepository

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        val settings = SettingsRepository(context)
        if (settings.monitoringEnabled) {
            TrafficMonitorService.start(context)
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
