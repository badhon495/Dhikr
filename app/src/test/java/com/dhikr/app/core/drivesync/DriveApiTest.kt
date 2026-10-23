package com.dhikr.app.core.drivesync

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveApiTest {

    @Test
    fun `findFileId returns null when files array empty`() = runTest {
        val api = DriveApi(httpClient = { DriveApi.Response(200, """{"files":[]}""") })
        assertNull(api.findFileId("token", "dhikr_backup.json"))
    }

    @Test
    fun `findFileId returns first file id when present`() = runTest {
        val api = DriveApi(httpClient = { DriveApi.Response(200, """{"files":[{"id":"abc123"}]}""") })
        assertEquals("abc123", api.findFileId("token", "dhikr_backup.json"))
    }

    @Test
    fun `findFileId throws DriveApiException on non-2xx`() = runTest {
        val api = DriveApi(httpClient = { DriveApi.Response(401, """{"error":"invalid_token"}""") })
        assertThrows(DriveApi.DriveApiException::class.java) {
            runBlocking { api.findFileId("token", "dhikr_backup.json") }
        }
    }

    @Test
    fun `uploadOrUpdate with null existingFileId creates and returns new id`() = runTest {
        val api = DriveApi(httpClient = { req ->
            assertTrue(req.url.contains("uploadType=multipart"))
            DriveApi.Response(200, """{"id":"newid456"}""")
        })
        val id = api.uploadOrUpdate("token", null, "dhikr_backup.json", "{}")
        assertEquals("newid456", id)
    }

    @Test
    fun `uploadOrUpdate with existingFileId patches and returns same id`() = runTest {
        val api = DriveApi(httpClient = { req ->
            assertTrue(req.url.contains("/files/abc123"))
            assertEquals("PATCH", req.method)
            DriveApi.Response(200, "{}")
        })
        val id = api.uploadOrUpdate("token", "abc123", "dhikr_backup.json", "{}")
        assertEquals("abc123", id)
    }

    @Test
    fun `downloadContent returns raw body`() = runTest {
        val api = DriveApi(httpClient = { DriveApi.Response(200, "raw backup json") })
        assertEquals("raw backup json", api.downloadContent("token", "abc123"))
    }

    @Test
    fun `downloadContent throws on 404`() = runTest {
        val api = DriveApi(httpClient = { DriveApi.Response(404, "") })
        assertThrows(DriveApi.DriveApiException::class.java) {
            runBlocking { api.downloadContent("token", "missing") }
        }
    }
}
