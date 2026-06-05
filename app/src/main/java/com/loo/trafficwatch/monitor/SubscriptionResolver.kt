package com.loo.trafficwatch.monitor

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import com.loo.trafficwatch.data.SettingsRepository

class SubscriptionResolver(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    fun currentDataSlot(): Int {
        val fallback = settings.fallbackActiveSlot
        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return fallback
        if (!PermissionUtils.hasPhoneState(context)) return fallback

        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return fallback
        val info = try {
            manager.getActiveSubscriptionInfo(subId)
        } catch (_: SecurityException) {
            null
        }

        return info?.simSlotIndex
            ?.plus(1)
            ?.coerceIn(1, 2)
            ?: fallback
    }

    fun activeSlotName(): String {
        val slot = currentDataSlot()
        val profile = settings.getSimProfile(slot)
        return buildString {
            append(profile.label.ifBlank { "卡$slot" })
            if (profile.carrier.isNotBlank()) append(" · ").append(profile.carrier)
            if (profile.phoneNumber.isNotBlank()) append(" · ").append(profile.phoneNumber)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !PermissionUtils.hasPhoneState(context)) {
                append(" · 手动归属")
            }
        }
    }
}
