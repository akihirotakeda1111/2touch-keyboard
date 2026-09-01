package com.example.twotouchkeyboard.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionPolicyTest {
    @Test
    fun isUpdateAvailable_returnsTrue_whenLatestIsNewer() {
        assertTrue(UpdateVersionPolicy.isUpdateAvailable(10, 11))
    }

    @Test
    fun isUpdateAvailable_returnsFalse_whenVersionsAreEqual() {
        assertFalse(UpdateVersionPolicy.isUpdateAvailable(10, 10))
    }

    @Test
    fun isUpdateAvailable_returnsFalse_whenInstalledIsNewer() {
        assertFalse(UpdateVersionPolicy.isUpdateAvailable(11, 10))
    }
}
