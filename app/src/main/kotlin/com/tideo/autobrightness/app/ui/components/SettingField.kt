package com.tideo.autobrightness.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** S13b unified settings-field surface: folds three S12.5b primitives behind SettingFieldSpec
 * for declarative screen lists. Behaviour-preserving: delegates to primitives (G2-F7/F1 logic untouched). */
sealed class SettingFieldSpec {
    abstract val label: String
    @get:StringRes abstract val help: Int?
    abstract val helper: String?
    abstract val enabled: Boolean
    abstract val testTag: String

    data class Decimal(
        override val label: String,
        val value: Number,
        val onCommit: (Double) -> Unit,
        val isInt: Boolean = true,
        val committed: Number? = null,
        val epoch: Int = 0,
        val error: String? = null,
        @StringRes override val help: Int? = null,
        override val helper: String? = null,
        override val enabled: Boolean = true,
        override val testTag: String = label,
    ) : SettingFieldSpec()

    data class Slider(
        override val label: String,
        val value: Int,
        val range: IntRange,
        val onCommit: (Int) -> Unit,
        val committed: Int? = null,
        @StringRes override val help: Int? = null,
        override val helper: String? = null,
        override val enabled: Boolean = true,
        override val testTag: String = label,
    ) : SettingFieldSpec()

    data class Toggle(
        override val label: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        @StringRes override val help: Int? = null,
        override val helper: String? = null,
        override val enabled: Boolean = true,
        override val testTag: String = label,
    ) : SettingFieldSpec()
}

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
