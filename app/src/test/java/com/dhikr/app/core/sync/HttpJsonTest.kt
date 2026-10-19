package com.dhikr.app.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpJsonTest {

    @Test
    fun mapsStatusCodes() {
        assertNull(mapHttpStatus(200))
        assertNull(mapHttpStatus(204))
        assertEquals(SyncException.Kind.AUTH_EXPIRED, mapHttpStatus(401)?.kind)
        assertEquals(SyncException.Kind.CONFLICT, mapHttpStatus(412)?.kind)
        assertEquals(SyncException.Kind.QUOTA, mapHttpStatus(429)?.kind)
        assertEquals(SyncException.Kind.QUOTA, mapHttpStatus(403, "rateLimitExceeded")?.kind)
        assertEquals(SyncException.Kind.QUOTA, mapHttpStatus(403, "storageQuotaExceeded")?.kind)
        assertEquals(SyncException.Kind.AUTH_EXPIRED, mapHttpStatus(403, "insufficientPermissions")?.kind)
        assertEquals(SyncException.Kind.UNKNOWN, mapHttpStatus(500)?.kind)
    }
}
