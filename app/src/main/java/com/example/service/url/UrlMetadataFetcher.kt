package com.example.service.url

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class UrlMetadata(val title: String, val description: String, val siteName: String)

object UrlMetadataFetcher {
    suspend fun fetch(rawUrl: String): Result<UrlMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalize(rawUrl)
            val connection = (URL(normalized).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Tnote/2.3")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
            }
            try {
                check(connection.responseCode in 200..299) { "Page returned ${connection.responseCode}" }
                val html = connection.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(4096)
                    val out = StringBuilder()
                    while (out.length < 512_000) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        out.append(buffer, 0, count)
                    }
                    out.toString()
                }
                val title = meta(html, "og:title") ?: tag(html, "title") ?: URI(normalized).host.orEmpty()
                val description = meta(html, "og:description") ?: metaName(html, "description").orEmpty()
                val site = meta(html, "og:site_name") ?: URI(normalized).host.orEmpty().removePrefix("www.")
                UrlMetadata(decode(title).clean(), decode(description).clean(), decode(site).clean())
            } finally {
                connection.disconnect()
            }
        }
    }

    fun normalize(value: String): String = value.trim().let {
        if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "https://$it"
    }

    private fun meta(html: String, property: String): String? =
        Regex("<meta[^>]+property=[\\\"']${Regex.escape(property)}[\\\"'][^>]+content=[\\\"'](.*?)[\\\"'][^>]*>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?: Regex("<meta[^>]+content=[\\\"'](.*?)[\\\"'][^>]+property=[\\\"']${Regex.escape(property)}[\\\"'][^>]*>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)

    private fun metaName(html: String, name: String): String? =
        Regex("<meta[^>]+name=[\\\"']${Regex.escape(name)}[\\\"'][^>]+content=[\\\"'](.*?)[\\\"'][^>]*>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)

    private fun tag(html: String, tag: String): String? =
        Regex("<$tag[^>]*>(.*?)</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)

    private fun decode(value: String): String = value
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty() }

    private fun String.clean(): String = replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
}
