package com.qtv.app.vod

import com.qtv.app.config.XptvVodSite
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.json.JSONObject

data class VodTab(val name: String, val id: String)
data class VodCard(val id: String, val name: String, val remarks: String)
data class VodTrack(val name: String, val url: String, val episode: String)
data class ResolvedVodStream(val url: String, val headers: Map<String, String>)

interface XptvVodResolver {
    val api: String
    suspend fun tabs(): List<VodTab>
    suspend fun cards(tab: VodTab, page: Int = 1): List<VodCard>
    suspend fun tracks(card: VodCard): List<VodTrack>
    suspend fun resolve(track: VodTrack): ResolvedVodStream
}

class XptvVodRepository {
    fun resolverFor(site: XptvVodSite): XptvVodResolver? = when (site.api) {
        "csp_huangguo" -> HuangguoResolver
        else -> site.api
            .takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let(::TvBoxCmsResolver)
    }
}

/** Native reader for the standard TVBox type:1 / MacCMS JSON contract. */
private class TvBoxCmsResolver(api: String) : XptvVodResolver {
    override val api = api.substringBefore('?').trimEnd('/') + "/"
    private val userAgent =
        "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val referer = runCatching {
        URI(this.api).let { "${it.scheme}://${it.authority}/" }
    }.getOrDefault(this.api)
    private val playbackHeaders = mapOf(
        "User-Agent" to userAgent,
        "Accept" to "*/*",
        "Referer" to referer,
    )

    override suspend fun tabs(): List<VodTab> {
        val classes = fetchJson(endpoint("ac=list")).optJSONArray("class")
            ?: throw IOException("TVBox 接口没有返回分类")
        return buildList {
            for (index in 0 until classes.length()) {
                val item = classes.optJSONObject(index) ?: continue
                val id = item.optString("type_id").trim()
                val name = item.optString("type_name").trim()
                if (id.isNotBlank() && name.isNotBlank()) add(VodTab(name, id))
            }
        }.ifEmpty { throw IOException("TVBox 接口没有可用分类") }
    }

    override suspend fun cards(tab: VodTab, page: Int): List<VodCard> {
        val list = fetchJson(endpoint("ac=detail&t=${tab.id}&pg=$page")).optJSONArray("list")
            ?: return emptyList()
        return buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.optString("vod_id").trim()
                val name = item.optString("vod_name").trim()
                if (id.isBlank() || name.isBlank()) continue
                add(VodCard(id, name, item.optString("vod_remarks").trim()))
            }
        }.distinctBy { it.id }
    }

    override suspend fun tracks(card: VodCard): List<VodTrack> {
        val item = fetchJson(endpoint("ac=detail&ids=${card.id}"))
            .optJSONArray("list")
            ?.optJSONObject(0)
            ?: throw IOException("TVBox 接口没有返回影片详情")
        val providers = item.optString("vod_play_from").split("\$\$\$")
        val groups = item.optString("vod_play_url").split("\$\$\$")
        val tracks = buildList {
            groups.forEachIndexed { groupIndex, group ->
                val provider = providers.getOrNull(groupIndex).orEmpty().ifBlank { "线路${groupIndex + 1}" }
                group.split('#').forEach { entry ->
                    val separator = entry.indexOf('$')
                    val title = if (separator >= 0) entry.substring(0, separator).trim() else "播放"
                    val url = if (separator >= 0) entry.substring(separator + 1).trim() else entry.trim()
                    if (url.startsWith("http") && isHls(url)) {
                        val displayName = if (groups.size > 1) "$title · $provider" else title
                        add(VodTrack(displayName.ifBlank { "播放" }, url, provider))
                    }
                }
            }
        }.distinctBy { it.url }
        return tracks.ifEmpty { throw IOException("当前影片没有可直接播放的 HLS 地址") }
    }

    override suspend fun resolve(track: VodTrack): ResolvedVodStream {
        if (!track.url.startsWith("http") || !isHls(track.url)) {
            throw IOException("当前线路不是可直接播放的 HLS 地址")
        }
        return ResolvedVodStream(track.url, playbackHeaders)
    }

    private fun endpoint(query: String) = "$api?$query"

    private fun fetchJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("TVBox 接口请求失败：HTTP ${connection.responseCode}")
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("TVBox 接口返回了无效 JSON", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun isHls(url: String) =
        url.substringBefore('?').lowercase().endsWith(".m3u8")
}

