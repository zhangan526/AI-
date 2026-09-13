package com.geekathon.guardpet

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Wrap of token chips with Smartisan/Nova-style drag-to-select a contiguous range. */
class TokenFlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    private val tokens = mutableListOf<String>()
    private val selected = sortedSetOf<Int>()
    private val gap = (6 * resources.displayMetrics.density).toInt()
    private val chipPaddingH = (8 * resources.displayMetrics.density).toInt()
    private val chipPaddingV = (6 * resources.displayMetrics.density).toInt()
    private val slop = (8 * resources.displayMetrics.density).toInt()
    private var dragStartIndex = -1
    private var dragging = false
    private var dragDeselect = false
    private val dragSnapshot = sortedSetOf<Int>()
    private var downX = 0f
    private var downY = 0f
    var onSelectionChanged: (() -> Unit)? = null

    fun setTokens(values: List<String>) {
        tokens.clear()
        tokens += values
        selected.clear()
        removeAllViews()
        values.forEach { token ->
            addView(createChip(token))
        }
        requestLayout()
        onSelectionChanged?.invoke()
    }

    fun selectedText(): String = TextTokenizer.joinSelected(tokens, selected)

    fun hasSelection(): Boolean = selected.isNotEmpty()

    fun invertSelection() {
        val inverted = (tokens.indices.toSet() - selected)
        selected.clear()
        selected.addAll(inverted)
        refreshChips()
        onSelectionChanged?.invoke()
    }

    private fun createChip(text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 16f
            setPadding(chipPaddingH, chipPaddingV, chipPaddingH, chipPaddingV)
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setBackgroundResource(R.drawable.token_bg)
            isClickable = false
            isFocusable = false
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var x = paddingStart
        var y = paddingTop
        var lineHeight = 0
        val innerWidth = width - paddingStart - paddingEnd
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.measure(childWidthSpec, childHeightSpec)
            val childW = child.measuredWidth
            val childH = child.measuredHeight
            if (x > paddingStart && x + childW > paddingStart + innerWidth) {
                x = paddingStart
                y += lineHeight + gap
                lineHeight = 0
            }
            lineHeight = max(lineHeight, childH)
            x += childW + gap
        }
        val wantedHeight = y + lineHeight + paddingBottom
        val height = resolveSize(wantedHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val innerWidth = r - l - paddingStart - paddingEnd
        var x = paddingStart
        var y = paddingTop
        var lineHeight = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val childW = child.measuredWidth
            val childH = child.measuredHeight
            if (x > paddingStart && x + childW > paddingStart + innerWidth) {
                x = paddingStart
                y += lineHeight + gap
                lineHeight = 0
            }
            child.layout(x, y, x + childW, y + childH)
            lineHeight = max(lineHeight, childH)
            x += childW + gap
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && hitIndex(event.x, event.y) >= 0) {
            return true
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragStartIndex = hitIndex(event.x, event.y)
                dragging = false
                dragDeselect = dragStartIndex in selected
                dragSnapshot.clear()
                dragSnapshot.addAll(selected)
                if (dragStartIndex >= 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragStartIndex < 0) return false
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx > slop || dy > slop) {
                    dragging = true
                    val current = hitIndex(event.x, event.y).takeIf { it >= 0 }
                        ?: nearestIndex(event.x, event.y)
                    applyDragRange(dragStartIndex, current)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragStartIndex >= 0 && event.actionMasked == MotionEvent.ACTION_UP) {
                    if (!dragging) {
                        toggle(dragStartIndex)
                    }
                }
                dragging = false
                dragStartIndex = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun toggle(index: Int) {
        if (!selected.add(index)) selected.remove(index)
        refreshChips()
        onSelectionChanged?.invoke()
    }

    private fun applyDragRange(from: Int, to: Int) {
        if (tokens.isEmpty()) return
        val start = min(from, to).coerceIn(0, tokens.lastIndex)
        val end = max(from, to).coerceIn(0, tokens.lastIndex)
        selected.clear()
        selected.addAll(dragSnapshot)
        for (index in start..end) {
            if (dragDeselect) selected.remove(index) else selected.add(index)
        }
        refreshChips()
        onSelectionChanged?.invoke()
    }

    private fun refreshChips() {
        for (index in 0 until childCount) {
            val chip = getChildAt(index) as? TextView ?: continue
            val on = index in selected
            chip.setBackgroundResource(if (on) R.drawable.token_bg_selected else R.drawable.token_bg)
            chip.setTextColor(
                if (on) Color.WHITE else ContextCompat.getColor(context, R.color.text_primary)
            )
        }
    }

    private fun hitIndex(x: Float, y: Float): Int {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (x >= child.left && x < child.right && y >= child.top && y < child.bottom) {
                return index
            }
        }
        return -1
    }

    private fun nearestIndex(x: Float, y: Float): Int {
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val cx = (child.left + child.right) / 2f
            val cy = (child.top + child.bottom) / 2f
            val dist = abs(x - cx) + abs(y - cy)
            if (dist < bestDist) {
                bestDist = dist
                best = index
            }
        }
        return best
    }
}
