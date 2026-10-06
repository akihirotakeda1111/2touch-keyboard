package com.example.twotouchkeyboard

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.twotouchkeyboard.test.OtherAppActivity
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 設定 → お試し入力 → 別アプリ → IMEの設定キー、のあと設定画面が前面になり、
 * Back で別アプリへ戻ることを確認する。
 */
@RunWith(AndroidJUnit4::class)
class SettingsKeyNavigationTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        device.pressHome()
        val ime = "com.example.twotouchkeyboard/.TwoTouchKeyboardService"
        val enable = device.executeShellCommand("ime enable $ime")
        val select = device.executeShellCommand("ime set $ime")
        val defaultIme = device.executeShellCommand("settings get secure default_input_method")
        assertTrue(
            "failed to enable IME: enable=$enable select=$select default=$defaultIme",
            defaultIme.contains("TwoTouchKeyboardService"),
        )
    }

    @Test
    fun settingsKey_showsSettingsAndBackReturnsToOtherApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext

        targetContext.startActivity(
            Intent(targetContext, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        assertTrue(device.wait(Until.hasObject(By.text("キーボード設定")), 10_000))
        dismissUpdateDialog()
        clickText("お試し入力を開く")
        assertTrue(
            device.wait(
                Until.hasObject(By.text("各入力欄をタップして 2Touch Keyboard の動作を確認できます。")),
                10_000,
            ),
        )

        instrumentation.context.startActivity(
            Intent(instrumentation.context, OtherAppActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        waitForResumed("OtherAppActivity")
        assertTrue(device.wait(Until.hasObject(By.text("別アプリ")), 10_000))
        device.findObject(By.desc("別アプリ入力"))?.click()
            ?: device.findObject(By.text("入力"))?.click()
            ?: fail("other app input field was not found")

        clickSettingsKey()
        waitForResumed("SettingsActivity")
        assertTrue(device.wait(Until.hasObject(By.text("キーボード設定")), 10_000))
        val resumedAfterKey = topResumedComponent()
        assertTrue("settings key resumed $resumedAfterKey", resumedAfterKey?.contains("SettingsActivity") == true)
        assertTrue("input try stayed in front: $resumedAfterKey", resumedAfterKey?.contains("InputTryActivity") != true)

        device.pressBack()
        waitForResumed("OtherAppActivity")
        assertTrue(device.wait(Until.hasObject(By.text("別アプリ")), 10_000))
    }

    private fun dismissUpdateDialog() {
        val later = device.wait(Until.findObject(By.text("あとで")), 1_000) ?: return
        later.click()
        device.waitForIdle()
    }

    private fun clickText(text: String) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val target = device.findObject(By.text(text))
            if (target != null) {
                target.click()
                return
            }
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.75f).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.3f).toInt(),
                30,
            )
            device.waitForIdle()
        }
        fail("text not found: $text")
    }

    private fun clickSettingsKey() {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val key = device.findObjects(By.text("設定"))
                .filter { it.visibleBounds.top > device.displayHeight / 3 }
                .maxByOrNull { it.visibleBounds.bottom }
            if (key != null) {
                key.click()
                return
            }
            device.waitForIdle()
        }
        fail("IME settings key was not found. resumed=${topResumedComponent()}")
    }

    private fun waitForResumed(simpleName: String) {
        val deadline = System.currentTimeMillis() + 10_000
        var last: String? = null
        while (System.currentTimeMillis() < deadline) {
            last = topResumedComponent()
            if (last?.contains(simpleName) == true) return
            Thread.sleep(200)
        }
        fail("timed out waiting for $simpleName, last=$last")
    }

    private fun topResumedComponent(): String? {
        val dump = device.executeShellCommand("dumpsys activity activities")
        val patterns = listOf(
            Regex("""topResumedActivity=ActivityRecord\{[^\n]* ([^\s}]+)/(\S+)"""),
            Regex("""mResumedActivity: ActivityRecord\{[^\n]* ([^\s}]+)/(\S+)"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(dump) ?: continue
            val className = match.groupValues[2].trim().trimEnd('}')
            return "${match.groupValues[1]}/$className"
        }
        return null
    }
}
