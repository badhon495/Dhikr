package com.dhikr.app.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder

/**
 * The sync file in the user's Drive appDataFolder (scope drive.appdata; the app
 * cannot see any other Drive file). Drive has no If-Match on media uploads, so the
 * etag is the file's md5Checksum, re-read just before writing.
 */
class GoogleDriveProvider(private val oauth: OAuthClient) : CloudProvider {

    private data class Meta(val id: String, val md5: String?)

    override suspend fun download(): RemoteFile? = withContext(Dispatchers.IO) {
        val token = oauth.freshAccessToken(ProviderId.GOOGLE)
        val meta = findFile(token) ?: return@withContext null
        val res = httpCall("GET", "$FILES/${meta.id}?alt=media", token)
        mapHttpStatus(res.code, res.body)?.let { throw it }
        RemoteFile(res.body, meta.md5)
    }

    override suspend fun upload(content: String, ifMatch: String?): String = withContext(Dispatchers.IO) {
        val token = oauth.freshAccessToken(ProviderId.GOOGLE)
        val existing = findFile(token)
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (existing == null) {
            if (ifMatch != null) throw SyncException(SyncException.Kind.CONFLICT, "Cloud file disappeared")
            val boundary = "dhikr_sync_boundary"
            val metadata = buildJsonObject {
                put("name", FILE_NAME)
                put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("appDataFolder")) })
            }.toString()
            val body = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$boundary\r\nContent-Type: application/json\r\n\r\n").toByteArray() +
                bytes + "\r\n--$boundary--".toByteArray()
            val res = httpCall(
                "POST", "$UPLOAD?uploadType=multipart&fields=id,md5Checksum", token, body,
                "multipart/related; boundary=$boundary",
            )
            mapHttpStatus(res.code, res.body)?.let { throw it }
            md5Of(res.body)
        } else {
            if (existing.md5 != ifMatch) throw SyncException(SyncException.Kind.CONFLICT, "Cloud file changed")
            val res = httpCall(
                "PATCH", "$UPLOAD/${existing.id}?uploadType=media&fields=id,md5Checksum", token,
                bytes, "application/json",
            )
            mapHttpStatus(res.code, res.body)?.let { throw it }
            md5Of(res.body)
        }
    }

    private fun findFile(token: String): Meta? {
        val q = URLEncoder.encode("name='$FILE_NAME'", "UTF-8")
        val fields = URLEncoder.encode("files(id,md5Checksum)", "UTF-8")
        val res = httpCall("GET", "$FILES?spaces=appDataFolder&q=$q&fields=$fields", token)
        mapHttpStatus(res.code, res.body)?.let { throw it }
        val files: JsonArray = parseObject(res.body)["files"]?.jsonArray ?: return null
        val first = files.firstOrNull()?.jsonObject ?: return null
        return Meta(first.getValue("id").jsonPrimitive.content, first["md5Checksum"]?.jsonPrimitive?.contentOrNull)
    }

    private fun md5Of(body: String): String =
        parseObject(body)["md5Checksum"]?.jsonPrimitive?.contentOrNull ?: ""

    private fun parseObject(text: String): JsonObject =
        try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw SyncException(SyncException.Kind.UNKNOWN, "Unexpected response from Google Drive", e)
        }

    private companion object {
        const val FILE_NAME = "dhikr-sync.json"
        const val FILES = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    }
}
