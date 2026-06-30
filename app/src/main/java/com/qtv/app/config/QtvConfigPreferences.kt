package com.qtv.app.config

import android.content.Context
import com.qtv.app.BuildConfig

private const val PREFS_NAME = "qtv_config_prefs"
private const val KEY_EXTERNAL_URL = "external_url"
private const val KEY_UPDATE_URL = "update_url"
private const val KEY_LAST_UPDATED_AT = "last_updated_at"
private const val KEY_LAST_APP_UPDATE_CHECK_AT = "last_app_update_check_at"
private const val KEY_CACHED_REMOTE_JSON = "cached_remote_json"
private const val KEY_CACHED_REMOTE_URL = "cached_remote_url"
private const val KEY_LAST_REMOTE_SYNC_AT = "last_remote_sync_at"
private const val LEGACY_DEFAULT_EXTERNAL_URL = "https://raw.githubusercontent.com/Wq5881898/QTV/main/qtv.json"

class QtvConfigPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyDefaultExternalUrl()
    }

    fun getSavedExternalUrl(): String? =
        prefs.getString(KEY_EXTERNAL_URL, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun getDefaultExternalUrl(): String =
        BuildConfig.QTV_REMOTE_CONFIG_URL.trim()

    fun getSavedUpdateUrl(): String? =
        prefs.getString(KEY_UPDATE_URL, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun getDefaultUpdateUrl(): String =
        BuildConfig.QTV_UPDATE_URL.trim()

    fun getConfiguredExternalUrl(): String =
        getSavedExternalUrl() ?: getDefaultExternalUrl()

    fun getConfiguredUpdateUrl(): String =
        getSavedUpdateUrl() ?: getDefaultUpdateUrl()

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

    fun saveUpdateUrl(url: String) {
        val normalized = url.trim()
        if (normalized.isBlank()) {
            clearUpdateUrl()
            return
        }
        prefs.edit().putString(KEY_UPDATE_URL, normalized).apply()
    }

    fun clearUpdateUrl() {
        prefs.edit().remove(KEY_UPDATE_URL).apply()
    }

    fun getLastUpdatedAtMillis(): Long? =
        prefs.getLong(KEY_LAST_UPDATED_AT, 0L)
            .takeIf { it > 0L }

    fun saveLastUpdatedAtMillis(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATED_AT, timestampMillis).apply()
    }

    fun getLastAppUpdateCheckAtMillis(): Long? =
        prefs.getLong(KEY_LAST_APP_UPDATE_CHECK_AT, 0L)
            .takeIf { it > 0L }

    fun saveLastAppUpdateCheckAtMillis(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_APP_UPDATE_CHECK_AT, timestampMillis).apply()
    }

    fun getCachedRemoteJson(expectedUrl: String): String? {
        val savedUrl = prefs.getString(KEY_CACHED_REMOTE_URL, null)?.trim()
        if (savedUrl.isNullOrBlank() || savedUrl != expectedUrl.trim()) {
            return null
        }
        return prefs.getString(KEY_CACHED_REMOTE_JSON, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun saveCachedRemoteJson(url: String, rawJson: String) {
        prefs.edit()
            .putString(KEY_CACHED_REMOTE_URL, url.trim())
            .putString(KEY_CACHED_REMOTE_JSON, rawJson)
            .putLong(KEY_LAST_REMOTE_SYNC_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearCachedRemoteJson() {
        prefs.edit()
            .remove(KEY_CACHED_REMOTE_URL)
            .remove(KEY_CACHED_REMOTE_JSON)
            .remove(KEY_LAST_REMOTE_SYNC_AT)
            .apply()
    }

    fun getLastRemoteSyncAtMillis(): Long? =
        prefs.getLong(KEY_LAST_REMOTE_SYNC_AT, 0L)
            .takeIf { it > 0L }

    fun resolvePreferredLocation(): QtvConfigLocation =
        getConfiguredExternalUrl()
            .takeIf { it.isNotBlank() }
            ?.let(QtvConfigLocation::ExternalUrl)
            ?: QtvConfigLocation.BundledDefault

    private fun migrateLegacyDefaultExternalUrl() {
        val savedUrl = prefs.getString(KEY_EXTERNAL_URL, null)?.trim().orEmpty()
        if (savedUrl != LEGACY_DEFAULT_EXTERNAL_URL) {
            return
        }

        prefs.edit()
            .putString(KEY_EXTERNAL_URL, getDefaultExternalUrl())
            .remove(KEY_CACHED_REMOTE_URL)
            .remove(KEY_CACHED_REMOTE_JSON)
            .remove(KEY_LAST_REMOTE_SYNC_AT)
            .apply()
    }
}
