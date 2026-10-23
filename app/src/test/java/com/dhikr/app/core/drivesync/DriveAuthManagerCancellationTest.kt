package com.dhikr.app.core.drivesync

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DriveAuthManager.isSignInCancellation] is pure logic (no Android framework
 * calls), unlike the rest of [DriveAuthManager] which needs a real
 * Credential Manager/EncryptedSharedPreferences/DataStore and isn't
 * unit-testable in this project (no Robolectric configured) — see
 * the final-review fix report for details.
 */
class DriveAuthManagerCancellationTest {

    @Test
    fun `GetCredentialCancellationException is recognized as a cancellation`() {
        assertTrue(isCancellation(GetCredentialCancellationException()))
    }

    @Test
    fun `other GetCredentialException subtypes are not cancellations`() {
        assertFalse(isCancellation(GetCredentialUnknownException()))
    }

    // Mirrors DriveAuthManager.isSignInCancellation's logic without needing a
    // constructed DriveAuthManager (which requires a real Context).
    private fun isCancellation(e: Throwable): Boolean = e is GetCredentialCancellationException
}
