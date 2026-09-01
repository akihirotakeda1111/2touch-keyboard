package com.example.twotouchkeyboard.update

import com.example.twotouchkeyboard.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface UpdateMetadataSource {
    suspend fun fetch(): String
}

class GitHubUpdateMetadataSource(
    private val endpoint: String = UpdateConfig.latestMetadataUrl,
) : UpdateMetadataSource {
    override suspend fun fetch(): String = withContext(Dispatchers.IO) {
        val connection = openHttpsConnection(endpoint)
        try {
            connection.connectTimeout = UpdateConfig.METADATA_CONNECT_TIMEOUT_MS
            connection.readTimeout = UpdateConfig.METADATA_READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "User-Agent",
                "2Touch-Keyboard/${BuildConfig.VERSION_NAME}",
            )

            val statusCode = connection.responseCode
            if (statusCode != HttpsURLConnection.HTTP_OK) {
                throw UpdateHttpException(statusCode)
            }
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                throw IOException("Update metadata redirected to a non-HTTPS URL")
            }
            if (connection.contentLengthLong > UpdateConfig.MAX_METADATA_BYTES) {
                throw IOException("Update metadata is too large")
            }

            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                    if (totalBytes > UpdateConfig.MAX_METADATA_BYTES) {
                        throw IOException("Update metadata is too large")
                    }
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttpsConnection(rawUrl: String): HttpsURLConnection {
        val connection = URL(rawUrl).openConnection()
        return connection as? HttpsURLConnection
            ?: throw IOException("Update metadata URL must use HTTPS")
    }
}

class UpdateHttpException(
    val statusCode: Int,
) : IOException("Update request failed with HTTP $statusCode")

object UpdateVersionPolicy {
    fun isUpdateAvailable(installedVersionCode: Long, latestVersionCode: Long): Boolean {
        return latestVersionCode > installedVersionCode
    }
}

sealed interface UpdateCheckResult {
    data class Available(val metadata: UpdateMetadata) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val error: Exception) : UpdateCheckResult
}

class UpdateRepository(
    private val source: UpdateMetadataSource = GitHubUpdateMetadataSource(),
    private val parser: UpdateMetadataParser = UpdateMetadataParser(),
) {
    suspend fun checkForUpdate(installedVersionCode: Long): UpdateCheckResult {
        return try {
            val metadata = parser.parse(source.fetch())
            if (UpdateVersionPolicy.isUpdateAvailable(installedVersionCode, metadata.versionCode)) {
                UpdateCheckResult.Available(metadata)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            UpdateCheckResult.Failed(error)
        }
    }
}
