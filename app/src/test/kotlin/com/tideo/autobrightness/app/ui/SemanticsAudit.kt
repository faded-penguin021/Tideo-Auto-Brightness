package com.tideo.autobrightness.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.fail

/**
 * A11y audit gate (D-156). Walks the MERGED semantics tree — the tree TalkBack traverses — and
 * fails on any interactive node (clickable / toggleable / slider) that exposes no usable label.
 *
 * "Usable" requires at least one letter or digit: a bare symbol glyph (e.g. the ⓘ help button's
 * Text) merges in as text but announces nothing meaningful, so symbol-only text does not count.
 * Non-interactive decorative icons (`contentDescription = null`) are correct practice and are
 * not flagged — only nodes a user can act on must be named.
 *
 * Every a11y-backlog unit (plans/a11y-diagnostics.md) renders its screen/component and funnels
 * through [assertAllInteractiveNodesAreLabeled]; this is the per-unit hard acceptance gate.
 */
fun ComposeContentTestRule.assertAllInteractiveNodesAreLabeled() {
    val violations = mutableListOf<String>()
    auditNode(onRoot().fetchSemanticsNode(), violations)
    if (violations.isNotEmpty()) {
        fail(
            "Unlabeled interactive semantics nodes — give each a label TalkBack can read " +
                "(associate the visible label via Modifier.semantics { contentDescription = … } " +
                "or add an a11y_* string; see plans/a11y-diagnostics.md guardrails):\n" +
                violations.joinToString("\n"),
        )
    }
}

/** Asserts a heading node (Modifier.semantics { heading() }) with exactly [text] exists. */
fun ComposeContentTestRule.assertHeadingExists(text: String) {
    val headings = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false)
    val texts = headings.flatMap { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()
    }
    if (text !in texts) {
        fail("No heading semantics node with text \"$text\" (headings found: $texts). Section headers need Modifier.semantics { heading() } so TalkBack users can jump between sections.")
    }
}

private fun auditNode(node: SemanticsNode, violations: MutableList<String>) {
    val config = node.config
    val interactive = config.contains(SemanticsActions.OnClick) ||
        config.contains(SemanticsActions.SetProgress) ||
        config.contains(SemanticsProperties.ToggleableState)
    if (interactive && !config.hasUsableLabel()) violations += describe(node)
    node.children.forEach { auditNode(it, violations) }
}

private fun SemanticsConfiguration.hasUsableLabel(): Boolean {
    val candidates = getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() +
        listOfNotNull(getOrNull(SemanticsProperties.EditableText)?.text)
    return candidates.any { candidate -> candidate.any { it.isLetterOrDigit() } }
}

private fun describe(node: SemanticsNode): String {
    val config = node.config
    val tag = config.getOrNull(SemanticsProperties.TestTag)
    val role = config.getOrNull(SemanticsProperties.Role)
    val kind = when {
        config.contains(SemanticsActions.SetProgress) -> "slider"
        config.contains(SemanticsProperties.ToggleableState) -> "toggleable"
        else -> "clickable"
    }
    return "  #${node.id} $kind role=$role testTag=$tag"
}
