/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.floating

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewConfiguration
import androidx.core.view.doOnLayout
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

    private var overlayView: InputView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var handleListener: View.OnTouchListener? = null

    val inputView: InputView?
        get() = overlayView

    fun show(theme: Theme = ThemeManager.activeTheme) {
        if (overlayView != null) return
        val params = createLayoutParams()
        handleListener = createHandleTouchListener()
        val view = InputView(service, fcitx, theme).apply {
            reservePreeditLine(true)
            setFloatingDragHandle(handleListener, true)
        }
        overlayView = view
        layoutParams = params
        view.handleEvents = true
        attachDrag(view)
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
            view.setFloatingDragHandle(null, false)
        }
        overlayView = null
        layoutParams = null
        handleListener = null
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val metrics = service.resources.displayMetrics
        val width = (metrics.widthPixels * 0.85f).toInt()
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
            x = (metrics.widthPixels - width) / 2
            y = metrics.heightPixels / 4
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
    }

    private fun attachDrag(view: View) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        view.setOnTouchListener { _, event ->
            val lp = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    val targetX = initialX + dx
                    val targetY = initialY + dy
                    val bounded = boundToScreen(targetX, targetY, view)
                    lp.x = bounded.x
                    lp.y = bounded.y
                    windowManager.updateViewLayout(view, lp)
                    true
                }
                else -> false
            }
        }
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

    private fun createHandleTouchListener(): View.OnTouchListener {
        val handler = Handler(Looper.getMainLooper())
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var dragging = false
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        return View.OnTouchListener { _, event ->
            val lp = layoutParams ?: return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    dragging = false
                    handler.postDelayed({ dragging = true }, longPressTimeout)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return@OnTouchListener false
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    val targetX = initialX + dx
                    val targetY = initialY + dy
                    val view = overlayView ?: return@OnTouchListener false
                    val bounded = boundToScreen(targetX, targetY, view)
                    lp.x = bounded.x
                    lp.y = bounded.y
                    windowManager.updateViewLayout(view, lp)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacksAndMessages(null)
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun boundToScreen(targetX: Int, targetY: Int, view: View): Point {
        val metrics = service.resources.displayMetrics
        val maxX = metrics.widthPixels - view.width.coerceAtLeast(1)
        val maxY = metrics.heightPixels - view.height.coerceAtLeast(1)
        val x = targetX.coerceIn(0, maxX)
        val y = targetY.coerceIn(0, maxY)
        return Point(x, y)
    }
}
