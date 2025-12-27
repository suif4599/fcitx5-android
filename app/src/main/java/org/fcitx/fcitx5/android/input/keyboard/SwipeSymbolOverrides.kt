/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import org.json.JSONObject

object SwipeSymbolOverrides {
    fun parse(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap(json.length()) {
                json.keys().forEach { key ->
                    val value = json.optString(key, "")
                    if (key.isNotEmpty() && value.isNotEmpty()) put(key, value)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
