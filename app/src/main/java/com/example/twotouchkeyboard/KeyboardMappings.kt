package com.example.twotouchkeyboard

object KeyboardMappings {
    val hiraganaRows: Map<Int, String> = mapOf(
        1 to "あいうえお",
        2 to "かきくけこ",
        3 to "さしすせそ",
        4 to "たちつてと",
        5 to "なにぬねの",
        6 to "はひふへほ",
        7 to "まみむめも",
        8 to "やゆよ",
        9 to "わをん",
    )

    val alphabetRows: Map<Int, String> = mapOf(
        2 to "abcABC",
        3 to "defDEF",
        4 to "ghiGHI",
        5 to "jklJKL",
        6 to "mnoMNO",
        7 to "pqrsPQRS",
        8 to "tuvTUV",
        9 to "wxyzWXYZ",
    )

    const val symbolRow: String = "、。？！・"

    val hiraganaRowHeadLabels: Map<Int, String> = hiraganaRows.mapValues { (_, chars) ->
        chars.first().toString()
    }

    val alphabetRowHeadLabels: Map<Int, String> = alphabetRows.mapValues { (_, chars) ->
        chars.first().toString()
    }
}
