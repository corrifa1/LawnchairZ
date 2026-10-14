/*
 * Copyright 2026 Daryl Denson and Expressive Launcher contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Developed for Expressive Launcher.
 * https://github.com/denson9874/ExpressiveLauncher
 * See NOTICE and docs/SOURCE_PROVENANCE.md for project attribution.
 */

package app.lawnchair.widgetstack

import android.animation.ValueAnimator
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.LauncherBindableItemsContainer
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.ListenableHostView
import com.android.launcher3.widget.NavigableAppWidgetHostView
import com.android.launcher3.widget.RoundedCornerEnforcement
import com.android.launcher3.widget.util.WidgetSizes
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shows the widgets of a [WidgetStackInfo] in one set of cells and pages between them with
 * vertical swipes.
 *
 * Children are the real host views, added once and never re-parented or recycled. The visible
 * child and its neighbours stay laid out; the rest are GONE but attached, so they keep receiving
 * updates. Extending [NavigableAppWidgetHostView] makes the cell layout size, scale and reorder
 * the stack exactly like a widget, and lets the drag code treat it as one.
 *
 * Touch rules (see docs/widget-stacks.md):
 * - A mostly-vertical drag pages the stack, unless the widget under the finger can scroll in that
 *   direction, in which case the widget owns the whole gesture.
 * - Horizontal drags are left to the workspace. Taps and long-presses go to the child widgets;
 *   a child's long-press is forwarded to the stack so the whole stack is picked up.
 * - The launcher's vertical swipe controllers skip touches that start on a pageable stack (see
 *   [WidgetStackTouch]).
 */
class WidgetStackView(context: Context) : NavigableAppWidgetHostView(context) {

    var stackInfo: WidgetStackInfo? = null
        private set

    private val pages = ArrayList<View>()
    private val updateListeners = HashMap<View, SafeCloseable>()

    /** Shown position in pages; fractional while dragging or settling. */
    private var position = 0f

    /** The page the stack rests on. Its neighbours are kept laid out. */
    private var settledPage = 0

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingThresholdPx = 500 * resources.displayMetrics.density
    private var velocityTracker: VelocityTracker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragStartPosition = 0f
    private var gestureDecided = false
    private var isPaging = false
    private var isTouching = false

    private val spring = SpringAnimation(this, OFFSET_PX).apply {
        spring = SpringForce().setDampingRatio(SPRING_DAMPING).setStiffness(SPRING_STIFFNESS)
        addEndListener { _, canceled, _, _ -> if (!canceled) onSettled(springTarget) }
    }
    private var springTarget = 0

