package com.dhikr.app.core.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Maps an HTTP status to the sync error it means; null for success (2xx). */
fun mapHttpStatus(code: Int, body: String? = null): SyncException? {
    if (code in 200..299) return null
    val kind = when {
        code == 401 -> SyncException.Kind.AUTH_EXPIRED
        code == 412 -> SyncException.Kind.CONFLICT
        code == 429 -> SyncException.Kind.QUOTA
        code == 403 && body != null &&
            (body.contains("quota", ignoreCase = true) || body.contains("rateLimit", ignoreCase = true)) ->
            SyncException.Kind.QUOTA
        code == 403 -> SyncException.Kind.AUTH_EXPIRED
        else -> SyncException.Kind.UNKNOWN
    }
    return SyncException(kind, "HTTP $code")
}

class HttpResult(val code: Int, val body: String, val etag: String?)

/** Minimal blocking HTTP call (callers run it on Dispatchers.IO). Never throws for
 *  non-2xx; throws [SyncException] NETWORK for connection failures. */
internal fun httpCall(
    method: String,
    url: String,
    accessToken: String,
    body: ByteArray? = null,
    contentType: String? = null,
    headers: Map<String, String> = emptyMap(),
): HttpResult {
    val conn = try {
        URL(url).openConnection() as HttpURLConnection
    } catch (e: IOException) {
        throw SyncException(SyncException.Kind.NETWORK, "Network error", e)
    }
    try {
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            contentType?.let { conn.setRequestProperty("Content-Type", it) }
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        return HttpResult(code, text, conn.getHeaderField("ETag"))
    } catch (e: IOException) {
        throw SyncException(SyncException.Kind.NETWORK, "Network error", e)
    } finally {
        conn.disconnect()
    }
}
