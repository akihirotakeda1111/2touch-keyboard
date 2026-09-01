package com.example.twotouchkeyboard.update

import android.content.Context
import com.example.twotouchkeyboard.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class UpdateDownloadFailure {
    NETWORK,
    HTTP,
    INTEGRITY,
    STORAGE,
}

sealed interface UpdateDownloadResult {
    data class Downloaded(val apkFile: File) : UpdateDownloadResult
    data class Failed(
        val reason: UpdateDownloadFailure,
        val error: Exception? = null,
    ) : UpdateDownloadResult
}

class UpdateDownloader(
    context: Context,
) {
    private val updateDirectory = File(context.cacheDir, UPDATE_DIRECTORY_NAME)

    suspend fun cleanup() = withContext(Dispatchers.IO) {
        updateDirectory.listFiles()?.forEach { file -> file.delete() }
    }

    suspend fun download(metadata: UpdateMetadata): UpdateDownloadResult = withContext(Dispatchers.IO) {
        if (!UpdateConfig.isAllowedApkUrl(metadata.apkUrl)) {
            return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.NETWORK)
        }
        if (!prepareEmptyUpdateDirectory()) {
            return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.STORAGE)
        }

        val targetFile = File(updateDirectory, "update-${metadata.versionCode}.apk")
        val partialFile = File(updateDirectory, "update-${metadata.versionCode}.part")
        val connection = try {
            openHttpsConnection(metadata.apkUrl)
        } catch (error: Exception) {
            return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.NETWORK, error)
        }

        try {
            connection.connectTimeout = UpdateConfig.DOWNLOAD_CONNECT_TIMEOUT_MS
            connection.readTimeout = UpdateConfig.DOWNLOAD_READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
            connection.setRequestProperty(
                "User-Agent",
                "2Touch-Keyboard/${BuildConfig.VERSION_NAME}",
            )

            val statusCode = connection.responseCode
            if (statusCode != HttpsURLConnection.HTTP_OK) {
                return@withContext UpdateDownloadResult.Failed(
                    UpdateDownloadFailure.HTTP,
                    UpdateHttpException(statusCode),
                )
            }
            if (!connection.url.protocol.equals("https", ignoreCase = true)) {
                return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.NETWORK)
            }

            val contentLength = connection.contentLengthLong
            if (contentLength == 0L || contentLength > UpdateConfig.MAX_APK_BYTES) {
                return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.NETWORK)
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partialFile).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        totalBytes += read
                        if (totalBytes > UpdateConfig.MAX_APK_BYTES) {
                            throw IOException("Downloaded APK exceeds the size limit")
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (totalBytes == 0L || (contentLength > 0L && contentLength != totalBytes)) {
                return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.NETWORK)
            }

            val actualHash = digest.digest().joinToString(separator = "") { byte ->
                "%02x".format(byte)
            }
            if (!hashesMatch(actualHash, metadata.sha256)) {
                return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.INTEGRITY)
            }

            if (targetFile.exists() && !targetFile.delete()) {
                return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.STORAGE)
            }
            if (!partialFile.renameTo(targetFile)) {
                return@withContext UpdateDownloadResult.Failed(UpdateDownloadFailure.STORAGE)
            }
            UpdateDownloadResult.Downloaded(targetFile)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            UpdateDownloadResult.Failed(UpdateDownloadFailure.NETWORK, error)
        } finally {
            connection.disconnect()
            if (partialFile.exists()) partialFile.delete()
        }
    }

    suspend fun verify(apkFile: File, expectedHash: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { isManagedApkFile(apkFile) && Sha256Verifier.verify(apkFile, expectedHash) }
            .getOrDefault(false)
    }

    suspend fun delete(apkFile: File) = withContext(Dispatchers.IO) {
        if (isManagedApkFile(apkFile)) apkFile.delete()
    }

    private fun prepareEmptyUpdateDirectory(): Boolean {
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) return false
        if (!updateDirectory.isDirectory) return false
        return updateDirectory.listFiles()?.all { file -> !file.exists() || file.delete() } ?: true
    }

    private fun isManagedApkFile(file: File): Boolean {
        val canonicalDirectory = runCatching { updateDirectory.canonicalFile }.getOrNull() ?: return false
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return canonicalFile.parentFile == canonicalDirectory &&
            canonicalFile.name.endsWith(".apk", ignoreCase = true) &&
            canonicalFile.isFile &&
            canonicalFile.length() > 0L
    }

    private fun openHttpsConnection(rawUrl: String): HttpsURLConnection {
        val connection = URL(rawUrl).openConnection()
        return connection as? HttpsURLConnection
            ?: throw IOException("APK URL must use HTTPS")
    }

    private fun hashesMatch(actualHash: String, expectedHash: String): Boolean {
        if (!expectedHash.matches(Regex("[0-9a-fA-F]{64}"))) return false
        return MessageDigest.isEqual(
            actualHash.hexToBytes(),
            expectedHash.lowercase().hexToBytes(),
        )
    }

    private fun String.hexToBytes(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private companion object {
        const val UPDATE_DIRECTORY_NAME = "updates"
    }
}
