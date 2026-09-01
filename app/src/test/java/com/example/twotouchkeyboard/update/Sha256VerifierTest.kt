package com.example.twotouchkeyboard.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256VerifierTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun verify_returnsTrue_forMatchingHash() {
        val apkFile = temporaryFolder.newFile("update.apk").apply {
            writeText("verified APK bytes")
        }

        assertTrue(
            Sha256Verifier.verify(
                apkFile,
                "4cfe9f28927fe0de080829bc0ac1abb1745f151ee1294c8a6dc33b294748ba17",
            ),
        )
    }

    @Test
    fun verify_returnsFalse_forMismatchingHash() {
        val apkFile = temporaryFolder.newFile("update.apk").apply {
            writeText("tampered APK bytes")
        }

        assertFalse(Sha256Verifier.verify(apkFile, "0".repeat(64)))
    }
}
