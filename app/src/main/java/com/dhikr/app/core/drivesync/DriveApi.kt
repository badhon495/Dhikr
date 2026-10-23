package com.dhikr.app.core.drivesync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

private const val DRIVE_FILES_BASE = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3/files"
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class DriveFile(val id: String)

@Serializable
private data class DriveFileList(val files: List<DriveFile> = emptyList())

@Serializable
private data class DriveFileMetadata(val name: String, val mimeType: String)

/**
 * Minimal Drive v3 REST client for backup sync. The HTTP call itself is
 * injected so tests never touch the network; [defaultHttpCall] performs the
 * real call via [HttpURLConnection].
 */
class DriveApi(private val httpClient: (Request) -> Response = ::defaultHttpCall) {

    data class Request(
        val method: String,
        val url: String,
        val accessToken: String,
        val body: ByteArray? = null,
        val contentType: String? = null,
    )

    data class Response(val statusCode: Int, val body: String)

    class DriveApiException(val statusCode: Int, message: String) : Exception(message)

    /** Returns the first matching file's id, or null if none found. */
    suspend fun findFileId(accessToken: String, fileName: String): String? =
        withContext(Dispatchers.IO) {
            val query = "name='$fileName' and trashed=false"
            val url = "$DRIVE_FILES_BASE?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&spaces=drive&fields=${URLEncoder.encode("files(id)", "UTF-8")}"
            val response = httpClient(Request(method = "GET", url = url, accessToken = accessToken))
            checkSuccess(response)
            val parsed = json.decodeFromString(DriveFileList.serializer(), response.body)
            parsed.files.firstOrNull()?.id
        }

    /**
     * Creates a new file (multipart upload) when [existingFileId] is null,
     * returning the new id; otherwise updates the existing file's content in
     * place (media upload) and returns [existingFileId] unchanged.
     */
    suspend fun uploadOrUpdate(
        accessToken: String,
        existingFileId: String?,
        fileName: String,
        jsonContent: String,
    ): String = withContext(Dispatchers.IO) {
        if (existingFileId == null) {
            val boundary = "drive_sync_${UUID.randomUUID()}"
            val metadata = json.encodeToString(
                DriveFileMetadata.serializer(),
                DriveFileMetadata(name = fileName, mimeType = "application/json"),
            )
            val body = buildString {
                append("--$boundary\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadata)
                append("\r\n--$boundary\r\n")
                append("Content-Type: application/json\r\n\r\n")
                append(jsonContent)
                append("\r\n--$boundary--")
            }
            val url = "$DRIVE_UPLOAD_BASE?uploadType=multipart"
            val response = httpClient(
                Request(
                    method = "POST",
                    url = url,
                    accessToken = accessToken,
                    body = body.toByteArray(Charsets.UTF_8),
                    contentType = "multipart/related; boundary=$boundary",
                ),
            )
            checkSuccess(response)
            val parsed = json.decodeFromString(DriveFile.serializer(), response.body)
            parsed.id
        } else {
            val url = "$DRIVE_UPLOAD_BASE/$existingFileId?uploadType=media"
            val response = httpClient(
                Request(
                    method = "PATCH",
                    url = url,
                    accessToken = accessToken,
                    body = jsonContent.toByteArray(Charsets.UTF_8),
                    contentType = "application/json",
                ),
            )
            checkSuccess(response)
            existingFileId
        }
    }

    /** Returns the raw file content for [fileId]. */
    suspend fun downloadContent(accessToken: String, fileId: String): String =
        withContext(Dispatchers.IO) {
            val url = "$DRIVE_FILES_BASE/$fileId?alt=media"
            val response = httpClient(Request(method = "GET", url = url, accessToken = accessToken))
            checkSuccess(response)
            response.body
        }

    private fun checkSuccess(response: Response) {
        if (response.statusCode !in 200..299) {
            throw DriveApiException(response.statusCode, "HTTP ${response.statusCode}: ${response.body.take(300)}")
        }
    }
}

/** Real HTTP implementation, used outside tests. */
fun defaultHttpCall(request: DriveApi.Request): DriveApi.Response {
    val conn = URL(request.url).openConnection() as HttpURLConnection
    return try {
        conn.requestMethod = request.method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Authorization", "Bearer ${request.accessToken}")
        request.contentType?.let { conn.setRequestProperty("Content-Type", it) }
        if (request.body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(request.body) }
        }
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        DriveApi.Response(status, body)
    } catch (e: IOException) {
        throw DriveApi.DriveApiException(-1, e.message ?: "network error")
    } finally {
        conn.disconnect()
    }
}
