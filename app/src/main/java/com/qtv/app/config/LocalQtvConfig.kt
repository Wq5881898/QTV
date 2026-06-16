package com.qtv.app.config

import android.content.Context
import org.json.JSONObject

data class QtvChannel(
    val id: String,
    val name: String,
    val category: String,
    val status: String,
    val streamUrl: String,
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
            val sources = item.getJSONArray("sources")
            if (sources.length() == 0) {
                continue
            }

            val primarySource = (0 until sources.length())
                .map { sources.getJSONObject(it) }
                .sortedBy { source -> source.optInt("priority", Int.MAX_VALUE) }
                .firstOrNull()
                ?: continue

            val url = primarySource.optString("url")
            if (url.isBlank()) {
                continue
            }

            add(
                QtvChannel(
                    id = item.optString("id", "channel-$index"),
                    name = item.optString("name", "Channel ${index + 1}"),
                    category = item.optString("category", "Local qtv.json"),
                    status = "Configured",
                    streamUrl = url,
                ),
            )
        }
    }
}