/** Native implementation of the public csp_huangguo contract; no remote JS is executed. */
private object HuangguoResolver : XptvVodResolver {
    override val api = "csp_huangguo"
    private const val site = "https://huangguoai.com"
    private const val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val headers = mapOf("User-Agent" to userAgent, "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", "Accept-Language" to "zh-CN,zh;q=0.9", "Referer" to "$site/")

    override suspend fun tabs() = listOf(VodTab("首页", "home"), VodTab("AI成人短剧", "ai-duanju"), VodTab("AI成人漫剧", "ai-manju"), VodTab("AI换脸", "ai-huanlian"), VodTab("AI魔改", "ai-mogai"), VodTab("排行榜", "ranks/hot"))

    override suspend fun cards(tab: VodTab, page: Int): List<VodCard> {
        val url = if (tab.id == "home") site else "$site/${tab.id}/${if (page > 1) "$page/" else ""}"
        return parseCards(fetch(url), ranks = tab.id.contains("rank"))
    }

    override suspend fun tracks(card: VodCard): List<VodTrack> {
        val html = fetch("$site/detail/${card.id}/")
        val grid = Regex("""<div\s+class=\"[^\"]*\bhg-web-detail__ep-grid\b[^\"]*\"[^>]*>([\s\S]*?)</div>""").find(html)?.groupValues?.get(1).orEmpty()
        val result = Regex("""<a\b[^>]*>\s*([\s\S]*?)</a>""").findAll(grid).mapNotNull { match ->
            val tag = match.value
            val href = Regex("""href="([^"]+)"""").find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
            val episode = Regex("""data-ep-id="([^"]*)"""").find(tag)?.groupValues?.get(1).orEmpty()
            VodTrack(if (episode.isBlank()) stripTags(match.groupValues[1]) else "第${episode}集", fix(href), episode)
        }.filter { it.name.isNotBlank() }.toList()
        return result.ifEmpty { Regex("""<a\b[^>]*class="[^"]*\bhg-web-detail__play\b[^"]*"[^>]*href="([^"]+)"""").find(html)?.groupValues?.get(1)?.let { listOf(VodTrack("第1集", fix(it), "")) }.orEmpty() }
    }

    override suspend fun resolve(track: VodTrack): ResolvedVodStream {
        val html = fetch(track.url, referer = site)
        val json = Regex("""id="videoInitialData"[^>]*>([\s\S]*?)</script>""").find(html)?.groupValues?.get(1) ?: throw IOException("The episode did not provide playback data")
        val data = JSONObject(json)
        val stream = data.optJSONObject("epPlaySrcs")?.optString(track.episode).orEmpty()
            .ifBlank { data.optString("videoSrc") }
            .replace("\\u0026", "&")
        if (!stream.startsWith("http")) throw IOException("The episode did not provide a playable stream")
        return ResolvedVodStream(stream, headers)
    }

    private fun parseCards(html: String, ranks: Boolean): List<VodCard> {
        val cardClass = if (ranks) "hg-rank-item" else "hg-drama-card"
        val starts = Regex("""<div\s+class="[^"]*\b$cardClass\b[^"]*"[^>]*>""").findAll(html).map { it.range.first }.toList()
        return starts.mapIndexed { index, start -> html.substring(start, starts.getOrElse(index + 1) { html.length }) }.mapNotNull { block ->
            val id = Regex("""href="[^"]*/detail/(\d+)/[^"]*"""").find(block)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = Regex("""(?:hg-drama-card__title|hg-rank-item__title)[^>]*>([\s\S]*?)</(?:a|h2)>""").find(block)?.groupValues?.get(1)?.let(::stripTags).orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val remarks = Regex("""(?:hg-drama-card__episode|hg-rank-item__tags)[^>]*>([\s\S]*?)</(?:span|div)>""").find(block)?.groupValues?.get(1)?.let(::stripTags).orEmpty()
            VodCard(id, title, remarks)
        }.distinctBy { it.id }
    }

    private fun fetch(url: String, referer: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 15_000; instanceFollowRedirects = true
            headers.forEach { (key, value) -> setRequestProperty(key, if (key == "Referer" && referer != null) "$referer/" else value) }
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("黄果短剧请求失败：HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun fix(value: String) = when { value.startsWith("//") -> "https:$value"; value.startsWith("/") -> "$site$value"; else -> value }
    private fun stripTags(value: String) = value.replace(Regex("<[^>]*>"), "").trim()
}
