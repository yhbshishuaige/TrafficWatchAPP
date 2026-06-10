package com.loo.trafficwatch.data

import android.content.Context
import androidx.core.content.edit

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("traffic_watch_settings", Context.MODE_PRIVATE)

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_MONITORING_ENABLED, value) }

    var fallbackActiveSlot: Int
        get() = prefs.getInt(KEY_FALLBACK_ACTIVE_SLOT, 1).coerceIn(1, 2)
        set(value) = prefs.edit { putInt(KEY_FALLBACK_ACTIVE_SLOT, value.coerceIn(1, 2)) }

    var lastSampleAtMillis: Long
        get() = prefs.getLong(KEY_LAST_SAMPLE_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_SAMPLE_AT, value) }

    var splashAnimationEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPLASH_ANIMATION_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_SPLASH_ANIMATION_ENABLED, value) }

    fun getSimProfiles(): List<SimProfile> = listOf(getSimProfile(1), getSimProfile(2))

    fun getSimProfile(slot: Int): SimProfile {
        val safeSlot = slot.coerceIn(1, 2)
        val prefix = "sim_$safeSlot"
        return SimProfile(
            slot = safeSlot,
            phoneNumber = prefs.getString("${prefix}_phone", "").orEmpty(),
            carrier = prefs.getString("${prefix}_carrier", "").orEmpty(),
            label = prefs.getString("${prefix}_label", "卡$safeSlot").orEmpty().ifBlank { "卡$safeSlot" },
        )
    }

    fun saveSimProfile(profile: SimProfile) {
        val safeSlot = profile.slot.coerceIn(1, 2)
        val prefix = "sim_$safeSlot"
        prefs.edit {
            putString("${prefix}_phone", profile.phoneNumber.trim())
            putString("${prefix}_carrier", profile.carrier.trim())
            putString("${prefix}_label", profile.label.trim().ifBlank { "卡$safeSlot" })
        }
    }

    companion object {
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_FALLBACK_ACTIVE_SLOT = "fallback_active_slot"
        private const val KEY_LAST_SAMPLE_AT = "last_sample_at"
        private const val KEY_SPLASH_ANIMATION_ENABLED = "splash_animation_enabled"
    }
}
