package com.example.twotouchkeyboard.update

import com.example.twotouchkeyboard.BuildConfig
import java.net.URI

internal object UpdateConfig {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val METADATA_CONNECT_TIMEOUT_MS = 10_000
    const val METADATA_READ_TIMEOUT_MS = 10_000
    const val DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000
    const val DOWNLOAD_READ_TIMEOUT_MS = 60_000
    const val MAX_METADATA_BYTES = 128 * 1024
    const val MAX_APK_BYTES = 250L * 1024L * 1024L

    val latestMetadataUrl: String =
        "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/latest/download/latest.json"

    fun isAllowedApkUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (!uri.host.equals("github.com", ignoreCase = true)) return false
        if (uri.port != -1 && uri.port != 443) return false
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return false

        val rawPath = uri.rawPath ?: return false
        if ('%' in rawPath || "//" in rawPath || "/../" in rawPath) return false

        val prefix = "/${BuildConfig.UPDATE_REPOSITORY}/releases/download/"
        if (!rawPath.startsWith(prefix)) return false

        val releaseAssetPath = rawPath.removePrefix(prefix).split('/')
        return releaseAssetPath.size == 2 &&
            releaseAssetPath.all { it.isNotBlank() && it != "." && it != ".." } &&
            releaseAssetPath.last().endsWith(".apk", ignoreCase = true)
    }
}
