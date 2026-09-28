package com.example.twotouchkeyboard

import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardEdgeAvoidanceTest {

    @Test
    fun padding_avoidsSideNavigationBarAndCutout() {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 48, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(36, 0, 0, 20))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 0, 24, 0))
            .build()

        val padding = KeyboardEdgeAvoidance.paddingFor(insets)

        assertEquals(36, padding.left)
        assertEquals(0, padding.top)
        assertEquals(24, padding.right)
        assertEquals(20, padding.bottom)
    }
}
