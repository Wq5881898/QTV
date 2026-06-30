package com.qtv.app.config

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val REMOTE_CONNECT_TIMEOUT_MS = 8_000
private const val REMOTE_READ_TIMEOUT_MS = 8_000

sealed interface QtvConfigLocation {
    data object BundledDefault : QtvConfigLocation

    data class ExternalUrl(val url: String) : QtvConfigLocation
}

data class QtvConfigCatalog(
    val channels: List<QtvChannel>,
    val activeLocation: QtvConfigLocation,
    val sourceSummary: String,
    val warningMessage: String? = null,
)

interface QtvConfigSource {
    fun canHandle(location: QtvConfigLocation): Boolean

    suspend fun loadRawJson(context: Context, location: QtvConfigLocation): String
}

class BundledQtvConfigSource : QtvConfigSource {
    override fun canHandle(location: QtvConfigLocation): Boolean =
        location == QtvConfigLocation.BundledDefault

    override suspend fun loadRawJson(context: Context, location: QtvConfigLocation): String =
        context.assets.open("qtv.json").bufferedReader().use { it.readText() }
}

class ExternalUrlQtvConfigSource : QtvConfigSource {
    override fun canHandle(location: QtvConfigLocation): Boolean = location is QtvConfigLocation.ExternalUrl

    override suspend fun loadRawJson(context: Context, location: QtvConfigLocation): String {
        location as QtvConfigLocation.ExternalUrl
        val connection = (URL(location.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
            readTimeout = REMOTE_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            doInput = true
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
            setRequestProperty("Pragma", "no-cache")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Remote config request failed with HTTP $responseCode")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class QtvConfigRepository(
    private val sources: List<QtvConfigSource> = listOf(
        BundledQtvConfigSource(),
        ExternalUrlQtvConfigSource(),
    ),
) {
    suspend fun loadCatalogFromRawJson(
        rawJson: String,
        location: QtvConfigLocation,
    ): QtvConfigCatalog =
        QtvConfigCatalog(
            channels = parseQtvChannels(rawJson, fallbackCategory = location.defaultCategory()),
            activeLocation = location,
            sourceSummary = location.sourceSummary(),
        )

    suspend fun fetchRawJson(
        context: Context,
        location: QtvConfigLocation,
    ): String {
        val source = sources.firstOrNull { it.canHandle(location) }
            ?: throw IllegalArgumentException("No config source registered for $location")
        return source.loadRawJson(context, location)
    }

    suspend fun loadCatalog(
        context: Context,
        preferredLocation: QtvConfigLocation,
    ): QtvConfigCatalog {
        val preferredResult = runCatching { loadFrom(context, preferredLocation) }
        if (preferredResult.isSuccess) {
            return preferredResult.getOrThrow()
        }

        val preferredError = preferredResult.exceptionOrNull() ?: IllegalStateException("Unknown config failure")
        if (preferredLocation == QtvConfigLocation.BundledDefault) {
            throw preferredError
        }

        val fallbackCatalog = loadFrom(context, QtvConfigLocation.BundledDefault)
        return fallbackCatalog.copy(
            warningMessage = buildFallbackWarning(preferredLocation, preferredError),
        )
    }

    private suspend fun loadFrom(
        context: Context,
        location: QtvConfigLocation,
    ): QtvConfigCatalog {
        val rawJson = fetchRawJson(context, location)
        return loadCatalogFromRawJson(rawJson, location)
    }
}

private fun QtvConfigLocation.defaultCategory(): String =
    when (this) {
        QtvConfigLocation.BundledDefault -> "Bundled qtv.json"
        is QtvConfigLocation.ExternalUrl -> "External source"
    }

private fun QtvConfigLocation.sourceSummary(): String =
    when (this) {
        QtvConfigLocation.BundledDefault -> "Bundled default"
        is QtvConfigLocation.ExternalUrl -> "External URL"
    }

private fun buildFallbackWarning(
    location: QtvConfigLocation,
    error: Throwable,
): String {
    val reason = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    val sourceName = when (location) {
        QtvConfigLocation.BundledDefault -> "bundled default config"
        is QtvConfigLocation.ExternalUrl -> "external source URL"
    }
    return if (reason.isBlank()) {
        "Failed to load $sourceName. Using bundled default config."
    } else {
        "Failed to load $sourceName. Using bundled default config. Reason: $reason"
    }
}
