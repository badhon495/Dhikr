package com.dhikr.app.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The sync file in the user's OneDrive App Folder (scope Files.ReadWrite.AppFolder,
 * i.e. /Apps/<app name>/ ; no other OneDrive file is visible). Microsoft Graph supports
 * If-Match on upload, so the etag is the driveItem eTag.
 */
class OneDriveProvider(private val oauth: OAuthClient) : CloudProvider {

    override suspend fun download(): RemoteFile? = withContext(Dispatchers.IO) {
        val token = oauth.freshAccessToken(ProviderId.MICROSOFT)
        val meta = httpCall("GET", ITEM, token)
        if (meta.code == 404) return@withContext null
        mapHttpStatus(meta.code, meta.body)?.let { throw it }
        val etag = extractEtag(meta.body)
        val content = httpCall("GET", "$ITEM:/content", token)
        if (content.code == 404) return@withContext null
        mapHttpStatus(content.code, content.body)?.let { throw it }
        RemoteFile(content.body, etag)
    }

    override suspend fun upload(content: String, ifMatch: String?): String = withContext(Dispatchers.IO) {
        val token = oauth.freshAccessToken(ProviderId.MICROSOFT)
        val headers = if (ifMatch != null) mapOf("If-Match" to ifMatch) else emptyMap()
        val res = httpCall(
            "PUT", "$ITEM:/content", token,
            content.toByteArray(Charsets.UTF_8), "application/json", headers,
        )
        mapHttpStatus(res.code, res.body)?.let { throw it }
        extractEtag(res.body) ?: ""
    }

    private fun extractEtag(body: String): String? =
        try {
            kotlinx.serialization.json.Json.parseToJsonElement(body)
                .let { it as? kotlinx.serialization.json.JsonObject }
                ?.get("eTag")?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
        } catch (e: Exception) {
            null
        }

    private companion object {
        const val ITEM = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/dhikr-sync.json"
    }
}
