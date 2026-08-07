package com.example.mozcengine

import android.content.Context
import android.util.Log
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Capability
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.CompositionMode
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Request
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand
import java.io.File

/**
 * Minimal Mozc session wrapper for candidate lookup.
 *
 * Sends pre-composed hiragana/alphabet strings via TEXT_INPUT and reads candidates
 * from [Output.allCandidateWords].
 */
class MozcSession private constructor(
    private var sessionId: Long,
) {
    fun convert(input: String, mode: ConversionMode): List<String> {
        if (input.isEmpty()) return emptyList()

        resetContext()
        val output = sendKey(buildTextInputKey(input, mode))
        return extractCandidates(output, input)
    }

    fun deleteSession() {
        if (sessionId == INVALID_SESSION_ID) return
        evaluate(
            Input.newBuilder()
                .setType(Input.CommandType.DELETE_SESSION)
                .setId(sessionId)
                .build(),
        )
        sessionId = INVALID_SESSION_ID
    }

    private fun resetContext() {
        evaluate(
            Input.newBuilder()
                .setId(sessionId)
                .setType(Input.CommandType.SEND_COMMAND)
                .setCommand(
                    SessionCommand.newBuilder()
                        .setType(SessionCommand.CommandType.RESET_CONTEXT),
                )
                .build(),
        )
    }

    private fun sendKey(key: KeyEvent): Output {
        return evaluate(
            Input.newBuilder()
                .setId(sessionId)
                .setType(Input.CommandType.SEND_KEY)
                .setKey(key)
                .build(),
        ).output
    }

    private fun buildTextInputKey(input: String, mode: ConversionMode): KeyEvent {
        val builder = KeyEvent.newBuilder()
            .setSpecialKey(KeyEvent.SpecialKey.TEXT_INPUT)
            .setKeyString(input)
            .setInputStyle(KeyEvent.InputStyle.AS_IS)

        when (mode) {
            ConversionMode.HIRAGANA -> builder.mode = CompositionMode.HIRAGANA
            ConversionMode.ALPHABET -> builder.mode = CompositionMode.HALF_ASCII
            ConversionMode.NUMBER -> builder.mode = CompositionMode.DIRECT
        }
        return builder.build()
    }

    private fun evaluate(input: Input): Command {
        val command = Command.newBuilder()
            .setInput(input)
            .build()
        val responseBytes = MozcJNI.evalCommand(command.toByteArray())
        return Command.parseFrom(responseBytes)
    }

    companion object {
        private const val TAG = "MozcSession"
        private const val INVALID_SESSION_ID = 0L
        private const val MOZC_DATA_ASSET = "mozc.data"

        @Volatile
        private var cachedDataFilePath: String? = null

        fun open(context: Context): MozcSession {
            ensureMozcLoaded(context)
            val createOutput = evaluateGlobal(
                Input.newBuilder()
                    .setType(Input.CommandType.CREATE_SESSION)
                    .setCapability(
                        Capability.newBuilder()
                            .setTextDeletion(Capability.TextDeletionCapabilityType.DELETE_PRECEDING_TEXT),
                    )
                    .build(),
            ).output
            val sessionId = createOutput.id

            evaluateGlobal(
                Input.newBuilder()
                    .setId(sessionId)
                    .setType(Input.CommandType.SET_REQUEST)
                    .setRequest(buildSoftwareKeyboardRequest())
                    .build(),
            )

            return MozcSession(sessionId)
        }

        private fun ensureMozcLoaded(context: Context) {
            if (MozcJNI.isLoaded()) return

            val filesDir = context.filesDir
            val profileDir = File(filesDir, "mozc_profile").apply { mkdirs() }
            val dataFile = resolveDataFile(context, filesDir)

            MozcJNI.load(
                profileDir.absolutePath,
                dataFile?.absolutePath ?: "",
            )
            Log.i(TAG, "Mozc loaded. dataVersion=${MozcJNI.getDataVersion()}")
        }

        private fun resolveDataFile(context: Context, filesDir: File): File? {
            cachedDataFilePath?.let { path ->
                val cached = File(path)
                if (cached.isFile) return cached
            }

            val extracted = File(filesDir, MOZC_DATA_ASSET)
            if (extracted.isFile) {
                cachedDataFilePath = extracted.absolutePath
                return extracted
            }

            return try {
                context.assets.open(MOZC_DATA_ASSET).use { input ->
                    extracted.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                cachedDataFilePath = extracted.absolutePath
                extracted
            } catch (_: Exception) {
                Log.w(TAG, "mozc.data not found in assets; using minimal engine")
                null
            }
        }

        private fun buildSoftwareKeyboardRequest(): Request {
            return Request.newBuilder()
                .setMixedConversion(true)
                .setZeroQuerySuggestion(true)
                .setUpdateInputModeFromSurroundingText(false)
                .setAutoPartialSuggestion(true)
                .build()
        }

        private fun evaluateGlobal(input: Input): Command {
            val command = Command.newBuilder()
                .setInput(input)
                .build()
            val responseBytes = MozcJNI.evalCommand(command.toByteArray())
            return Command.parseFrom(responseBytes)
        }

        private fun extractCandidates(output: Output, fallbackInput: String): List<String> {
            val candidates = mutableListOf<String>()

            if (output.hasAllCandidateWords()) {
                output.allCandidateWords.candidatesList.forEach { word ->
                    val value = word.value
                    if (value.isNotEmpty()) {
                        candidates.add(value)
                    }
                }
            }

            if (candidates.isEmpty() && output.hasCandidateWindow()) {
                output.candidateWindow.candidateList.forEach { candidate ->
                    val value = candidate.value
                    if (value.isNotEmpty()) {
                        candidates.add(value)
                    }
                }
            }

            if (candidates.isEmpty() && output.hasPreedit()) {
                val preedit = output.preedit.segmentList.joinToString("") { it.value }
                if (preedit.isNotEmpty()) {
                    candidates.add(preedit)
                }
            }

            if (candidates.isEmpty()) {
                candidates.add(fallbackInput)
            }

            return candidates.distinct()
        }
    }
}
