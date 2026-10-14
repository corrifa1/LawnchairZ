/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R

/**
 * The "Widget stack" entry in the widget picker. It places an empty stack on the home screen and
 * opens the stack editor, where widgets that fit are added.
 */
object WidgetStackPickerEntry {

    /** Adds the entry under the picker's [title] when the picker belongs to the launcher. */
    @JvmStatic
    fun attach(activity: Any, title: TextView?) {
        val launcher = activity as? Launcher ?: return
        val header = title?.parent as? ViewGroup ?: return
        if (!PreferenceManager2.getInstance(launcher).widgetStacks.firstCached()) return

        val density = launcher.resources.displayMetrics.density
        val color = title.currentTextColor
        val entry = TextView(launcher).apply {
            setText(R.string.widget_stack_label)
            setTextColor(color)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_widget_stack, 0, 0, 0)
            compoundDrawableTintList = ColorStateList.valueOf(color)
            compoundDrawablePadding = (8 * density).toInt()
            val padH = (16 * density).toInt()
            val padV = (8 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = 100 * density
                setColor(ColorUtils.setAlphaComponent(color, 0x1F))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AbstractFloatingView.closeAllOpenViews(launcher)
                val stack = WidgetStackController.placeEmptyStack(launcher)
                if (stack == null) {
                    Toast.makeText(launcher, R.string.out_of_space, Toast.LENGTH_SHORT).show()
                } else {
                    WidgetStackEditor.show(launcher, stack)
                }
            }
        }

        val margin = (8 * density).toInt()
        when (header) {
            is RelativeLayout -> header.addView(
                entry,
                RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_END)
                    addRule(RelativeLayout.ALIGN_BASELINE, title.id)
                    marginEnd = margin
                },
            )
            is LinearLayout -> header.addView(
                entry,
                header.indexOfChild(title) + 1,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = margin
                },
            )
            else -> Unit
        }
    }
}
