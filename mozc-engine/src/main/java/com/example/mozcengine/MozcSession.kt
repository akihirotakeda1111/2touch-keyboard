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

/**
 * Mozc session wrapper with incremental composition updates.
 *
 * Uses [SessionCommand.CommandType.UPDATE_COMPOSITION] to replace the current
 * composition without resetting context on every keystroke, which allows Mozc
 * prediction/conversion to work for both Japanese and English input.
 */
class MozcSession private constructor(
    private var sessionId: Long,
) {
    private var lastMozcInput: String = ""
    private var lastMode: ConversionMode? = null

    fun convert(input: String, mode: ConversionMode): List<String> {
        if (input.isEmpty()) {
            resetSession()
            return emptyList()
        }

        val mozcInput = normalizeInput(input, mode)
        ensureMode(mode)

        val output = when {
            mozcInput == lastMozcInput && mode == lastMode -> {
                updateComposition(mozcInput)
            }
            lastMozcInput.isNotEmpty() &&
                mozcInput.startsWith(lastMozcInput) &&
                mode == lastMode -> {
                appendText(mozcInput.substring(lastMozcInput.length), mode)
            }
            lastMozcInput.isNotEmpty() &&
                lastMozcInput.startsWith(mozcInput) &&
                mode == lastMode -> {
                deleteText(lastMozcInput.length - mozcInput.length)
                if (mozcInput.isEmpty()) {
                    resetSession()
                    return emptyList()
                }
                updateComposition(mozcInput)
            }
            else -> {
                resetContext()
                switchCompositionMode(mode)
                updateComposition(mozcInput)
            }
        }

        lastMozcInput = mozcInput
        lastMode = mode
        return extractCandidates(output, input, mode)
    }

    fun resetSession() {
        resetContext()
        lastMozcInput = ""
        lastMode = null
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
        lastMozcInput = ""
        lastMode = null
    }

    private fun ensureMode(mode: ConversionMode) {
        if (lastMode == mode) return
        if (lastMode != null) {
            resetContext()
        }
        switchCompositionMode(mode)
        lastMozcInput = ""
        lastMode = mode
    }

    private fun normalizeInput(input: String, mode: ConversionMode): String {
        return when (mode) {
            ConversionMode.ALPHABET -> AlphabetPredictionSupport.lookupInput(input)
            else -> input
        }
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

    private fun switchCompositionMode(mode: ConversionMode) {
        evaluate(
            Input.newBuilder()
                .setId(sessionId)
                .setType(Input.CommandType.SEND_COMMAND)
                .setCommand(
                    SessionCommand.newBuilder()
                        .setType(SessionCommand.CommandType.SWITCH_COMPOSITION_MODE)
                        .setCompositionMode(toCompositionMode(mode)),
                )
                .build(),
        )
    }

    private fun updateComposition(text: String): Output {
        return evaluate(
            Input.newBuilder()
                .setId(sessionId)
                .setType(Input.CommandType.SEND_COMMAND)
                .setCommand(
                    SessionCommand.newBuilder()
                        .setType(SessionCommand.CommandType.UPDATE_COMPOSITION)
                        .addCompositionEvents(
                            SessionCommand.CompositionEvent.newBuilder()
                                .setCompositionString(text)
                                .setProbability(1.0),
                        ),
                )
                .build(),
        ).output
    }

    private fun appendText(text: String, mode: ConversionMode): Output {
        if (text.isEmpty()) {
            return evaluate(
                Input.newBuilder()
                    .setId(sessionId)
                    .setType(Input.CommandType.SEND_COMMAND)
                    .setCommand(
                        SessionCommand.newBuilder()
                            .setType(SessionCommand.CommandType.GET_STATUS),
                    )
                    .build(),
            ).output
        }
        return sendKey(buildTextInputKey(text, mode))
    }

    private fun deleteText(count: Int): Output {
        var output = evaluate(
            Input.newBuilder()
                .setId(sessionId)
                .setType(Input.CommandType.SEND_COMMAND)
                .setCommand(
                    SessionCommand.newBuilder()
                        .setType(SessionCommand.CommandType.GET_STATUS),
                )
                .build(),
        ).output
        repeat(count.coerceAtLeast(0)) {
            output = sendKey(
                KeyEvent.newBuilder()
                    .setSpecialKey(KeyEvent.SpecialKey.BACKSPACE)
                    .build(),
            )
        }
        return output
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
        return KeyEvent.newBuilder()
            .setSpecialKey(KeyEvent.SpecialKey.TEXT_INPUT)
            .setKeyString(input)
            .setInputStyle(KeyEvent.InputStyle.AS_IS)
            .setActivated(true)
            .setMode(toCompositionMode(mode))
            .build()
    }

    private fun toCompositionMode(mode: ConversionMode): CompositionMode {
        return when (mode) {
            ConversionMode.HIRAGANA -> CompositionMode.HIRAGANA
            ConversionMode.ALPHABET -> CompositionMode.HALF_ASCII
            ConversionMode.NUMBER -> CompositionMode.DIRECT
        }
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

        fun hasDictionaryData(context: Context): Boolean {
            ensureMozcLoaded(context)
            return getDataVersion().isNotEmpty()
        }

        fun getDataVersion(): String {
            return if (MozcJNI.isLoaded()) {
                MozcJNI.getDataVersion().orEmpty()
            } else {
                ""
            }
        }

        private fun ensureMozcLoaded(context: Context) {
            if (MozcJNI.isLoaded()) return

            val filesDir = context.filesDir
            val profileDir = java.io.File(filesDir, "mozc_profile").apply { mkdirs() }
            val dataFile = resolveDataFile(context, filesDir)

            MozcJNI.load(
                profileDir.absolutePath,
                dataFile?.absolutePath ?: "",
            )
            Log.i(TAG, "Mozc loaded. dataVersion=${MozcJNI.getDataVersion()}")
        }

        private fun resolveDataFile(context: Context, filesDir: java.io.File): java.io.File? {
            cachedDataFilePath?.let { path ->
                val cached = java.io.File(path)
                if (cached.isFile) return cached
            }

            val extracted = java.io.File(filesDir, MOZC_DATA_ASSET)
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

        private fun extractCandidates(
            output: Output,
            fallbackInput: String,
            mode: ConversionMode,
        ): List<String> {
            val candidates = linkedSetOf<String>()

            if (output.hasAllCandidateWords()) {
                output.allCandidateWords.candidatesList.forEach { word ->
                    val value = word.value
                    if (value.isNotEmpty()) {
                        candidates.add(value)
                    }
                }
            }

            if (output.hasCandidateWindow()) {
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
                    val includePreedit = mode != ConversionMode.ALPHABET ||
                        AlphabetPredictionSupport.isEnglishWordCandidate(preedit)
                    if (includePreedit) {
                        candidates.add(preedit)
                    }
                }
            }

            return when (mode) {
                ConversionMode.ALPHABET -> AlphabetPredictionSupport.prepareEnglishCandidates(
                    candidates.toList(),
                    fallbackInput,
                )
                else -> {
                    if (candidates.isEmpty()) {
                        listOf(fallbackInput)
                    } else {
                        candidates.toList()
                    }
                }
            }
        }
    }
}
