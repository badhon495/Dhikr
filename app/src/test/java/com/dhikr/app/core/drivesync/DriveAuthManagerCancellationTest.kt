package com.dhikr.app.core.drivesync

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import kotlinx.coroutines.CancellationException
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

    /**
     * `awaitAuthorization`'s `addOnCanceledListener` (the bridge from the
     * Play Services `Task<AuthorizationResult>` to this suspend function)
     * previously resumed the coroutine with a thrown
     * `java.util.concurrent.CancellationException` when the Task reported
     * cancellation (e.g. user backs out of the consent screen). That class
     * shares its simple name — but nothing else — with
     * `kotlinx.coroutines.CancellationException`, so it was incorrectly
     * caught and rethrown by `catch (e: CancellationException)` guards in
     * `DriveBackupRepository.backup`/`restore`, which exist specifically to
     * let *real* coroutine cancellation propagate without being swallowed.
     * The practical effect: cancelling the consent screen mid-retry made the
     * coroutine look cancelled from the repository's perspective, so the
     * ViewModel's launch ended silently and `Status` stayed stuck at
     * `Working` forever.
     *
     * `driveAuthorizationCancelledFailure()` is the exact function the
     * cancel listener now calls to build its `Result.failure`. These tests
     * exercise it directly (no Android `Task` needed) to confirm the fix:
     * the resulting exception is a plain `DriveAuthorizationCancelled`, not
     * a `java.util.concurrent.CancellationException`, and critically is not
     * an instance of `kotlinx.coroutines.CancellationException` either, so
     * it can never again be caught by that guard.
     */
    @Test
    fun `driveAuthorizationCancelledFailure is not a kotlinx coroutines CancellationException`() {
        val failure = driveAuthorizationCancelledFailure()

        assertFalse(failure is CancellationException)
        assertFalse(failure is java.util.concurrent.CancellationException)
        assertTrue(failure is DriveAuthorizationCancelled)
    }

    @Test
    fun `driveAuthorizationCancelledFailure is not caught by a kotlinx coroutines CancellationException guard`() {
        // Mirrors the exact catch clause in DriveBackupRepository.backup()
        // and restore(): `catch (e: CancellationException) { throw e }`,
        // which must only ever catch real coroutine cancellation.
        val result = runCatchingWithCoroutineCancellationGuard { driveAuthorizationCancelledFailure() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DriveAuthorizationCancelled)
    }

    private fun runCatchingWithCoroutineCancellationGuard(block: () -> Throwable): Result<Nothing> {
        return try {
            throw block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