    /** The launcher's widget corner radius; the stack clips its pages to it. */
    val cornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(context)

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(2 * resources.displayMetrics.density, 0f, 0f, 0x55000000)
    }
    private val dotRadius = 2.5f * resources.displayMetrics.density
    private val dotSpacing = 8f * resources.displayMetrics.density
    private val dotInset = 7f * resources.displayMetrics.density
    private var indicatorAlpha = 0f
    private var indicatorAnimator: ValueAnimator? = null
    private val hideIndicator = Runnable { animateIndicator(0f) }

    private var emptyView: View? = null
    private var appliedSpanX = -1
    private var appliedSpanY = -1

    private var bindTime = 0L
    private var pendingRotation = -1
    private var isVisibleToUser = false

    /** The launcher hosting this stack, or null in previews. */
    private val launcher: Launcher? by lazy {
        var c: Context? = context
        while (c != null) {
            if (c is Launcher) return@lazy c
            c = (c as? ContextWrapper)?.baseContext
        }
        null
    }

    private val stackWriter: WidgetStackWriter? by lazy {
        launcher?.let { WidgetStackWriter(it.modelWriter, it.cellPosMapper) }
    }

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        clipToOutline = true
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        // Move, remove and edit actions, as for other workspace items.
        accessibilityDelegate = mActivity.accessibilityDelegate
    }

    /** Attaches [info] and its children's host views, in rank order. */
    fun bind(info: WidgetStackInfo, children: List<AppWidgetHostView>) {
        stackInfo = info
        tag = info
        updateListeners.values.forEach { it.close() }
        updateListeners.clear()
        removeAllViews()
        pages.clear()
        emptyView = null

        children.forEach { child ->
            addView(child, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            pages.add(child)
            // A child's long-press picks up the whole stack.
            child.setOnLongClickListener { performLongClick() }
            if (child is ListenableHostView) {
                updateListeners[child] = child.addUpdateListener { onChildUpdated(child) }
            }
        }
        if (pages.isEmpty()) showEmptyView()

        bindTime = SystemClock.uptimeMillis()
        settledPage = info.activeRank.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        appliedSpanX = -1
        appliedSpanY = -1
        applyChildSizesIfNeeded()
        updatePageVisibility()
        applyPosition(settledPage.toFloat())
        updateAccessibilityState()
    }

    /**
     * Attaches widgets inflated off the main thread to the widget host so they receive updates,
     * as Launcher does for top-level widgets. The host may hand back a different view.
     */
    fun attachPagesToHost(holder: LauncherWidgetHolder) {
        val info = stackInfo ?: return
        var changed = false
        val attached = pages.map { page ->
            if (page !is LauncherAppWidgetHostView) return@map page
            holder.attachViewToHostAndGetAttachedView(page).also { view ->
                if (view !== page) {
                    changed = true
                    if (view.tag == null) view.tag = page.tag
                }
            }
        }
        if (changed) bind(info, attached.filterIsInstance<AppWidgetHostView>())
    }

    /** The host view of the visible child, if any. */
    val activeChildView: View? get() = pages.getOrNull(settledPage)

    val pageCount: Int get() = pages.size

    /** The children's host views, in rank order. */
    val pageViews: List<View> get() = pages

    /**
     * Removes a child's view without touching the model; the caller updates the info. The
     * visible child stays visible unless it is the one removed.
     */
    fun removePage(child: View) {
        val index = pages.indexOf(child)
        if (index < 0) return
        spring.cancel()
        updateListeners.remove(child)?.close()
        pages.removeAt(index)
        removeView(child)
        settledPage = WidgetStackOrdering.activeAfterRemove(settledPage, index, pages.size)
        if (pages.isEmpty()) showEmptyView()
        updatePageVisibility()
        applyPosition(settledPage.toFloat())
        updateAccessibilityState()
    }

    /**
     * Re-orders the existing pages to match the info's contents after a reorder, without
     * re-inflating any widget, and shows the info's visible child.
     */
    fun syncPagesToInfo() {
        val info = stackInfo ?: return
        spring.cancel()
        pages.sortBy { page -> info.contents.indexOfFirst { it === page.tag }.let { if (it < 0) Int.MAX_VALUE else it } }
        settledPage = info.activeRank.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        updatePageVisibility()
        applyPosition(settledPage.toFloat())
        updateAccessibilityState()
    }

    /** Applies [op] to each child, stopping at the first match, like Workspace.mapOverItems. */
    fun mapOverPages(op: LauncherBindableItemsContainer.ItemOperator): View? =
        pages.firstOrNull { op.evaluate(it.tag as? ItemInfo, it) }

    /** Whether vertical swipes on this stack should page it rather than reach the launcher. */
    fun canPage(): Boolean {
        if (pages.size < 2) return false
        return launcher?.isInState(LauncherState.NORMAL) ?: true
    }

    /** Pages to [page], animating unless animations are off. */
    fun pageTo(page: Int, animate: Boolean = true) {
        if (pages.isEmpty()) return
        val target = page.coerceIn(0, pages.size - 1)
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || !isLaidOut) {
            spring.cancel()
            applyPosition(target.toFloat())
            onSettled(target)
        } else {
            showIndicator()
            animateTo(target, 0f)
        }
    }

    override fun shouldAllowDirectClick() = false

    /** A short settle-in pulse for a stack that was just created or grew. */
    fun playCreateAnimation() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        showIndicator()
        ValueAnimator.ofFloat(0.9f, 1f).apply {
            duration = CREATE_ANIM_MS
            interpolator = OvershootInterpolator(1.5f)
            addUpdateListener { reorderBounceScale = it.animatedValue as Float }
            start()
        }
        scheduleIndicatorHide()
    }

    // ---------------------------------------------------------------------------------------
    // Layout and sizing

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyChildSizesIfNeeded()
        applyPosition(position)
        invalidateOutline()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // The cell layout updates the info's span before laying out a resized stack.
        applyChildSizesIfNeeded()
    }

    /** Sends every child the stack's size, once per span change, as for a resized widget. */
    private fun applyChildSizesIfNeeded() {
        val info = stackInfo ?: return
        if (info.spanX == appliedSpanX && info.spanY == appliedSpanY) return
        appliedSpanX = info.spanX
        appliedSpanY = info.spanY
        // Keep the children's rows in step with a resized stack (persisted rows only).
        info.syncChildGeometry().filter { it.id != ItemInfo.NO_ID }.forEach {
            launcher?.modelWriter?.modifyItemInDatabase(it, info.id, info.screenId, 0, 0, info.spanX, info.spanY)
        }
        pages.forEach { child ->
            if (child is AppWidgetHostView) {
                WidgetSizes.updateWidgetSizeRanges(child, context, info.spanX, info.spanY)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Paging

    private fun applyPosition(p: Float) {
        position = p
        val h = height.toFloat()
        pages.forEachIndexed { i, child ->
            if (child.visibility == GONE) return@forEachIndexed
            val distance = i - p
            child.translationY = distance * h
            val scale = WidgetStackPhysics.childScale(distance)
            child.scaleX = scale
            child.scaleY = scale
            child.alpha = WidgetStackPhysics.childAlpha(distance)
            // Neighbours sit outside the clip when settled; skip drawing them.
            child.visibility = if (abs(distance) < 1f) VISIBLE else INVISIBLE
        }
        invalidate()
    }

    /** Lays out the settled page and its neighbours; the rest are GONE but stay attached. */
    private fun updatePageVisibility() {
        pages.forEachIndexed { i, child ->
            val near = abs(i - settledPage) <= 1
            if (!near) {
                child.visibility = GONE
            } else if (child.visibility == GONE) {
                child.visibility = INVISIBLE
            }
            child.importantForAccessibility = if (i == settledPage) {
                IMPORTANT_FOR_ACCESSIBILITY_AUTO
            } else {
                IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        }
    }

    private fun animateTo(target: Int, velocityPagesPerSecond: Float) {
        val h = height.toFloat()
        if (h <= 0f || !ValueAnimator.areAnimatorsEnabled()) {
            applyPosition(target.toFloat())
            onSettled(target)
            return
        }
        springTarget = target
        // Keep the spring in step with the system animation speed.
        val scale = ValueAnimator.getDurationScale().coerceAtLeast(0.1f)
        spring.spring.stiffness = SPRING_STIFFNESS / (scale * scale)
        spring.spring.finalPosition = target * h
        spring.setStartValue(position * h)
        spring.setStartVelocity(velocityPagesPerSecond * h)
        spring.start()
    }

    private fun onSettled(page: Int) {
        val changed = page != settledPage
        settledPage = page
        position = page.toFloat()
        updatePageVisibility()
        applyPosition(position)
        if (changed) {
            performHapticFeedback(
                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
            stackInfo?.let { info ->
                val writer = stackWriter
                if (writer != null) writer.setActiveRank(info, page) else info.activeRank = page
            }
            updateAccessibilityState()
        }
        scheduleIndicatorHide()
    }

    // ---------------------------------------------------------------------------------------
    // Touch

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!canPage()) return false
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                gestureDecided = false
                isPaging = false
                isTouching = true
                if (spring.isRunning) {
                    // Catch the page mid-flight.
                    spring.cancel()
                    beginPaging(ev.y)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureDecided) decideGesture(ev)
                return isPaging
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endTouch()
        }
        return isPaging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!canPage()) return super.onTouchEvent(ev)
        trackVelocity(ev)
        when (ev.actionMasked) {
            // No child took the touch (empty or pending stack): keep tracking so it can page.
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                gestureDecided = false
                isTouching = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureDecided) decideGesture(ev)
                if (isPaging) dragTo(ev.y)
            }
            MotionEvent.ACTION_UP -> {
                if (isPaging) release()
                endTouch()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isPaging) animateTo(position.roundToInt().coerceIn(0, pages.size - 1), 0f)
                endTouch()
            }
        }
        return true
    }

    private fun decideGesture(ev: MotionEvent) {
        val dx = ev.x - downX
        val dy = ev.y - downY
        if (abs(dx) < touchSlop && abs(dy) < touchSlop) return
        gestureDecided = true
        // Finger moving up reveals the next widget, so the content would scroll down (+1).
        val direction = if (dy < 0) 1 else -1
        if (abs(dy) > abs(dx) && !canChildScroll(downX, downY, direction)) {
            beginPaging(ev.y)
        }
    }

    /** Starts paging from the current position, anchored at [anchorY] so nothing jumps. */
    private fun beginPaging(anchorY: Float) {
        isPaging = true
        gestureDecided = true
        dragStartPosition = position
        downY = anchorY
        // Keep the workspace and the drag layer's controllers from taking the gesture.
        parent?.requestDisallowInterceptTouchEvent(true)
        pages.forEach { it.cancelLongPress() }
        showIndicator()
    }

    private fun dragTo(y: Float) {
        val h = height.toFloat().takeIf { it > 0f } ?: return
        // One page per gesture, like iOS; the rubber band handles the ends.
        val raw = (dragStartPosition - (y - downY) / h).coerceIn(settledPage - 1f, settledPage + 1f)
        applyPosition(WidgetStackPhysics.rubberBand(raw, pages.size))
    }

    private fun release() {
        val h = height.toFloat().takeIf { it > 0f } ?: return
        val tracker = velocityTracker
        tracker?.computeCurrentVelocity(1000)
        val velocityPx = -(tracker?.yVelocity ?: 0f)
        val target = WidgetStackPhysics.settleTarget(
            position, velocityPx / h, settledPage, pages.size, flingThresholdPx / h,
        )
        animateTo(target, velocityPx / h)
    }

    private fun endTouch() {
        isTouching = false
        isPaging = false
        gestureDecided = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun trackVelocity(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) velocityTracker?.clear()
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        tracker.addMovement(ev)
    }

    /** Whether the view under ([x], [y]) in the visible child can scroll vertically in [direction]. */
    private fun canChildScroll(x: Float, y: Float, direction: Int): Boolean {
        val child = activeChildView ?: return false
        return canScrollAt(child, x - child.left - child.translationX, y - child.top - child.translationY, direction)
    }

    private fun canScrollAt(view: View, x: Float, y: Float, direction: Int): Boolean {
        if (view.canScrollVertically(direction)) return true
        if (view !is ViewGroup) return false
        for (i in view.childCount - 1 downTo 0) {
            val c = view.getChildAt(i)
            if (c.visibility != VISIBLE) continue
            val lx = x + view.scrollX - c.left - c.translationX
            val ly = y + view.scrollY - c.top - c.translationY
            if (lx >= 0 && ly >= 0 && lx < c.width && ly < c.height && canScrollAt(c, lx, ly, direction)) {
                return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------------------------------
    // Page indicator

    private fun showIndicator() {
        removeCallbacks(hideIndicator)
        animateIndicator(1f)
    }

    private fun scheduleIndicatorHide() {
        removeCallbacks(hideIndicator)
        // Always schedule: the indicator may still be fading in and read as 0 here.
        postDelayed(hideIndicator, INDICATOR_HIDE_DELAY_MS)
    }

    private fun animateIndicator(target: Float) {
        indicatorAnimator?.cancel()
        if (indicatorAlpha == target) return
        indicatorAnimator = ValueAnimator.ofFloat(indicatorAlpha, target).apply {
            duration = INDICATOR_FADE_MS
            addUpdateListener {
                indicatorAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (indicatorAlpha <= 0f || pages.size < 2) return
        val x = if (Utilities.isRtl(resources)) dotInset else width - dotInset
        val top = height / 2f - (pages.size - 1) * dotSpacing / 2f
        for (i in pages.indices) {
            val emphasis = 1f - abs(i - position).coerceAtMost(1f)
            dotPaint.alpha = (255 * indicatorAlpha * (0.4f + 0.6f * emphasis)).toInt()
            canvas.drawCircle(x, top + i * dotSpacing, dotRadius, dotPaint)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Smart Rotate

    private fun onChildUpdated(child: View) {
        val info = stackInfo ?: return
        if (!info.smartRotate || isTouching) return
        // Ignore the burst of updates that follows binding.
        if (SystemClock.uptimeMillis() - bindTime < SMART_ROTATE_SETTLE_MS) return
        val page = pages.indexOf(child)
        // Never rotate while the user can see the stack; apply it before they next do.
        if (page >= 0 && !isVisibleToUser) pendingRotation = page
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        isVisibleToUser = isVisible
        if (isVisible && pendingRotation >= 0) {
            val page = pendingRotation
            pendingRotation = -1
            if (page != settledPage && stackInfo?.smartRotate == true) pageTo(page, animate = false)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Accessibility

    private fun childLabel(page: Int): CharSequence? {
        val child = pages.getOrNull(page) as? AppWidgetHostView ?: return null
        return child.appWidgetInfo?.loadLabel(context.packageManager)
            ?: (child.tag as? LauncherAppWidgetInfo)?.providerName?.packageName
    }

    private fun updateAccessibilityState() {
        val label = childLabel(settledPage)
        contentDescription = if (label != null) {
            context.getString(R.string.widget_stack_description, label)
        } else {
            context.getString(R.string.widget_stack_label)
        }
        stateDescription = if (pages.isEmpty()) {
            null
        } else {
            context.getString(R.string.widget_stack_position, settledPage + 1, pages.size)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (settledPage < pages.size - 1) {
            info.addAction(
                AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
                    context.getString(R.string.widget_stack_next),
                ),
            )
        }
        if (settledPage > 0) {
            info.addAction(
                AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                    context.getString(R.string.widget_stack_previous),
                ),
            )
        }
        info.isScrollable = pages.size > 1
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> if (settledPage < pages.size - 1) {
                pageTo(settledPage + 1)
                return true
            }
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> if (settledPage > 0) {
                pageTo(settledPage - 1)
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private fun showEmptyView() {
        val view = TextView(context).apply {
            setText(R.string.widget_stack_empty)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0x33000000)
            // Lets an empty stack be picked up, moved and deleted.
            setOnLongClickListener { this@WidgetStackView.performLongClick() }
        }
        emptyView = view
        addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        spring.cancel()
        indicatorAnimator?.cancel()
        removeCallbacks(hideIndicator)
        indicatorAlpha = 0f
    }

    companion object {
        private const val SPRING_STIFFNESS = 500f
        private const val SPRING_DAMPING = 0.88f
        private const val INDICATOR_FADE_MS = 200L
        private const val INDICATOR_HIDE_DELAY_MS = 1000L
        private const val SMART_ROTATE_SETTLE_MS = 3000L
        private const val CREATE_ANIM_MS = 320L

        /** The scroll offset in pixels; the spring animates this. */
        private val OFFSET_PX = object : FloatPropertyCompat<WidgetStackView>("stackOffset") {
            override fun getValue(view: WidgetStackView) = view.position * view.height
            override fun setValue(view: WidgetStackView, value: Float) {
                val h = view.height.toFloat()
                if (h > 0f) view.applyPosition(value / h)
            }
        }

        /**
         * Inflates a stack for [info]. [inflateChild] creates each child's host view, or returns
         * null when the child cannot be shown and was removed from the model.
         */
        @JvmStatic
        fun inflate(
            context: Context,
            info: WidgetStackInfo,
            inflateChild: (LauncherAppWidgetInfo) -> AppWidgetHostView?,
        ): WidgetStackView {
            val children = ArrayList<AppWidgetHostView>()
            info.contents.toList().forEach { child ->
                val view = inflateChild(child)
                if (view == null) info.remove(child) else children.add(view)
            }
            return WidgetStackView(context).apply { bind(info, children) }
        }
    }
}
