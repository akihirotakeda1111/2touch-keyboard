package com.example.twotouchkeyboard

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class LandscapeInputRotationTest {

    private lateinit var controller: ServiceController<TwoTouchKeyboardService>
    private lateinit var activityController: ActivityController<Activity>
    private lateinit var service: TwoTouchKeyboardService
    private lateinit var keyboard: View

    @Before
    fun setUp() {
        kotlinx.coroutines.runBlocking {
            val repository = SettingsRepository(ApplicationProvider.getApplicationContext())
            repository.setHiraganaInputMode(CharacterInputMethod.TWOTOUCH)
            repository.setAlphabetInputMode(CharacterInputMethod.TOGGLE)
            repository.setToggleAutoCommitTimeoutMs(60_000)
        }
        activityController = Robolectric.buildActivity(Activity::class.java).setup()
        controller = Robolectric.buildService(TwoTouchKeyboardService::class.java).create()
        service = controller.get()
        Thread.sleep(150)
        idleMain()
        keyboard = service.onCreateInputView()
        showKeyboard()
        service.onStartInputView(editorInfo(), false)
        idle()
    }

    @Test
    fun fullscreenMode_staysDisabledInLandscape() {
        assertFalse(service.onEvaluateFullscreenMode())
        org.robolectric.RuntimeEnvironment.setQualifiers("+land")
        assertFalse(service.onEvaluateFullscreenMode())
    }

    @Test
    fun rotation_keepsTwoTouchFirstStrokeWaiting() {
        press(R.id.key_1)
        idle()
        assertEquals("い", label(R.id.key_2))
        assertEquals("Ａ", label(R.id.key_6))

        rotate()

        assertEquals("い", label(R.id.key_2))
        assertEquals("Ａ", label(R.id.key_6))
        assertEquals("Ｅ", label(R.id.key_0))

        press(R.id.key_2)
        idle()
        assertEquals("か", label(R.id.key_2))
        assertEquals("は", label(R.id.key_6))
        assertEquals("わ", label(R.id.key_0))
    }

    @Test
    fun rotation_releasesHeldDeleteKey() {
        val delete = keyboard.findViewById<Button>(R.id.key_delete)
        val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        delete.dispatchTouchEvent(down)
        down.recycle()
        assertTrue(delete.isPressed)

        changeOrientation()

        assertFalse(delete.isPressed)
    }

    @Test
    fun finishingInputDuringRotation_doesNotClearStateUntilFallback() {
        press(R.id.key_1)
        idle()
        changeOrientation()
        assertEquals("い", label(R.id.key_2))

        service.onFinishInputView(true)
        idle()
        assertEquals("い", label(R.id.key_2))

        shadowOf(Looper.getMainLooper()).idleFor(
            TwoTouchKeyboardService.ORIENTATION_PRESERVE_WINDOW_MS,
            TimeUnit.MILLISECONDS,
        )
        idle()
        assertEquals("か", label(R.id.key_2))
    }

    @Test
    fun sameEditorRestart_keepsWaitingStateAndCancelsFallback() {
        press(R.id.key_1)
        idle()
        changeOrientation()
        service.onFinishInputView(true)
        service.onStartInputView(editorInfo(), false)
        idle()

        shadowOf(Looper.getMainLooper()).idleFor(
            TwoTouchKeyboardService.ORIENTATION_PRESERVE_WINDOW_MS,
            TimeUnit.MILLISECONDS,
        )
        idle()
        assertEquals("い", label(R.id.key_2))
        assertNotEquals("か", label(R.id.key_6))
    }

    @Test
    fun rotation_keepsConversionSelection() {
        press(R.id.key_1)
        press(R.id.key_1)
        idle()
        Thread.sleep(300)
        idle()
        val candidates = keyboard.findViewById<LinearLayout>(R.id.candidate_container)
        assertTrue(candidates.childCount >= 2)

        press(R.id.key_space)
        press(R.id.key_space)
        idle()
        val selectedBefore = selectedCandidateText()

        rotate()

        assertEquals(selectedBefore, selectedCandidateText())
        assertTrue(candidatesOfCurrentKeyboard().childCount >= 2)
    }

    private fun rotate() {
        changeOrientation()
        service.onStartInputView(editorInfo(), false)
        idle()
    }

    private fun changeOrientation() {
        val config = Configuration(service.resources.configuration)
        config.orientation = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Configuration.ORIENTATION_PORTRAIT
        } else {
            Configuration.ORIENTATION_LANDSCAPE
        }
        service.onConfigurationChanged(config)
        keyboard = service.onCreateInputView()
        showKeyboard()
    }

    private fun showKeyboard() {
        activityController.get().setContentView(keyboard)
        idle()
    }

    private fun selectedCandidateText(): String {
        val selectedColor = ContextCompat.getColor(
            service,
            R.color.candidate_selected_background,
        )
        val container = candidatesOfCurrentKeyboard()
        val selected = (0 until container.childCount).map { container.getChildAt(it) }
            .first { (it.background as? ColorDrawable)?.color == selectedColor }
        return selected.findViewById<android.widget.TextView>(R.id.candidate_text).text.toString()
    }

    private fun candidatesOfCurrentKeyboard(): LinearLayout {
        return keyboard.findViewById(R.id.candidate_container)
    }

    private fun press(viewId: Int) {
        keyboard.findViewById<Button>(viewId).performClick()
    }

    private fun label(viewId: Int): String {
        return keyboard.findViewById<Button>(viewId).text.toString()
    }

    private fun editorInfo(): EditorInfo {
        return EditorInfo().apply {
            packageName = "com.example.notes"
            fieldId = 7
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun idle() {
        idleMain()
        flushRunQueue(keyboard)
    }

    private fun flushRunQueue(view: View) {
        val field = View::class.java.getDeclaredField("mRunQueue")
        field.isAccessible = true
        val queue = field.get(view) ?: return
        val execute = queue.javaClass.methods.first { method ->
            method.name == "executeActions"
        }
        execute.invoke(queue, android.os.Handler(Looper.getMainLooper()))
        shadowOf(Looper.getMainLooper()).idle()
    }
}

@RunWith(RobolectricTestRunner::class)
class HiraganaToggleRotationTest {

    @Test
    fun rotation_keepsToggleCharacterInProgress() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        kotlinx.coroutines.runBlocking {
            val repository = SettingsRepository(context)
            repository.setHiraganaInputMode(CharacterInputMethod.TOGGLE)
            repository.setToggleAutoCommitTimeoutMs(0)
        }
        val service = Robolectric.buildService(TwoTouchKeyboardService::class.java).create().get()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        var keyboard = service.onCreateInputView()
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        activity.get().setContentView(keyboard)
        service.onStartInputView(textEditor(), false)
        flush(keyboard)

        press(keyboard, R.id.key_1)
        flush(keyboard)
        assertEquals("あいうえお", keyboard.findViewById<Button>(R.id.key_1).text.toString())

        val config = Configuration(service.resources.configuration)
        config.orientation = Configuration.ORIENTATION_LANDSCAPE
        service.onConfigurationChanged(config)
        keyboard = service.onCreateInputView()
        activity.get().setContentView(keyboard)
        service.onStartInputView(textEditor(), false)
        flush(keyboard)

        assertEquals("あいうえお", keyboard.findViewById<Button>(R.id.key_1).text.toString())
    }

    private fun flush(view: View) {
        shadowOf(Looper.getMainLooper()).idle()
        val field = View::class.java.getDeclaredField("mRunQueue")
        field.isAccessible = true
        val queue = field.get(view) ?: return
        val execute = queue.javaClass.methods.first { method -> method.name == "executeActions" }
        execute.invoke(queue, android.os.Handler(Looper.getMainLooper()))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun press(keyboard: View, viewId: Int) {
        keyboard.findViewById<Button>(viewId).performClick()
    }

    private fun textEditor(): EditorInfo {
        return EditorInfo().apply {
            packageName = "com.example.notes"
            fieldId = 7
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
    }
}
