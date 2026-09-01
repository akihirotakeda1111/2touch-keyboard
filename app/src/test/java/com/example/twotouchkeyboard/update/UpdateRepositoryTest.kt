package com.example.twotouchkeyboard.update

import com.example.twotouchkeyboard.BuildConfig
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateRepositoryTest {
    @Test
    fun checkForUpdate_returnsAvailable_forNewerVersion() = runBlocking {
        val repository = UpdateRepository(source = UpdateMetadataSource { validJson() })

        assertTrue(repository.checkForUpdate(10) is UpdateCheckResult.Available)
    }

    @Test
    fun checkForUpdate_containsTimeoutFailure() = runBlocking {
        val repository = UpdateRepository(
            source = UpdateMetadataSource { throw SocketTimeoutException("timeout") },
        )

        assertTrue(repository.checkForUpdate(10) is UpdateCheckResult.Failed)
    }

    @Test
    fun checkForUpdate_containsConnectionFailure() = runBlocking {
        val repository = UpdateRepository(
            source = UpdateMetadataSource { throw ConnectException("offline") },
        )

        assertTrue(repository.checkForUpdate(10) is UpdateCheckResult.Failed)
    }

    @Test
    fun checkForUpdate_containsHttpFailure() = runBlocking {
        val repository = UpdateRepository(
            source = UpdateMetadataSource { throw UpdateHttpException(503) },
        )

        assertTrue(repository.checkForUpdate(10) is UpdateCheckResult.Failed)
    }

    private fun validJson(): String =
        """
            {
              "schemaVersion": 1,
              "versionCode": 11,
              "versionName": "1.1.0",
              "apkUrl": "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v1.1.0/app-1.1.0.apk",
              "sha256": "${"b".repeat(64)}"
            }
        """.trimIndent()
}
