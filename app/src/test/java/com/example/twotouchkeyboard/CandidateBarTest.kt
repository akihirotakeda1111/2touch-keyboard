package com.example.twotouchkeyboard

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CandidateBarControllerTest {

    private lateinit var context: Context
    private lateinit var keyboardView: View
    private lateinit var candidateContainer: LinearLayout
    private lateinit var controller: CandidateBarController
    private var performedAction: CandidateBarAction? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyboardView = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        bind(keyboardView)
    }

    @Test
    fun idleActions_defineSettingsKey() {
        assertEquals(
            listOf(
                CandidateBarActionSpec(
                    action = CandidateBarAction.OPEN_SETTINGS,
                    labelRes = R.string.key_settings,
                ),
            ),
            CandidateBarActions.idle,
        )
    }

    @Test
    fun noCandidates_showsSettingsAction() {
        refresh()

        assertEquals(1, candidateContainer.childCount)
        val action = actionViewAt(0)
        assertEquals(context.getString(R.string.key_settings), action.text.toString())
        assertEquals(CandidateBarAction.OPEN_SETTINGS, action.tag)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, action.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, action.layoutParams.height)
        assertEquals(Gravity.CENTER, action.gravity)
        assertNull(candidateContainer.findViewById(R.id.candidate_text))
    }

    @Test
    fun candidates_replaceFunctionActions() {
        refresh(candidates = listOf("東京", "とうきょう"))

        assertEquals(2, candidateContainer.childCount)
        assertEquals("東京", candidateTextAt(0))
        assertEquals("とうきょう", candidateTextAt(1))
        assertNull(candidateContainer.findViewById(R.id.candidate_bar_action_text))
        assertTrue((0 until candidateContainer.childCount).all { candidateContainer.getChildAt(it).tag == null })
    }

    @Test
    fun clearingCandidates_restoresFunctionActions() {
        refresh(candidates = listOf("あ"))
        assertEquals("あ", candidateTextAt(0))

        refresh()

        assertEquals(1, candidateContainer.childCount)
        assertEquals(context.getString(R.string.key_settings), actionViewAt(0).text.toString())
        assertEquals(CandidateBarAction.OPEN_SETTINGS, actionViewAt(0).tag)
    }

    @Test
    fun multipleFunctionActions_areAddedToTheSameContainer() {
        val actions = listOf(
            CandidateBarActionSpec(CandidateBarAction.OPEN_SETTINGS, R.string.key_settings),
            CandidateBarActionSpec(CandidateBarAction.OPEN_SETTINGS, R.string.key_symbols),
        )

        refresh(actions = actions)

        assertEquals(2, candidateContainer.childCount)
        assertSame(candidateContainer, actionViewAt(0).parent)
        assertSame(candidateContainer, actionViewAt(1).parent)
        assertEquals(context.getString(R.string.key_settings), actionViewAt(0).text.toString())
        assertEquals(context.getString(R.string.key_symbols), actionViewAt(1).text.toString())
        assertEquals(CandidateBarAction.OPEN_SETTINGS, actionViewAt(0).tag)
        assertEquals(CandidateBarAction.OPEN_SETTINGS, actionViewAt(1).tag)
    }

    @Test
    fun settingsAction_invokesCallback() {
        refresh()

        actionViewAt(0).performClick()

        assertEquals(CandidateBarAction.OPEN_SETTINGS, performedAction)
    }

    @Test
    fun candidateClick_invokesSelectionWithoutAction() {
        var selected: String? = null
        refresh(candidates = listOf("東京"), onCandidateSelected = { selected = it })

        candidateContainer.getChildAt(0).performClick()

        assertEquals("東京", selected)
        assertNull(performedAction)
    }

    @Test
    fun activeSelection_highlightsOnlyTheSelectedCandidate() {
        refresh(candidates = listOf("あ", "い"), selectedIndex = 1, selectionActive = true)

        val expected = ContextCompat.getColor(context, R.color.candidate_selected_background)
        val selected = candidateContainer.getChildAt(1)
        assertTrue(selected.background is ColorDrawable)
        assertEquals(expected, (selected.background as ColorDrawable).color)
        assertTrue(candidateContainer.getChildAt(0).background !is ColorDrawable)
    }

    @Test
    fun previewCandidates_areNotHighlighted() {
        refresh(candidates = listOf("あ"), selectedIndex = 0, highlightSelection = true, selectionActive = false)

        assertTrue(candidateContainer.getChildAt(0).background !is ColorDrawable)
    }

    @Test
    fun keyboardHeight_doesNotChangeBetweenActionsAndCandidates() {
        assertKeyboardHeightStable(keyboardView)
    }

    @Test
    fun landscapeKeyboardHeight_doesNotChangeBetweenActionsAndCandidates() {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val landContext = context.createConfigurationContext(config)
        val landscape = LayoutInflater.from(landContext).inflate(R.layout.keyboard_view, null)
        val grid = landscape.findViewById<GridLayout>(R.id.keyboard_grid)
        assertEquals(6, grid.columnCount)
        assertKeyboardHeightStable(landscape)
    }

    private fun assertKeyboardHeightStable(root: View) {
        val scroll = root.findViewById<View>(R.id.candidate_scroll)
        val panel = root.findViewById<View>(R.id.keyboard_panel)
        val container = root.findViewById<LinearLayout>(R.id.candidate_container)
        val bar = CandidateBarController(
            container = container,
            scrollView = root.findViewById(R.id.candidate_scroll),
            onAction = {},
        )

        measure(root)
        val emptyHeight = root.measuredHeight
        val emptyScrollHeight = scroll.measuredHeight
        assertTrue(emptyScrollHeight > 0)

        bar.refresh(
            candidates = emptyList(),
            selectedIndex = -1,
            highlightSelection = false,
            selectionActive = false,
            onCandidateSelected = {},
        )
        measure(root)
        layout(root)
        assertEquals(emptyHeight, root.measuredHeight)
        assertEquals(emptyScrollHeight, scroll.measuredHeight)
        assertTrue(container.getChildAt(0).height > 0)
        assertEquals(scroll.height, container.getChildAt(0).height)
        assertEquals(scroll.bottom, panel.top)

        bar.refresh(
            candidates = listOf("候補", "変換"),
            selectedIndex = 0,
            highlightSelection = true,
            selectionActive = false,
            onCandidateSelected = {},
        )
        measure(root)
        layout(root)
        assertEquals(emptyHeight, root.measuredHeight)
        assertEquals(emptyScrollHeight, scroll.measuredHeight)
        assertEquals(scroll.bottom, panel.top)

        bar.refresh(
            candidates = emptyList(),
            selectedIndex = -1,
            highlightSelection = false,
            selectionActive = false,
            onCandidateSelected = {},
        )
        measure(root)
        assertEquals(emptyHeight, root.measuredHeight)
        assertEquals(emptyScrollHeight, scroll.measuredHeight)
    }

    private fun bind(root: View) {
        keyboardView = root
        candidateContainer = root.findViewById(R.id.candidate_container)
        performedAction = null
        controller = CandidateBarController(
            container = candidateContainer,
            scrollView = root.findViewById(R.id.candidate_scroll),
            onAction = { performedAction = it },
        )
    }

    private fun refresh(
        candidates: List<String> = emptyList(),
        selectedIndex: Int = if (candidates.isEmpty()) -1 else 0,
        highlightSelection: Boolean = candidates.isNotEmpty(),
        selectionActive: Boolean = false,
        onCandidateSelected: (String) -> Unit = {},
        actions: List<CandidateBarActionSpec> = CandidateBarActions.idle,
    ) {
        controller.refresh(
            candidates = candidates,
            selectedIndex = selectedIndex,
            highlightSelection = highlightSelection,
            selectionActive = selectionActive,
            onCandidateSelected = onCandidateSelected,
            actions = actions,
        )
    }

    private fun actionViewAt(index: Int): TextView {
        return candidateContainer.getChildAt(index) as TextView
    }

    private fun candidateTextAt(index: Int): String {
        return candidateContainer.getChildAt(index).findViewById<TextView>(R.id.candidate_text).text.toString()
    }

    private fun measure(view: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
    }

    private fun layout(view: View) {
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}

