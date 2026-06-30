package com.qtv.app.config

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

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

internal fun parseQtvChannels(rawJson: String, fallbackCategory: String): List<QtvChannel> {
    val normalized = rawJson.trimStart()
    return when {
        normalized.startsWith("{") -> parseJsonChannels(rawJson, fallbackCategory)
        normalized.startsWith("#EXTM3U", ignoreCase = true) -> parseM3uChannels(rawJson, fallbackCategory)
        else -> parseSimpleTextChannels(rawJson, fallbackCategory)
    }
}

private fun parseJsonChannels(rawJson: String, fallbackCategory: String): List<QtvChannel> {
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
                    category = item.optString("category", fallbackCategory),
                    status = "Configured",
                    sourceType = parsedSources.first().type,
                    sources = parsedSources,
                ),
            )
        }
    }
}

private fun parseM3uChannels(rawText: String, fallbackCategory: String): List<QtvChannel> {
    data class PendingMeta(
        val name: String,
        val category: String,
        val id: String,
    )

    val byKey = linkedMapOf<String, MutableList<QtvSource>>()
    val metaByKey = linkedMapOf<String, PendingMeta>()
    var pendingMeta: PendingMeta? = null

    rawText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attributes = parseM3uAttributes(line)
                    val name = line.substringAfter(',', "").trim().ifBlank {
                        attributes["tvg-name"].orEmpty().trim()
                    }.ifBlank { "Channel ${metaByKey.size + 1}" }
                    val category = attributes["group-title"].orEmpty().trim().ifBlank { fallbackCategory }
                    val id = attributes["tvg-id"].orEmpty().trim().ifBlank { slugify(name) }
                    val key = buildChannelKey(id, name)
                    metaByKey.putIfAbsent(key, PendingMeta(name = name, category = category, id = id))
                    pendingMeta = metaByKey[key]
                }
                !line.startsWith("#") -> {
                    val currentMeta = pendingMeta ?: PendingMeta(
                        name = deriveNameFromUrl(line, metaByKey.size + 1),
                        category = fallbackCategory,
                        id = slugify(deriveNameFromUrl(line, metaByKey.size + 1)),
                    )
                    val key = buildChannelKey(currentMeta.id, currentMeta.name)
                    metaByKey.putIfAbsent(key, currentMeta)
                    byKey.getOrPut(key) { mutableListOf() }
                        .add(
                            QtvSource(
                                url = line,
                                priority = byKey.getOrPut(key) { mutableListOf() }.size + 1,
                                type = detectSourceType(line),
                                label = "Source ${byKey.getOrPut(key) { mutableListOf() }.size + 1}",
                            ),
                        )
                    pendingMeta = null
                }
            }
        }

    return metaByKey.mapNotNull { (key, meta) ->
        val sources = byKey[key].orEmpty().distinctBy { it.url }
        if (sources.isEmpty()) {
            null
        } else {
            QtvChannel(
                id = meta.id,
                name = meta.name,
                category = meta.category,
                status = "Configured",
                sourceType = sources.first().type,
                sources = sources.mapIndexed { index, source ->
                    source.copy(priority = index + 1, label = "Source ${index + 1}")
                },
            )
        }
    }
}

private fun parseSimpleTextChannels(rawText: String, fallbackCategory: String): List<QtvChannel> {
    val blocks = rawText
        .replace("\r\n", "\n")
        .split(Regex("\n\\s*\n"))
        .mapNotNull { block ->
            val lines = block.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.size < 2) {
                null
            } else {
                lines
            }
        }

    return buildList {
        blocks.forEachIndexed { index, lines ->
            val name = lines.first()
            val urls = lines.drop(1).filter { it.startsWith("http://") || it.startsWith("https://") }
            if (urls.isEmpty()) {
                return@forEachIndexed
            }

            add(
                QtvChannel(
                    id = slugify(name).ifBlank { "channel-${index + 1}" },
                    name = name,
                    category = fallbackCategory,
                    status = "Configured",
                    sourceType = detectSourceType(urls.first()),
                    sources = urls.distinct().mapIndexed { sourceIndex, url ->
                        QtvSource(
                            url = url,
                            priority = sourceIndex + 1,
                            type = detectSourceType(url),
                            label = "Source ${sourceIndex + 1}",
                        )
                    },
                ),
            )
        }
    }
}

private fun parseSources(sources: JSONArray): List<QtvSource> =
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

private fun parseM3uAttributes(line: String): Map<String, String> =
    Regex("""([\w-]+)="([^"]*)"""")
        .findAll(line.substringBefore(','))
        .associate { match -> match.groupValues[1] to match.groupValues[2] }

private fun detectSourceType(url: String): String =
    when {
        url.contains(".m3u8", ignoreCase = true) -> "hls"
        else -> "hls"
    }

private fun slugify(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

private fun buildChannelKey(id: String, name: String): String =
    "${id.lowercase()}::${name.lowercase()}"

private fun deriveNameFromUrl(url: String, index: Int): String =
    runCatching {
        val uri = URI(url)
        uri.path.substringAfterLast('/').substringBefore('?').ifBlank { uri.host.orEmpty() }
    }.getOrNull()
        ?.replace(Regex("[._-]+"), " ")
        ?.trim()
        ?.ifBlank { null }
        ?: "Channel $index"
