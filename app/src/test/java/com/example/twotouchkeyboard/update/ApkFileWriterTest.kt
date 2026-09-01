package com.example.twotouchkeyboard.update

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkFileWriterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun write_wrapsFileCreationFailure_asLocalStorageFailure() {
        val writer = ApkFileWriter(
            outputStreamFactory = { throw IOException("cache is unavailable") },
        )

        assertThrows(LocalApkWriteException::class.java) {
            writer.write(
                input = ByteArrayInputStream(byteArrayOf(1)),
                destination = temporaryFolder.newFile("update.part"),
                digest = sha256(),
                maxBytes = 1024,
            )
        }
    }

    @Test
    fun write_wrapsOutputFailure_asLocalStorageFailure() {
        val writer = ApkFileWriter(
            outputStreamFactory = { failingOutputStream() },
        )

        assertThrows(LocalApkWriteException::class.java) {
            writer.write(
                input = ByteArrayInputStream(byteArrayOf(1)),
                destination = temporaryFolder.newFile("update.part"),
                digest = sha256(),
                maxBytes = 1024,
            )
        }
    }

    @Test
    fun write_wrapsCloseFailure_asLocalStorageFailure() {
        val writer = ApkFileWriter(
            outputStreamFactory = {
                object : ByteArrayOutputStream() {
                    override fun close() {
                        throw IOException("buffer flush failed")
                    }
                }
            },
        )

        assertThrows(LocalApkWriteException::class.java) {
            writer.write(
                input = ByteArrayInputStream(byteArrayOf(1)),
                destination = temporaryFolder.newFile("update.part"),
                digest = sha256(),
                maxBytes = 1024,
            )
        }
    }

    @Test
    fun write_doesNotClassifyInputFailure_asLocalStorageFailure() {
        val writer = ApkFileWriter(
            outputStreamFactory = { ByteArrayOutputStream() },
        )
        val failingInput = object : InputStream() {
            override fun read(): Int = throw IOException("connection lost")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                throw IOException("connection lost")
            }
        }

        val error = assertThrows(IOException::class.java) {
            writer.write(
                input = failingInput,
                destination = temporaryFolder.newFile("update.part"),
                digest = sha256(),
                maxBytes = 1024,
            )
        }

        assertFalse(error is LocalApkWriteException)
    }

    private fun failingOutputStream(): OutputStream {
        return object : OutputStream() {
            override fun write(value: Int) {
                throw IOException("cache is full")
            }
        }
    }

    private fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")
}
