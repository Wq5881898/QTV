package com.qtv.app.config

import org.json.JSONArray
import org.json.JSONObject

/** A deliberately small, allow-listed bridge for on-demand site configurations. */
data class XptvVodSite(
    val name: String,
    val api: String,
    val extensionUrl: String,
)

internal fun parseXptvVodSites(root: JSONObject): List<XptvVodSite> {
    val sites = root.optJSONArray("sites") ?: JSONArray().put(root)
    return buildList {
        for (index in 0 until sites.length()) {
            val site = sites.optJSONObject(index) ?: continue
            val type = site.optInt("type", -1)
            val api = site.optString("api").trim()
            val ext = site.optString("ext").trim()
            // Remote extensions are never executed. Supported contracts have native resolvers.
            val supported =
                (type == 1 && (api.startsWith("https://") || api.startsWith("http://"))) ||
                    (type == 3 && api == "csp_huangguo" && ext.isNotBlank())
            if (supported) {
                add(
                    XptvVodSite(
                        name = site.optString("name", "点播").ifBlank { "点播" },
                        api = api,
                        extensionUrl = ext,
                    ),
                )
            }
        }
    }
}
