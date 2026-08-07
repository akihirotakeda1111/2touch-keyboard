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
        if (input.isEmpty()) return emptyList()
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                session.convert(input, mode)
            }
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
