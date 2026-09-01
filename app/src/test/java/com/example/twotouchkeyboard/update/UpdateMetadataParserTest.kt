package com.example.twotouchkeyboard.update

import com.example.twotouchkeyboard.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateMetadataParserTest {
    private val parser = UpdateMetadataParser()

    @Test
    fun parse_returnsMetadata_forValidJson() {
        val metadata = parser.parse(validJson())

        assertEquals(1, metadata.schemaVersion)
        assertEquals(16L, metadata.versionCode)
        assertEquals("1.6.0", metadata.versionName)
        assertEquals("a".repeat(64), metadata.sha256)
        assertEquals("2026-09-01T00:00:00Z", metadata.publishedAt)
    }

    @Test
    fun parse_rejectsMalformedJson() {
        assertThrows(InvalidUpdateMetadataException::class.java) {
            parser.parse("{not-json")
        }
    }

    @Test
    fun parse_rejectsMissingRequiredField() {
        assertThrows(InvalidUpdateMetadataException::class.java) {
            parser.parse(
                validJson().replace(
                    "\"sha256\": \"${"a".repeat(64)}\"",
                    "\"notSha256\": \"${"a".repeat(64)}\"",
                ),
            )
        }
    }

    @Test
    fun parse_rejectsUnsupportedSchemaVersion() {
        assertThrows(InvalidUpdateMetadataException::class.java) {
            parser.parse(validJson().replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        }
    }

    @Test
    fun parse_rejectsStringVersionCode() {
        assertThrows(InvalidUpdateMetadataException::class.java) {
            parser.parse(validJson().replace("\"versionCode\": 16", "\"versionCode\": \"16\""))
        }
    }

    @Test
    fun parse_rejectsApkFromAnotherRepository() {
        assertThrows(InvalidUpdateMetadataException::class.java) {
            parser.parse(
                validJson().replace(
                    "https://github.com/${BuildConfig.UPDATE_REPOSITORY}",
                    "https://github.com/attacker/other-repository",
                ),
            )
        }
    }

    private fun validJson(): String =
        """
            {
              "schemaVersion": 1,
              "versionCode": 16,
              "versionName": "1.6.0",
              "apkUrl": "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v1.6.0/app-1.6.0.apk",
              "sha256": "${"a".repeat(64)}",
              "publishedAt": "2026-09-01T00:00:00Z"
            }
        """.trimIndent()
}
