package com.qtv.app.config

import android.content.Context
import com.qtv.app.BuildConfig

private const val PREFS_NAME = "qtv_config_prefs"
private const val KEY_EXTERNAL_URL = "external_url"
private const val KEY_LAST_UPDATED_AT = "last_updated_at"

class QtvConfigPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedExternalUrl(): String? =
        prefs.getString(KEY_EXTERNAL_URL, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun getDefaultExternalUrl(): String =
        BuildConfig.QTV_REMOTE_CONFIG_URL.trim()

    fun getConfiguredExternalUrl(): String =
        getSavedExternalUrl() ?: getDefaultExternalUrl()

    fun saveExternalUrl(url: String) {
        val normalized = url.trim()
        if (normalized.isBlank()) {
            clearExternalUrl()
            return
        }
        prefs.edit().putString(KEY_EXTERNAL_URL, normalized).apply()
    }

    fun clearExternalUrl() {
        prefs.edit().remove(KEY_EXTERNAL_URL).apply()
    }

    fun getLastUpdatedAtMillis(): Long? =
        prefs.getLong(KEY_LAST_UPDATED_AT, 0L)
            .takeIf { it > 0L }

    fun saveLastUpdatedAtMillis(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATED_AT, timestampMillis).apply()
    }

    fun resolvePreferredLocation(): QtvConfigLocation =
        getConfiguredExternalUrl()
            .takeIf { it.isNotBlank() }
            ?.let(QtvConfigLocation::ExternalUrl)
            ?: QtvConfigLocation.BundledDefault
}
