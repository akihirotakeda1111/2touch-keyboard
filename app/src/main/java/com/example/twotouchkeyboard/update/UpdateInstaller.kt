package com.example.twotouchkeyboard.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.twotouchkeyboard.BuildConfig
import java.io.File

enum class UpdateInstallFailure {
    INVALID_APK,
    INSTALLER_UNAVAILABLE,
}

sealed interface UpdateInstallResult {
    data object Started : UpdateInstallResult
    data class Failed(
        val reason: UpdateInstallFailure,
        val error: Exception? = null,
    ) : UpdateInstallResult
}

class UpdateInstaller(
    private val context: Context,
) {
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS,
                0,
            ) == 1
        }
    }

    fun createInstallPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createPerSourceInstallPermissionIntent()
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPerSourceInstallPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
    }

    fun install(apkFile: File, metadata: UpdateMetadata): UpdateInstallResult {
        if (!isExpectedUpdateApk(apkFile, metadata)) {
            return UpdateInstallResult.Failed(UpdateInstallFailure.INVALID_APK)
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.update.fileprovider",
                apkFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, APK_MIME_TYPE)
                clipData = ClipData.newRawUri("APK update", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            UpdateInstallResult.Started
        } catch (error: Exception) {
            UpdateInstallResult.Failed(UpdateInstallFailure.INSTALLER_UNAVAILABLE, error)
        }
    }

    private fun isExpectedUpdateApk(apkFile: File, metadata: UpdateMetadata): Boolean {
        val updateDirectory = runCatching {
            File(context.cacheDir, UPDATE_DIRECTORY_NAME).canonicalFile
        }.getOrNull() ?: return false
        val canonicalApk = runCatching { apkFile.canonicalFile }.getOrNull() ?: return false
        if (canonicalApk.parentFile != updateDirectory ||
            !canonicalApk.isFile ||
            canonicalApk.length() <= 0L ||
            !canonicalApk.name.endsWith(".apk", ignoreCase = true)
        ) {
            return false
        }

        val packageInfo = context.packageManager.getPackageArchiveInfo(canonicalApk.path, 0)
            ?: return false
        return packageInfo.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(packageInfo) == metadata.versionCode
    }

    private companion object {
        const val UPDATE_DIRECTORY_NAME = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
