package com.qtv.app.updater

import android.net.Uri
import com.qtv.app.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class QtvUpdateRepository {
    fun checkForUpdate(updateUrl: String): QtvUpdateResult {
        val normalizedUrl = updateUrl.trim()
        require(normalizedUrl.isNotBlank()) { "Update URL is empty" }

        return when {
            normalizedUrl.endsWith(".apk", ignoreCase = true) ->
                QtvUpdateResult(
                    updateAvailable = true,
                    latestVersion = null,
                    currentVersion = BuildConfig.VERSION_NAME,
                    downloadUrl = normalizedUrl,
                    releasePageUrl = normalizedUrl,
                    notes = "Manual APK update source configured.",
                )

            normalizedUrl.contains("api.github.com/repos/", ignoreCase = true) ->
                fetchGitHubRelease(normalizedUrl)

            normalizedUrl.contains("github.com", ignoreCase = true) &&
                normalizedUrl.contains("/releases/latest", ignoreCase = true) ->
                fetchGitHubRelease(toGitHubLatestApiUrl(normalizedUrl))

            else -> throw IllegalArgumentException("Unsupported update URL. Use a GitHub release API URL, a GitHub latest release URL, or a direct APK URL.")
        }
    }

    private fun fetchGitHubRelease(apiUrl: String): QtvUpdateResult {
        val payload = fetchJson(apiUrl)
        val release = JSONObject(payload)
        val latestVersion = release.optString("tag_name")
            .ifBlank { release.optString("name") }
            .ifBlank { null }
        val releasePageUrl = release.optString("html_url").ifBlank { apiUrl }
        val notes = release.optString("body").ifBlank { null }
        val assets = release.optJSONArray("assets")

        var downloadUrl: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val candidateUrl = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && candidateUrl.isNotBlank()) {
                    downloadUrl = candidateUrl
                    break
                }
            }
        }

        val updateAvailable = latestVersion?.let { isRemoteVersionNewer(it, BuildConfig.VERSION_NAME) } ?: false

        return QtvUpdateResult(
            updateAvailable = updateAvailable,
            latestVersion = latestVersion,
            currentVersion = BuildConfig.VERSION_NAME,
            downloadUrl = downloadUrl,
            releasePageUrl = releasePageUrl,
            notes = notes,
        )
    }

    private fun fetchJson(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "QTV/${BuildConfig.VERSION_NAME}")
        }

        return connection.useAndRead()
    }

    private fun HttpURLConnection.useAndRead(): String {
        try {
            val stream =
                if (responseCode in 200..299) {
                    inputStream
                } else {
                    errorStream ?: throw IllegalStateException("Update request failed with HTTP $responseCode")
                }

            return BufferedReader(InputStreamReader(stream)).use { reader ->
                buildString {
                    reader.forEachLine { line ->
                        append(line)
                    }
                }
            }
        } finally {
            disconnect()
        }
    }

    private fun toGitHubLatestApiUrl(url: String): String {
        val uri = Uri.parse(url)
        val segments = uri.pathSegments
        if (segments.size < 4 || segments[2] != "releases" || segments[3] != "latest") {
            throw IllegalArgumentException("Unsupported GitHub latest release URL.")
        }
        val owner = segments[0]
        val repo = segments[1]
        return "https://api.github.com/repos/$owner/$repo/releases/latest"
    }

    private fun isRemoteVersionNewer(remote: String, current: String): Boolean {
        val remoteParts = normalizeVersion(remote)
        val currentParts = normalizeVersion(current)
        val maxLength = maxOf(remoteParts.size, currentParts.size)
        for (index in 0 until maxLength) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (remotePart != currentPart) {
                return remotePart > currentPart
            }
        }
        return false
    }

    private fun normalizeVersion(value: String): List<Int> =
        value.trim()
            .removePrefix("v")
            .split('.', '-', '_')
            .mapNotNull { token -> token.toIntOrNull() }
}

data class QtvUpdateResult(
    val updateAvailable: Boolean,
    val latestVersion: String?,
    val currentVersion: String,
    val downloadUrl: String?,
    val releasePageUrl: String?,
    val notes: String?,
)
