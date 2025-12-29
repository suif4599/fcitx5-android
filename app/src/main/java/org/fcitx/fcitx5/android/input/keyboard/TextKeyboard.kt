/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.annotation.Keep
import androidx.core.view.allViews
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource

@SuppressLint("ViewConstructor")
class TextKeyboard(
    context: Context,
    theme: Theme,
    swipeOverrides: Map<String, String> = emptyMap(),
    private val tabLongPressText: String = AppPrefs.sanitizeTabLongPressText(
        AppPrefs.getInstance().keyboard.tabLongPressText.getValue()
    )
) : BaseKeyboard(context, theme, layout(swipeOverrides, tabLongPressText)) {

    enum class CapsState { None, Once, Lock }

    companion object {
        const val Name = "Text"

        fun layout(
            swipeOverrides: Map<String, String>,
            tabLongPressText: String
        ): List<List<KeyDef>> {
            fun alt(key: String, fallback: String) = swipeOverrides[key] ?: fallback

            return listOf(
                listOf(
                    AlphabetKey("Q", alt("Q", "1")),
                    AlphabetKey("W", alt("W", "2")),
                    AlphabetKey("E", alt("E", "3")),
                    AlphabetKey("R", alt("R", "4")),
                    AlphabetKey("T", alt("T", "5")),
                    AlphabetKey("Y", alt("Y", "6")),
                    AlphabetKey("U", alt("U", "7")),
                    AlphabetKey("I", alt("I", "8")),
                    AlphabetKey("O", alt("O", "9")),
                    AlphabetKey("P", alt("P", "0"))
                ),
                listOf(
                    AlphabetKey("A", alt("A", "\\")),
                    AlphabetKey("S", alt("S", "!")),
                    AlphabetKey("D", alt("D", "@")),
                    AlphabetKey("F", alt("F", "+")),
                    AlphabetKey("G", alt("G", "%")),
                    AlphabetKey("H", alt("H", "\"")),
                    AlphabetKey("J", alt("J", "=")),
                    AlphabetKey("K", alt("K", "*")),
                    AlphabetKey("L", alt("L", "?"))
                ),
                listOf(
                    CapsKey(),
                    AlphabetKey("Z", alt("Z", "(")),
                    AlphabetKey("X", alt("X", ")")),
                    AlphabetKey("C", alt("C", "-")),
                    AlphabetKey("V", alt("V", "_")),
                    AlphabetKey("B", alt("B", ":")),
                    AlphabetKey("N", alt("N", ";")),
                    AlphabetKey("M", alt("M", "/")),
                    BackspaceKey()
                ),
                listOf(
                    LayoutSwitchKey("?123", ""),
                    TabKey(longPressText = tabLongPressText),
                    CommaKey(0.1f, KeyDef.Appearance.Variant.Alternative),
                    SpaceKey(),
                    SymbolKey(
                        ".",
                        0.1f,
                        KeyDef.Appearance.Variant.Alternative,
                        swipeAscii = ".",
                        swipeFullWidth = "。"
                    ),
                    ReturnKey()
                )
            )
        }
    }

    val caps: ImageKeyView by lazy { findViewById(R.id.button_caps) }
    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val quickphrase: ImageKeyView by lazy { findViewById(R.id.button_quickphrase) }
    val space: TextKeyView by lazy { findViewById(R.id.button_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    private val keepLettersUppercase by AppPrefs.getInstance().keyboard.keepLettersUppercase

    private val textKeys: List<TextKeyView> by lazy {
        allViews.filterIsInstance(TextKeyView::class.java).toList()
    }

    private var capsState: CapsState = CapsState.None

    private fun transformAlphabet(c: String): String {
        return when (capsState) {
            CapsState.None -> c.lowercase()
            else -> c.uppercase()
        }
    }

    private var punctuationMapping: Map<String, String> = mapOf()
    private fun transformPunctuation(p: String) = punctuationMapping.getOrDefault(p, p)

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        var transformed = action
        when (action) {
            is KeyAction.FcitxKeyAction -> when (source) {
                KeyActionListener.Source.Keyboard -> {
                    when (capsState) {
                        CapsState.None -> {
                            transformed = action.copy(act = action.act.lowercase())
                        }
                        CapsState.Once -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.Shift)
                            )
                            switchCapsState()
                        }
                        CapsState.Lock -> {
                            transformed = action.copy(
                                act = action.act.uppercase(),
                                states = KeyStates(KeyState.Virtual, KeyState.CapsLock)
                            )
                        }
                    }
                }
                KeyActionListener.Source.Popup -> {
                    if (capsState == CapsState.Once) {
                        switchCapsState()
                    }
                }
            }
            is KeyAction.CapsAction -> switchCapsState(action.lock)
            else -> {}
        }
        super.onAction(transformed, source)
    }

    override fun onAttach() {
        capsState = CapsState.None
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        punctuationMapping = mapping
        updatePunctuationKeys()
    }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = buildString {
            append(ime.displayName)
            ime.subMode.run { label.ifEmpty { name.ifEmpty { null } } }?.let { append(" ($it)") }
        }
        if (capsState != CapsState.None) {
            switchCapsState()
        }
    }

    private fun transformPopupPreview(c: String): String {
        if (c.length != 1) return c
        if (c[0].isLetter()) return transformAlphabet(c)
        return transformPunctuation(c)
    }

    override fun onPopupAction(action: PopupAction) {
        val newAction = when (action) {
            is PopupAction.PreviewAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.PreviewUpdateAction -> action.copy(content = transformPopupPreview(action.content))
            is PopupAction.ShowKeyboardAction -> {
                val label = action.keyboard.label
                if (label.length == 1 && label[0].isLetter())
                    action.copy(keyboard = KeyDef.Popup.Keyboard(transformAlphabet(label)))
                else action
            }
            else -> action
        }
        super.onPopupAction(newAction)
    }

    private fun switchCapsState(lock: Boolean = false) {
        capsState =
            if (lock) {
                when (capsState) {
                    CapsState.Lock -> CapsState.None
                    else -> CapsState.Lock
                }
            } else {
                when (capsState) {
                    CapsState.None -> CapsState.Once
                    else -> CapsState.None
                }
            }
        updateCapsButtonIcon()
        updateAlphabetKeys()
    }

    private fun updateCapsButtonIcon() {
        caps.img.apply {
            imageResource = when (capsState) {
                CapsState.None -> R.drawable.ic_capslock_none
                CapsState.Once -> R.drawable.ic_capslock_once
                CapsState.Lock -> R.drawable.ic_capslock_lock
            }
        }
    }

    private fun updateAlphabetKeys() {
        textKeys.forEach {
            if (it.def !is KeyDef.Appearance.AltText) return
            it.mainText.text = it.def.displayText.let { str ->
                if (str.length != 1 || !str[0].isLetter()) return@forEach
                if (keepLettersUppercase) str.uppercase() else transformAlphabet(str)
            }
        }
    }

    private fun updatePunctuationKeys() {
        textKeys.forEach {
            if (it is AltTextKeyView) {
                it.def as KeyDef.Appearance.AltText
                it.altText.text = transformPunctuation(it.def.altText)
            } else {
                it.def as KeyDef.Appearance.Text
                it.mainText.text = it.def.displayText.let { str ->
                    if (str[0].run { isLetter() || isWhitespace() }) return@forEach
                    transformPunctuation(str)
                }
            }
        }
    }

}