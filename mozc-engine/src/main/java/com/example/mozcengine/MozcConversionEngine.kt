package com.example.mozcengine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [ConversionEngine] backed by Mozc via JNI.
 */
class MozcConversionEngine private constructor(
    private val session: MozcSession,
) : ConversionEngine {

    override val isMozc: Boolean = true

    private val mutex = Mutex()

    override suspend fun convert(input: String, mode: ConversionMode): List<String> {
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                if (input.isEmpty()) {
                    session.resetSession()
                    emptyList()
                } else {
                    session.convert(input, mode)
                }
            }
        }
    }

    override suspend fun suggestNext(
        mode: ConversionMode,
        selectedCandidate: String?,
    ): List<String> {
        if (mode != ConversionMode.HIRAGANA) return emptyList()
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                session.suggestNext(mode, selectedCandidate)
            }
        }
    }

    override fun resetSession() {
        session.resetSession()
    }

    override fun recordCandidateSelection(
        contextKey: String,
        candidate: String,
        mode: ConversionMode,
    ) {
        if (mode != ConversionMode.HIRAGANA) return
        session.addUserHistory(contextKey, candidate)
    }

    override fun clearCandidateUsageHistory(mode: ConversionMode?) {
        if (mode == null || mode == ConversionMode.HIRAGANA) {
            session.clearUserHistory()
        }
    }

    override fun close() {
        session.deleteSession()
    }

    companion object {
        private const val TAG = "MozcConversionEngine"

        fun tryCreate(context: Context): MozcConversionEngine? {
            return try {
                val session = MozcSession.open(context.applicationContext)
                MozcConversionEngine(session)
            } catch (error: UnsatisfiedLinkError) {
                Log.w(TAG, "libmozc.so not available", error)
                null
            } catch (error: Exception) {
                Log.w(TAG, "Failed to initialize Mozc", error)
                null
            }
        }
    }
}
