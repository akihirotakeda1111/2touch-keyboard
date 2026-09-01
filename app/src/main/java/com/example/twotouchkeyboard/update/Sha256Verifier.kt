package com.example.twotouchkeyboard.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Sha256Verifier {
    private val expectedHashPattern = Regex("[0-9a-fA-F]{64}")

    fun verify(file: File, expectedHash: String): Boolean {
        if (!expectedHashPattern.matches(expectedHash)) return false
        val actualHash = file.inputStream().buffered().use(::calculate)
        return MessageDigest.isEqual(
            actualHash.hexToBytes(),
            expectedHash.lowercase().hexToBytes(),
        )
    }

    fun calculate(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun String.hexToBytes(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
