package com.example.twotouchkeyboard

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

enum class CandidateBarAction {
    OPEN_SETTINGS,
}

data class CandidateBarActionSpec(
    val action: CandidateBarAction,
    val labelRes: Int,
)

object CandidateBarActions {
    /** 変換候補がないときに候補バーへ並べる機能キー。 */
    val idle: List<CandidateBarActionSpec> = listOf(
        CandidateBarActionSpec(
            action = CandidateBarAction.OPEN_SETTINGS,
            labelRes = R.string.key_settings,
        ),
    )
}

/**
 * 候補の有無で候補バーの中身を切り替える。
 * 候補があるときは変換候補のみ、ないときは機能キーのみを描画する。
 */
class CandidateBarController(
    private val container: LinearLayout,
    private val scrollView: HorizontalScrollView,
    private val onAction: (CandidateBarAction) -> Unit,
) {
    fun refresh(
        candidates: List<String>,
        selectedIndex: Int,
        highlightSelection: Boolean,
        selectionActive: Boolean,
        onCandidateSelected: (String) -> Unit,
        actions: List<CandidateBarActionSpec> = CandidateBarActions.idle,
    ) {
        if (candidates.isNotEmpty()) {
            renderCandidates(
                candidates = candidates,
                selectedIndex = selectedIndex,
                highlightSelection = highlightSelection,
                selectionActive = selectionActive,
                onCandidateSelected = onCandidateSelected,
            )
        } else {
            renderFunctionActions(actions)
        }
    }

    private fun renderCandidates(
        candidates: List<String>,
        selectedIndex: Int,
        highlightSelection: Boolean,
        selectionActive: Boolean,
        onCandidateSelected: (String) -> Unit,
    ) {
        container.removeAllViews()
        val context = container.context
        val inflater = LayoutInflater.from(context)
        val scrollIndex = if (highlightSelection) selectedIndex else -1

        candidates.forEachIndexed { index, candidate ->
            val itemView = inflater.inflate(R.layout.suggest_item, container, false)
            val textView = itemView.findViewById<TextView>(R.id.candidate_text)
            textView.text = candidate

            if (highlightSelection && selectionActive && index == selectedIndex) {
                itemView.setBackgroundColor(
                    ContextCompat.getColor(context, R.color.candidate_selected_background),
                )
                textView.setTextColor(
                    ContextCompat.getColor(context, R.color.candidate_selected_text),
                )
            } else {
                itemView.setBackgroundResource(R.drawable.candidate_chip_background)
                textView.setTextColor(
                    ContextCompat.getColor(context, R.color.candidate_text),
                )
            }

            textView.setOnClickListener {
                onCandidateSelected(candidate)
            }
            container.addView(itemView)
        }

        if (scrollIndex >= 0) {
            scrollToSelectedCandidate(scrollIndex)
        }
    }

    private fun renderFunctionActions(actions: List<CandidateBarActionSpec>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        actions.forEach { spec ->
            val itemView = inflater.inflate(R.layout.candidate_bar_action_item, container, false)
            val textView = itemView.findViewById<TextView>(R.id.candidate_bar_action_text)
            textView.setText(spec.labelRes)
            textView.tag = spec.action
            textView.setOnClickListener {
                onAction(spec.action)
            }
            container.addView(itemView)
        }
    }

    private fun scrollToSelectedCandidate(selectedIndex: Int) {
        scrollView.post {
            val child = container.getChildAt(selectedIndex) ?: return@post
            val scrollX = child.left - (scrollView.width - child.width) / 2
            scrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }
}

internal fun settingsActivityIntent(context: Context): Intent {
    return Intent(context, SettingsActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
