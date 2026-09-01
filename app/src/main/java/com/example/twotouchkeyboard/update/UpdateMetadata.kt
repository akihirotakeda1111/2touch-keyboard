package com.example.twotouchkeyboard.update

import org.json.JSONException
import org.json.JSONObject

data class UpdateMetadata(
    val schemaVersion: Int,
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val publishedAt: String?,
)

class InvalidUpdateMetadataException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class UpdateMetadataParser(
    private val apkUrlValidator: (String) -> Boolean = UpdateConfig::isAllowedApkUrl,
) {
    fun parse(jsonText: String): UpdateMetadata {
        if (jsonText.toByteArray(Charsets.UTF_8).size > UpdateConfig.MAX_METADATA_BYTES) {
            throw InvalidUpdateMetadataException("Update metadata is too large")
        }

        val json = try {
            JSONObject(jsonText)
        } catch (error: JSONException) {
            throw InvalidUpdateMetadataException("Update metadata is not valid JSON", error)
        }

        val schemaVersion = json.requiredInteger("schemaVersion")
        if (schemaVersion != UpdateConfig.SUPPORTED_SCHEMA_VERSION.toLong()) {
            throw InvalidUpdateMetadataException("Unsupported schemaVersion: $schemaVersion")
        }

        val versionCode = json.requiredInteger("versionCode")
        if (versionCode !in 1..Int.MAX_VALUE.toLong()) {
            throw InvalidUpdateMetadataException("versionCode is out of range")
        }

        val versionName = json.requiredString("versionName", maxLength = 100)
        val apkUrl = json.requiredString("apkUrl", maxLength = 2_048)
        if (!apkUrlValidator(apkUrl)) {
            throw InvalidUpdateMetadataException("apkUrl is not an allowed GitHub Release URL")
        }

        val sha256 = json.requiredString("sha256", maxLength = 64).lowercase()
        if (!SHA_256_PATTERN.matches(sha256)) {
            throw InvalidUpdateMetadataException("sha256 must contain 64 hexadecimal characters")
        }

        val publishedAt = json.optionalString("publishedAt", maxLength = 100)

        return UpdateMetadata(
            schemaVersion = schemaVersion.toInt(),
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            sha256 = sha256,
            publishedAt = publishedAt,
        )
    }

    private fun JSONObject.requiredInteger(fieldName: String): Long {
        if (!has(fieldName) || isNull(fieldName)) {
            throw InvalidUpdateMetadataException("Missing required field: $fieldName")
        }
        return when (val value = get(fieldName)) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            else -> throw InvalidUpdateMetadataException("$fieldName must be an integer")
        }
    }

    private fun JSONObject.requiredString(fieldName: String, maxLength: Int): String {
        if (!has(fieldName) || isNull(fieldName)) {
            throw InvalidUpdateMetadataException("Missing required field: $fieldName")
        }
        val value = get(fieldName)
        if (value !is String || value.isBlank() || value.length > maxLength) {
            throw InvalidUpdateMetadataException("$fieldName is invalid")
        }
        if (value.any { it.isISOControl() }) {
            throw InvalidUpdateMetadataException("$fieldName contains control characters")
        }
        return value
    }

    private fun JSONObject.optionalString(fieldName: String, maxLength: Int): String? {
        if (!has(fieldName) || isNull(fieldName)) return null
        val value = get(fieldName)
        if (value !is String || value.isBlank() || value.length > maxLength) {
            throw InvalidUpdateMetadataException("$fieldName is invalid")
        }
        if (value.any { it.isISOControl() }) {
            throw InvalidUpdateMetadataException("$fieldName contains control characters")
        }
        return value
    }

    private companion object {
        val SHA_256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}
