package com.qtv.app.config

import android.content.Context
import org.json.JSONObject

data class QtvSource(
    val url: String,
    val priority: Int,
    val type: String,
    val label: String,
)

data class QtvChannel(
    val id: String,
    val name: String,
    val category: String,
    val status: String,
    val sourceType: String,
    val sources: List<QtvSource>,
)

fun loadBundledChannels(context: Context): List<QtvChannel> {
    val rawJson = context.assets.open("qtv.json").bufferedReader().use { it.readText() }
    val root = JSONObject(rawJson)
    val items = root
        .getJSONObject("channels")
        .getJSONArray("items")

    return buildList(items.length()) {
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val parsedSources = item.optJSONArray("sources")
                ?.let(::parseSources)
                .orEmpty()

            if (parsedSources.isEmpty()) {
                continue
            }

            add(
                QtvChannel(
                    id = item.optString("id", "channel-$index"),
                    name = item.optString("name", "Channel ${index + 1}"),
                    category = item.optString("category", "Local qtv.json"),
                    status = "Configured",
                    sourceType = parsedSources.first().type,
                    sources = parsedSources,
                ),
            )
        }
    }
}

private fun parseSources(sources: org.json.JSONArray): List<QtvSource> =
    (0 until sources.length())
        .map { sources.getJSONObject(it) }
        .mapIndexedNotNull { index, source ->
            val url = source.optString("url")
            if (url.isBlank()) {
                return@mapIndexedNotNull null
            }

            QtvSource(
                url = url,
                priority = source.optInt("priority", Int.MAX_VALUE),
                type = source.optString("type", "hls").lowercase(),
                label = source.optString("label", "Source ${index + 1}"),
            )
        }
        .sortedBy { source -> source.priority }
