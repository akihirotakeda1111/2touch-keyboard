package com.example.twotouchkeyboard.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.example.twotouchkeyboard.BuildConfig

data class InstalledAppVersion(
    val versionCode: Long,
    val versionName: String,
)

class InstalledAppVersionProvider(
    private val context: Context,
) {
    fun get(): InstalledAppVersion {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return InstalledAppVersion(
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            versionName = packageInfo.versionName ?: BuildConfig.VERSION_NAME,
        )
    }
}
