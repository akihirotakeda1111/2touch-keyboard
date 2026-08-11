package com.example.twotouchkeyboard.candidate

import android.content.Context
import com.example.mozcengine.MozcSession

object CandidateUsageHistoryManager {

    fun clearAll(context: Context) {
        val appContext = context.applicationContext
        EnglishCandidateUsageStore(appContext).clear()
        if (MozcSession.hasDictionaryData(appContext)) {
            MozcSession.clearUserHistory(appContext)
        }
    }
}
