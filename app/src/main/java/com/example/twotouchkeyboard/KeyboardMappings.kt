package com.example.twotouchkeyboard

/**
 * 2タッチ入力の拡張キー（第2タッチ: 6–9, 0）の文字割り当て。
 *
 * 第1タッチが 0 のときは [KeyboardKey.Zero] を、第2タッチが 0 のときはキー番号 0 で参照する。
 */

/** 第2タッチキー (6,7,8,9,0) → 文字 */
typealias ExtensionSlots = Map<Int, Char>

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

    val hiraganaExtensionRows: Map<Int, ExtensionSlots> = mapOf(
        1 to extensionRow("ＡＢＣＤＥ"),
        2 to extensionRow("ＦＧＨＩＪ"),
        3 to extensionRow("ＫＬＭＮＯ"),
        4 to extensionRow("ＰＱＲＳＴ"),
        5 to extensionRow("ＵＶＷＸＹ"),
        6 to extensionRow("Ｚ！？ー／"),
        7 to extensionRow("￥＆、。・"),
        8 to extensionRow("（）＊＃＄"),
        9 to extensionRow("１２３４５"),
        0 to extensionRow("６７８９０"),
    )

    val alphabetExtensionRows: Map<Int, ExtensionSlots> = mapOf(
        0 to extensionRow("67890"),
        6 to extensionSlots(7 to '!', 8 to '?', 9 to '-', 0 to '/'),
        7 to extensionSlots(7 to '&', 8 to ',', 9 to '.', 0 to '・'),
        8 to extensionSlots(7 to ')', 8 to '*', 9 to '#', 0 to '$'),
        9 to extensionRow("12345"),
    )

    val hiraganaExtensionHeadLabels: Map<Int, String> = hiraganaExtensionRows.mapValues { (_, slots) ->
        slots[6]?.toString() ?: ""
    }

    val alphabetExtensionHeadLabels: Map<Int, String> = mapOf(
        0 to "6",
    )

    fun secondKeyNumber(key: KeyboardKey): Int? {
        return when (key) {
            is KeyboardKey.Digit -> key.number
            KeyboardKey.Zero -> 0
            else -> null
        }
    }

    fun isExtensionSecondKey(secondKey: Int): Boolean {
        return secondKey in 6..9 || secondKey == 0
    }

    private fun extensionRow(chars: String): ExtensionSlots {
        require(chars.length == 5) { "Extension row must contain exactly 5 characters." }
        return mapOf(
            6 to chars[0],
            7 to chars[1],
            8 to chars[2],
            9 to chars[3],
            0 to chars[4],
        )
    }

    private fun extensionSlots(vararg pairs: Pair<Int, Char>): ExtensionSlots {
        return pairs.toMap()
    }
}
