package app.weglet.browser

import android.content.SharedPreferences
import android.net.Uri
import java.net.HttpURLConnection
import java.net.IDN
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads the public OpenPhish community feed and stores only SHA-256 URL indicators.
 * Browsing URLs are checked locally and are never submitted to the feed provider.
 */
class ThreatFeed(private val prefs: SharedPreferences) {
    data class Snapshot(
        val enabled: Boolean,
        val indicatorCount: Int,
        val sourceUrlCount: Int,
        val lastUpdatedAt: Long,
        val updating: Boolean,
        val lastUpdateFailed: Boolean
    )

    @Volatile
    private var hashes: Set<String> =
        prefs.getStringSet(KEY_HASHES, emptySet()).orEmpty().toSet()

    private val updating = AtomicBoolean(false)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun isKnownPhishing(url: String): Boolean {
        if (!isEnabled() || hashes.isEmpty()) return false
        return indicators(url).any(hashes::contains)
    }

    fun snapshot(): Snapshot = Snapshot(
        enabled = isEnabled(),
        indicatorCount = hashes.size,
        sourceUrlCount = prefs.getInt(KEY_SOURCE_COUNT, 0),
        lastUpdatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
        updating = updating.get(),
        lastUpdateFailed = prefs.getBoolean(KEY_LAST_FAILED, false)
    )

    fun refreshIfNeeded(force: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        if (!isEnabled()) {
            onComplete(false)
            return
        }
        val lastUpdated = prefs.getLong(KEY_UPDATED_AT, 0L)
        val cacheIsFresh = hashes.isNotEmpty() &&
            System.currentTimeMillis() - lastUpdated < UPDATE_INTERVAL_MS
        if (!force && cacheIsFresh) {
            onComplete(true)
            return
        }
        if (!updating.compareAndSet(false, true)) return

        Thread {
            val success = runCatching {
                val downloaded = downloadIndicators()
                require(downloaded.hashes.size >= MIN_INDICATORS) {
                    "Threat feed returned too few indicators"
                }
                val saved = prefs.edit()
                    .putStringSet(KEY_HASHES, downloaded.hashes)
                    .putInt(KEY_SOURCE_COUNT, downloaded.sourceUrlCount)
                    .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                    .putBoolean(KEY_LAST_FAILED, false)
                    .commit()
                require(saved) { "Threat feed cache could not be saved" }
                hashes = downloaded.hashes
            }.onFailure {
                prefs.edit().putBoolean(KEY_LAST_FAILED, true).apply()
            }.isSuccess
            updating.set(false)
            onComplete(success)
        }.apply {
            name = "Weglet-ThreatFeed"
            isDaemon = true
            start()
        }
    }

    private fun downloadIndicators(): DownloadedFeed {
        val connection = URL(FEED_URL).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "text/plain")
            connection.setRequestProperty("User-Agent", "Weglet/1.3.6 Android threat-feed")
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Threat feed HTTP ${connection.responseCode}"
            }
            val contentLength = connection.contentLengthLong
            require(contentLength < 0 || contentLength <= MAX_FEED_BYTES) {
                "Threat feed is unexpectedly large"
            }

            val collected = LinkedHashSet<String>()
            var sourceCount = 0
            var bytesRead = 0L
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.take(MAX_FEED_LINES).forEach { rawLine ->
                    bytesRead += rawLine.toByteArray(Charsets.UTF_8).size + 1L
                    require(bytesRead <= MAX_FEED_BYTES) { "Threat feed exceeded size limit" }
                    val line = rawLine.trim()
                    if (line.startsWith("http://", true) || line.startsWith("https://", true)) {
                        val lineIndicators = indicators(line)
                        if (lineIndicators.isNotEmpty()) {
                            collected += lineIndicators
                            sourceCount++
                        }
                    }
                }
            }
            DownloadedFeed(collected, sourceCount)
        } finally {
            connection.disconnect()
        }
    }

    private data class DownloadedFeed(
        val hashes: Set<String>,
        val sourceUrlCount: Int
    )

    companion object {
        private const val KEY_ENABLED = "openphish_enabled"
        private const val KEY_HASHES = "openphish_indicators_v1"
        private const val KEY_SOURCE_COUNT = "openphish_source_count"
        private const val KEY_UPDATED_AT = "openphish_updated_at"
        private const val KEY_LAST_FAILED = "openphish_last_failed"
        private const val FEED_URL =
            "https://raw.githubusercontent.com/openphish/public_feed/refs/heads/main/feed.txt"
        private const val UPDATE_INTERVAL_MS = 12L * 60L * 60L * 1000L
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 12_000
        private const val MAX_FEED_BYTES = 2L * 1024L * 1024L
        private const val MAX_FEED_LINES = 50_000
        private const val MIN_INDICATORS = 50

        private fun indicators(rawUrl: String): Set<String> {
            val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return emptySet()
            if (uri.scheme?.lowercase(Locale.US) !in setOf("http", "https")) return emptySet()
            val rawHost = uri.host?.trim()?.trimEnd('.') ?: return emptySet()
            val host = runCatching {
                if (rawHost.contains(':')) rawHost.lowercase(Locale.US)
                else IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.US)
            }.getOrNull() ?: return emptySet()
            if (host.isBlank()) return emptySet()

            val port = uri.port.takeIf { it > 0 && it !in setOf(80, 443) }
            val authority = if (port == null) host else "$host:$port"
            val path = uri.encodedPath.orEmpty().ifBlank { "/" }
                .replace(Regex("/{2,}"), "/")
                .let { if (it.length > 1) it.trimEnd('/') else it }
                .take(2048)
            val query = uri.encodedQuery.orEmpty().take(2048)
            val plain = LinkedHashSet<String>()
            val base = "$authority|$path"
            if (query.isBlank()) {
                plain += base
            } else {
                plain += "$base?$query"
                // A non-root phishing route usually remains malicious when tracking parameters change.
                if (path != "/") plain += base
            }
            return plain.mapTo(LinkedHashSet(), ::sha256)
        }

        private fun sha256(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
