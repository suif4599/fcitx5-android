/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import androidx.preference.DialogPreference
import kotlin.math.roundToInt
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.utils.setOnChangeListener
import splitties.dimensions.dp
import splitties.resources.resolveThemeAttribute
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.seekBar
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.verticalMargin
import splitties.views.gravityHorizontalCenter
import splitties.views.textAppearance

/**
 * Dialog preference backed by a float value with min/max and step.
 */
class DialogSeekBarFloatPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : DialogPreference(context, attrs, defStyleAttr) {

    private var value = 0f
    var min: Float
    var max: Float
    var step: Float
    var unit: String

    var default: Float = 0f
    var defaultLabel: String? = null

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.DialogSeekBarPreference, 0, 0).run {
            try {
                min = getFloat(R.styleable.DialogSeekBarPreference_min, 0f)
                max = getFloat(R.styleable.DialogSeekBarPreference_max, 1f)
                step = getFloat(R.styleable.DialogSeekBarPreference_step, 0.01f)
                unit = getString(R.styleable.DialogSeekBarPreference_unit) ?: ""
                if (getBoolean(R.styleable.DialogSeekBarPreference_useSimpleSummaryProvider, false)) {
                    summaryProvider = SimpleSummaryProvider
                }
            } finally {
                recycle()
            }
        }
    }

    override fun persistFloat(value: Float): Boolean {
        return super.persistFloat(value).also {
            if (it) this@DialogSeekBarFloatPreference.value = value
        }
    }

    override fun setDefaultValue(defaultValue: Any?) {
        super.setDefaultValue(defaultValue)
        default = (defaultValue as? Float) ?: 0f
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getFloat(index, default)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedFloat(defaultValue as? Float ?: default)
    }

    override fun onClick() {
        showSeekBarDialog()
    }

    private fun showSeekBarDialog() {
        val textView = context.textView {
            text = textForValue(value)
            textAppearance = context.resolveThemeAttribute(android.R.attr.textAppearanceListItem)
        }
        val seekBar = context.seekBar {
            max = progressForValue(this@DialogSeekBarFloatPreference.max)
            progress = progressForValue(value)
            setOnChangeListener {
                textView.text = textForValue(valueForProgress(it))
            }
        }
        val dialogContent = context.verticalLayout {
            gravity = gravityHorizontalCenter
            if (dialogMessage != null) {
                val messageText = textView { text = dialogMessage }
                add(messageText, lParams {
                    verticalMargin = dp(8)
                    horizontalMargin = dp(24)
                })
            }
            add(textView, lParams {
                verticalMargin = dp(24)
            })
            add(seekBar, lParams {
                width = matchParent
                horizontalMargin = dp(10)
                bottomMargin = dp(10)
            })
        }
        AlertDialog.Builder(context)
            .setTitle(this@DialogSeekBarFloatPreference.dialogTitle)
            .setView(dialogContent)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newValue = valueForProgress(seekBar.progress)
                setValue(newValue)
            }
            .setNeutralButton(R.string.default_) { _, _ ->
                setValue(default)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setValue(value: Float) {
        if (callChangeListener(value)) {
            persistFloat(value)
            notifyChanged()
        }
    }

    private fun progressForValue(value: Float): Int {
        val clamped = value.coerceIn(min, max)
        return ((clamped - min) / step).roundToInt()
    }

    private fun valueForProgress(progress: Int): Float {
        val raw = (progress * step) + min
        return raw.coerceIn(min, max)
    }

    private fun textForValue(value: Float = this@DialogSeekBarFloatPreference.value): String {
        if (value == default && defaultLabel != null) return defaultLabel!!
        return String.format("%.2f %s", value, unit).trim()
    }

    object SimpleSummaryProvider : SummaryProvider<DialogSeekBarFloatPreference> {
        override fun provideSummary(preference: DialogSeekBarFloatPreference): CharSequence {
            return preference.textForValue()
        }
    }
}
