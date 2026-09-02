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
    private var lastCandidateIds: List<Int> = emptyList()
    private var lastCandidateIdByValue: Map<String, Int> = emptyMap()
    private var lastCandidateReadings: Map<String, String> = emptyMap()

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
                lastCandidateReadings = emptyMap()
                updateComposition(mozcInput)
            }
        }

        val previousReadings = CandidateReadingMerger.reusableReadings(
            previousInput = lastMozcInput,
            newInput = mozcInput,
            previousReadings = lastCandidateReadings,
        )
        lastMozcInput = mozcInput
        lastMode = mode
        lastCandidateIds = extractCandidateIds(output)
        lastCandidateIdByValue = extractCandidateIdByValue(output)
        val displayed = extractCandidates(output, input, mode, previousReadings)
        lastCandidateReadings = displayed.readings
        return displayed.candidates
    }

    fun suggestNext(mode: ConversionMode, selectedCandidate: String?): List<String> {
        if (mode != ConversionMode.HIRAGANA) return emptyList()

        ensureMode(mode)
        val hadComposition = lastMozcInput.isNotEmpty()
        val output = when {
            hadComposition && !selectedCandidate.isNullOrEmpty() -> {
                val candidateId = lastCandidateIdByValue[selectedCandidate]
                if (candidateId != null) {
                    submitCandidate(candidateId)
                } else {
                    submitSession()
                }
            }
            hadComposition -> submitSession()
            else -> requestNextWordPrediction()
        }
        lastMozcInput = ""
        lastCandidateIds = emptyList()
        lastCandidateIdByValue = emptyMap()
        lastCandidateReadings = emptyMap()
        return extractSuggestionCandidates(output)
    }

    fun resetSession() {
        resetContext()
        lastMozcInput = ""
        lastMode = null
        lastCandidateIds = emptyList()
        lastCandidateIdByValue = emptyMap()
        lastCandidateReadings = emptyMap()
    }

    fun addUserHistory(key: String, value: String) {
        if (key.isEmpty() || value.isEmpty()) return
        evaluateGlobal(
            Input.newBuilder()
                .setType(Input.CommandType.ADD_USER_HISTORY)
                .setUserHistoryData(
                    ProtoCommands.UserHistoryData.newBuilder()
                        .setKey(key)
                        .setValue(value)
                        .build(),
                )
                .build(),
        )
    }

    fun clearUserHistory() {
        clearUserHistoryData()
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
        lastCandidateIds = emptyList()
        lastCandidateIdByValue = emptyMap()
        lastCandidateReadings = emptyMap()
    }

    private fun ensureMode(mode: ConversionMode) {
        if (lastMode == mode) return
        if (lastMode != null) {
            resetContext()
        }
        switchCompositionMode(mode)
        lastMozcInput = ""
        lastMode = mode
        lastCandidateIds = emptyList()
        lastCandidateIdByValue = emptyMap()
        lastCandidateReadings = emptyMap()
    }

    private fun submitSession(): Output {
        val submitOutput = sendCommand(SessionCommand.CommandType.SUBMIT)
        return resolveNextWordOutput(submitOutput)
    }

    private fun submitCandidate(candidateId: Int): Output {
        val submitOutput = sendCommand(SessionCommand.CommandType.SUBMIT_CANDIDATE, candidateId)
        return resolveNextWordOutput(submitOutput)
    }

    private fun requestNextWordPrediction(): Output {
        return resolveNextWordOutput(commitOutput = null)
    }

    /**
     * Prefer zero-query candidates returned by commit commands. REQUEST_NWP is tried last
     * because it resets converter history and expects surrounding text from the client.
     */
    private fun resolveNextWordOutput(commitOutput: Output?): Output {
        if (commitOutput != null && hasSuggestionCandidates(commitOutput)) {
            return commitOutput
        }
        val statusOutput = sendCommand(SessionCommand.CommandType.GET_STATUS)
        if (hasSuggestionCandidates(statusOutput)) {
            return statusOutput
        }
        val nwpOutput = sendCommand(SessionCommand.CommandType.REQUEST_NWP)
        if (hasSuggestionCandidates(nwpOutput)) {
            return nwpOutput
        }
        return Output.getDefaultInstance()
    }

    private fun sendCommand(type: SessionCommand.CommandType, candidateId: Int? = null): Output {
        val commandBuilder = SessionCommand.newBuilder().setType(type)
        candidateId?.let { commandBuilder.setId(it) }
        return evaluate(
            Input.newBuilder()
                .setId(sessionId)
                .setType(Input.CommandType.SEND_COMMAND)
                .setCommand(commandBuilder)
                .build(),
        ).output
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
            JapaneseCandidatePrior.loadOnce(context)
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

        fun clearUserHistory(context: Context) {
            ensureMozcLoaded(context.applicationContext)
            clearUserHistoryData()
        }

        private fun clearUserHistoryData() {
            evaluateGlobal(
                Input.newBuilder()
                    .setType(Input.CommandType.CLEAR_USER_HISTORY)
                    .build(),
            )
            evaluateGlobal(
                Input.newBuilder()
                    .setType(Input.CommandType.CLEAR_USER_PREDICTION)
                    .build(),
            )
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

        private fun extractCandidateIdByValue(output: Output): Map<String, Int> {
            val map = linkedMapOf<String, Int>()

            if (output.hasAllCandidateWords()) {
                output.allCandidateWords.candidatesList.forEach { word ->
                    if (word.value.isNotEmpty() && word.hasId()) {
                        map[word.value] = word.id
                    }
                }
            }

            if (output.hasCandidateWindow()) {
                output.candidateWindow.candidateList.forEach { candidate ->
                    if (candidate.value.isNotEmpty() && candidate.hasId()) {
                        map.putIfAbsent(candidate.value, candidate.id)
                    }
                }
            }

            return map
        }

        private fun extractCandidateIds(output: Output): List<Int> {
            if (!output.hasCandidateWindow()) return emptyList()
            return output.candidateWindow.candidateList.mapNotNull { candidate ->
                if (candidate.hasId()) candidate.id else null
            }
        }

        private data class ExtractedCandidate(
            val value: String,
            val reading: String,
        )

        private data class DisplayedCandidates(
            val candidates: List<String>,
            val readings: Map<String, String>,
        )

        private fun extractCandidateEntries(
            output: Output,
            previousReadings: Map<String, String> = emptyMap(),
            emptyReadingFallback: String = "",
        ): List<ExtractedCandidate> {
            val allCandidateWordReadings = if (output.hasAllCandidateWords()) {
                output.allCandidateWords.candidatesList.map { word ->
                    word.value to word.key.orEmpty()
                }
            } else {
                emptyList()
            }
            val candidateWindowValues = if (output.hasCandidateWindow()) {
                output.candidateWindow.candidateList.map { it.value }
            } else {
                emptyList()
            }
            return CandidateReadingMerger.merge(
                allCandidateWordReadings = allCandidateWordReadings,
                candidateWindowValues = candidateWindowValues,
                previousReadings = previousReadings,
                emptyReadingFallback = emptyReadingFallback,
            ).map { (value, reading) -> ExtractedCandidate(value, reading) }
        }

        private fun extractCandidateValues(output: Output): List<String> {
            return extractCandidateEntries(output).map { it.value }
        }

        private fun extractSuggestionCandidates(output: Output): List<String> {
            return extractCandidateValues(output)
        }

        private fun hasSuggestionCandidates(output: Output): Boolean {
            return extractCandidateValues(output).isNotEmpty()
        }

        private fun extractCandidates(
            output: Output,
            fallbackInput: String,
            mode: ConversionMode,
            previousReadings: Map<String, String>,
        ): DisplayedCandidates {
            val entries = extractCandidateEntries(
                output,
                previousReadings = previousReadings,
                emptyReadingFallback = fallbackInput,
            ).toMutableList()

            if (entries.isEmpty() && output.hasPreedit()) {
                val preedit = output.preedit.segmentList.joinToString("") { it.value }
                if (preedit.isNotEmpty()) {
                    val includePreedit = mode != ConversionMode.ALPHABET ||
                        AlphabetPredictionSupport.isEnglishWordCandidate(preedit)
                    if (includePreedit) {
                        entries.add(ExtractedCandidate(preedit, fallbackInput))
                    }
                }
            }

            val readings = entries.associate { it.value to it.reading }
            val candidates = entries.map { it.value }
            val displayed = when (mode) {
                ConversionMode.ALPHABET -> AlphabetPredictionSupport.prepareEnglishCandidates(
                    candidates,
                    fallbackInput,
                )
                ConversionMode.HIRAGANA -> {
                    val hiraganaEntries = if (entries.isEmpty()) {
                        listOf(ExtractedCandidate(fallbackInput, fallbackInput))
                    } else {
                        entries
                    }
                    val ranked = HiraganaPredictionSupport.rankCandidates(
                        hiraganaEntries.map { it.value },
                        fallbackInput,
                        hiraganaEntries.map { it.reading },
                        getPriority = JapaneseCandidatePrior.current()::priorityOf,
                    )
                    ranked.ifEmpty { listOf(fallbackInput) }
                }
                else -> {
                    if (candidates.isEmpty()) {
                        listOf(fallbackInput)
                    } else {
                        candidates
                    }
                }
            }
            return DisplayedCandidates(displayed, readings)
        }
    }
}