@RunWith(RobolectricTestRunner::class)
class CandidateBarServiceTest {

    @Test
    fun settingsIntent_targetsSettingsActivityWithNewTask() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = settingsActivityIntent(context)

        assertEquals(SettingsActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun idleKeyboard_showsSettingsAndLaunchesSettingsActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = Robolectric.buildService(TwoTouchKeyboardService::class.java).create().get()
        shadowOf(Looper.getMainLooper()).idle()
        val keyboard = service.onCreateInputView()
        val container = keyboard.findViewById<LinearLayout>(R.id.candidate_container)

        assertEquals(1, container.childCount)
        val settingsKey = container.getChildAt(0) as TextView
        assertEquals(context.getString(R.string.key_settings), settingsKey.text.toString())
        assertEquals(CandidateBarAction.OPEN_SETTINGS, settingsKey.tag)

        settingsKey.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(service).nextStartedActivity
        assertEquals(SettingsActivity::class.java.name, started.component?.className)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun candidatePresence_switchesBetweenCandidatesAndSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = Robolectric.buildService(TwoTouchKeyboardService::class.java).create().get()
        shadowOf(Looper.getMainLooper()).idle()
        val keyboard = service.onCreateInputView()
        val container = keyboard.findViewById<LinearLayout>(R.id.candidate_container)
        val session = conversionSession(service)

        session.setCandidates(listOf("漢字", "かんじ"))
        refreshConversionUi(service)

        assertEquals(2, container.childCount)
        assertEquals("漢字", container.getChildAt(0).findViewById<TextView>(R.id.candidate_text).text.toString())
        assertNull(container.findViewById<TextView>(R.id.candidate_bar_action_text))

        session.clear()
        refreshConversionUi(service)

        assertEquals(1, container.childCount)
        val settingsKey = container.getChildAt(0) as TextView
        assertEquals(context.getString(R.string.key_settings), settingsKey.text.toString())
        assertEquals(CandidateBarAction.OPEN_SETTINGS, settingsKey.tag)
    }

    private fun conversionSession(service: TwoTouchKeyboardService): ConversionSession {
        val field = TwoTouchKeyboardService::class.java.getDeclaredField("conversionSession")
        field.isAccessible = true
        return field.get(service) as ConversionSession
    }

    private fun refreshConversionUi(service: TwoTouchKeyboardService) {
        val method = TwoTouchKeyboardService::class.java.getDeclaredMethod("refreshConversionUi")
        method.isAccessible = true
        method.invoke(service)
    }
}
