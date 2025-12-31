/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.floating

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.view.doOnLayout
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.InputView
import org.fcitx.fcitx5.android.daemon.FcitxConnection

class FloatingKeyboardController(
    private val service: FcitxInputMethodService,
    private val fcitx: FcitxConnection
) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val keyboardPrefs = AppPrefs.getInstance().keyboard
    private val scalePref = keyboardPrefs.floatingKeyboardScale
    private val scaleChangeListener = ManagedPreference.OnChangeListener<Float> { _, value ->
        applyScale(value)
    }

    private var overlayView: InputView? = null
    val inputView: InputView?
        get() = overlayView
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentScale = clampScale(keyboardPrefs.floatingKeyboardScale.getValue())

    init {
        scalePref.registerOnChangeListener(scaleChangeListener)
    }

    private fun dragTouchListener(target: View) = View.OnTouchListener { _, event ->
        val lp = layoutParams ?: return@OnTouchListener false
        val view = overlayView ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                target.tag = DragState(lp.x, lp.y, event.rawX, event.rawY)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val state = target.tag as? DragState ?: return@OnTouchListener false
                val dx = (event.rawX - state.startX).toInt()
                val dy = (event.rawY - state.startY).toInt()
                val targetX = state.initialX + dx
                val targetY = state.initialY + dy
                val bounded = boundToScreen(targetX, targetY, view)
                lp.x = bounded.x
                lp.y = bounded.y
                windowManager.updateViewLayout(view, lp)
                true
            }
            else -> false
        }
    }

    fun show(theme: Theme = ThemeManager.activeTheme) {
        if (overlayView != null) return
        currentScale = clampScale(keyboardPrefs.floatingKeyboardScale.getValue())
        val params = createLayoutParams(currentScale)
        val view = InputView(service, fcitx, theme).apply {
            reservePreeditLine(true)
            configureFloatingDrag(true, dragTouchListener(this))
            setFloatingScale(1f)
            scaleX = currentScale
            scaleY = currentScale
            pivotX = 0f
            pivotY = 0f
        }
        overlayView = view
        layoutParams = params
        view.handleEvents = true
        view.setOnTouchListener(dragTouchListener(view))
        windowManager.addView(view, params)
    }

    fun hide() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // view might not be attached
            }
            view.handleEvents = false
        }
        overlayView = null
        layoutParams = null
    }

    fun destroy() {
        scalePref.unregisterOnChangeListener(scaleChangeListener)
    }

    @Suppress("DEPRECATION")
    private fun createLayoutParams(scale: Float): WindowManager.LayoutParams {
        val metrics = service.resources.displayMetrics
        val width = (metrics.widthPixels * BASE_WIDTH_RATIO).toInt()
        val height = WindowManager.LayoutParams.WRAP_CONTENT
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val visibleWidth = (width * scale).toInt()
            x = (metrics.widthPixels - visibleWidth) / 2
            y = (metrics.heightPixels * DEFAULT_Y_RATIO).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
    }

    private fun attachDrag(view: View) {
        view.setOnTouchListener(dragTouchListener(view))
        view.doOnLayout {
            // ensure initial bounds are within screen after first layout
            layoutParams?.let { lp ->
                val bounded = boundToScreen(lp.x, lp.y, view)
                if (bounded.x != lp.x || bounded.y != lp.y) {
                    lp.x = bounded.x
                    lp.y = bounded.y
                    windowManager.updateViewLayout(view, lp)
                }
            }
        }
    }

    private fun boundToScreen(targetX: Int, targetY: Int, view: View): Point {
        val metrics = service.resources.displayMetrics
        val scaledWidth = (view.width * view.scaleX).toInt().coerceAtLeast(1)
        val scaledHeight = (view.height * view.scaleY).toInt().coerceAtLeast(1)
        val maxX = metrics.widthPixels - scaledWidth
        val maxY = metrics.heightPixels - scaledHeight
        val x = targetX.coerceIn(0, maxX)
        val y = targetY.coerceIn(0, maxY)
        return Point(x, y)
    }

    fun attachDragHandle(handle: View) {
        handle.setOnTouchListener(dragTouchListener(overlayView ?: handle))
    }

    private fun applyScale(scale: Float) {
        val clamped = clampScale(scale)
        currentScale = clamped
        overlayView?.let { view ->
            view.setFloatingScale(1f)
            view.scaleX = clamped
            view.scaleY = clamped
            view.pivotX = 0f
            view.pivotY = 0f
            layoutParams?.let { lp ->
                val baseWidth = (service.resources.displayMetrics.widthPixels * BASE_WIDTH_RATIO).toInt()
                lp.width = baseWidth
                val visibleWidth = (baseWidth * clamped).toInt()
                lp.x = (service.resources.displayMetrics.widthPixels - visibleWidth) / 2
                windowManager.updateViewLayout(view, lp)
                val bounded = boundToScreen(lp.x, lp.y, view)
                if (bounded.x != lp.x || bounded.y != lp.y) {
                    lp.x = bounded.x
                    lp.y = bounded.y
                    windowManager.updateViewLayout(view, lp)
                }
            }
        }
    }

    private fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    private data class DragState(
        val initialX: Int,
        val initialY: Int,
        val startX: Float,
        val startY: Float
    )

    companion object {
        private const val BASE_WIDTH_RATIO = 0.85f
        private const val DEFAULT_Y_RATIO = 0.25f
        private const val MIN_SCALE = 0.2f
        private const val MAX_SCALE = 1.0f
    }
}
