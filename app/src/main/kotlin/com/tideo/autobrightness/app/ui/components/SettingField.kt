package com.tideo.autobrightness.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * S13b — the unified settings-field surface (m3_audit §4 "Inconsistent field rendering"). It folds the
 * three S12.5b primitives — [NumberSettingField], [IntSliderSettingField], [SwitchSettingRow] — behind
 * one entry point keyed by a [SettingFieldSpec], so a screen lists its fields declaratively and the
 * draft/validation/long-press-help/`[committed]`-bracket behaviour stays uniform.
 *
 * **Behaviour-preserving:** [SettingField] *delegates* to the existing primitives unchanged — the
 * subtle draft-epoch / committed-bracket logic (G2-F7/F1) is NOT re-implemented, so the existing field
 * tests stay green. S13c migrates screens onto this spec list; the primitives remain the renderers.
 */
sealed class SettingFieldSpec {
    abstract val label: String
    abstract val help: String?
    abstract val helper: String?
    abstract val enabled: Boolean
    abstract val testTag: String

    /** A free-text numeric (int or decimal) field — delegates to [NumberSettingField]. */
    data class Decimal(
        override val label: String,
        val value: Number,
        val onCommit: (Double) -> Unit,
        val isInt: Boolean = true,
        val committed: Number? = null,
        val epoch: Int = 0,
        val error: String? = null,
        override val help: String? = null,
        override val helper: String? = null,
        override val enabled: Boolean = true,
        override val testTag: String = label,
    ) : SettingFieldSpec()

    /** A bounded integer slider — delegates to [IntSliderSettingField]. */
    data class Slider(
        override val label: String,
        val value: Int,
        val range: IntRange,
        val onCommit: (Int) -> Unit,
        val committed: Int? = null,
        override val help: String? = null,
        override val helper: String? = null,
        override val enabled: Boolean = true,
        override val testTag: String = label,
    ) : SettingFieldSpec()

    /** A labelled on/off switch — delegates to [SwitchSettingRow]. */
    data class Toggle(
        override val label: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        override val help: String? = null,
        override val helper: String? = null,
        override val enabled: Boolean = true,
        override val testTag: String = label,
    ) : SettingFieldSpec()
}

/** Renders a [SettingFieldSpec] via the matching S12.5b primitive (see [SettingFieldSpec]). */
@Composable
fun SettingField(spec: SettingFieldSpec, modifier: Modifier = Modifier) {
    when (spec) {
        is SettingFieldSpec.Decimal -> NumberSettingField(
            label = spec.label,
            value = spec.value,
            onCommit = spec.onCommit,
            modifier = modifier,
            epoch = spec.epoch,
            committed = spec.committed,
            error = spec.error,
            helper = spec.helper,
            help = spec.help,
            enabled = spec.enabled,
            isInt = spec.isInt,
            testTag = spec.testTag,
        )
        is SettingFieldSpec.Slider -> IntSliderSettingField(
            label = spec.label,
            value = spec.value,
            range = spec.range,
            onCommit = spec.onCommit,
            modifier = modifier,
            committed = spec.committed,
            helper = spec.helper,
            help = spec.help,
            enabled = spec.enabled,
            testTag = spec.testTag,
        )
        is SettingFieldSpec.Toggle -> SwitchSettingRow(
            label = spec.label,
            checked = spec.checked,
            onCheckedChange = spec.onCheckedChange,
            modifier = modifier,
            helper = spec.helper,
            help = spec.help,
            enabled = spec.enabled,
            testTag = spec.testTag,
        )
    }
}
