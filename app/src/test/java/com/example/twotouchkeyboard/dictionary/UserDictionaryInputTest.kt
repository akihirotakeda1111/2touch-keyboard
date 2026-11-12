package com.example.twotouchkeyboard.dictionary

import android.inputmethodservice.InputMethodService
import android.os.Looper
import android.text.InputType
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.example.twotouchkeyboard.ConversionSession
import com.example.twotouchkeyboard.KeyboardInputCoordinator
import com.example.twotouchkeyboard.TwoTouchKeyboardService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UserDictionaryInputTest {
    @Test
    fun registeredPhrase_commitsOnlySelectedReading_andIsHiddenForPasswords() {
        val repository = UserDictionaryRepository(ApplicationProvider.getApplicationContext())
        runBlocking {
            repository.snapshot.first().entries.forEach { repository.delete(it.id) }
            repository.save(null, "よろ", "どうぞよろしくお願いします。")
        }
        val controller = Robolectric.buildService(TwoTouchKeyboardService::class.java).create()
        val service = controller.get()
        try {
            service.onCreateInputView()
            val editable = SpannableStringBuilder()
            Selection.setSelection(editable, 0)
            val connection = object : BaseInputConnection(View(service), true) {
                override fun getEditable() = editable
            }
            InputMethodService::class.java.getDeclaredField("mStartedInputConnection").apply {
                isAccessible = true
                set(service, connection)
            }
            service.onStartInputView(EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT
                packageName = "test.notes"
                fieldId = 1
            }, false)
            val coordinator = field(service, "coordinator") as KeyboardInputCoordinator
            val session = field(service, "conversionSession") as ConversionSession
            coordinator.setComposingFromConversion("よろそのご")
            session.setCandidates(listOf("よろそのご"))
            session.activate(5)
            session.moveConversionEnd(-3, 5)
            TwoTouchKeyboardService::class.java.getDeclaredMethod(
                "requestConversion", String::class.java, Boolean::class.javaPrimitiveType,
            ).apply { isAccessible = true }.invoke(service, "よろそのご", false)
            await { session.getCandidates().firstOrNull() == "どうぞよろしくお願いします。" }
            TwoTouchKeyboardService::class.java.getDeclaredMethod("applyPartialConversionFromUi", String::class.java)
                .apply { isAccessible = true }.invoke(service, session.getCandidates().first())
            assertEquals("どうぞよろしくお願いします。そのご", editable.toString())
            assertEquals("そのご", coordinator.getComposingText())

            service.onFinishInput()
            service.onStartInputView(EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                packageName = "test.notes"
                fieldId = 2
            }, false)
            coordinator.setComposingFromConversion("よろ")
            TwoTouchKeyboardService::class.java.getDeclaredMethod(
                "requestConversion", String::class.java, Boolean::class.javaPrimitiveType,
            ).apply { isAccessible = true }.invoke(service, "よろ", false)
            assertFalse(coordinator.isConversionEnabled())
            assertTrue(session.getCandidates().isEmpty())
        } finally {
            controller.destroy()
            runBlocking { repository.snapshot.first().entries.forEach { repository.delete(it.id) } }
        }
    }

    private fun field(service: TwoTouchKeyboardService, name: String): Any =
        TwoTouchKeyboardService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(service)

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(20)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue("Dictionary candidate did not reach the IME", condition())
    }
}
