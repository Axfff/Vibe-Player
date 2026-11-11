package com.iven.musicplayergo.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/** Square viewport with bounded pinch zoom and drag; exported pixels match the preview exactly. */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private val transform = Matrix()
    private var source: Bitmap? = null
    private var minimumScale = 1f
    private var currentScale = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (currentScale * detector.scaleFactor).coerceIn(minimumScale, minimumScale * 6f)
            val factor = next / currentScale
            transform.postScale(factor, factor, detector.focusX, detector.focusY)
            currentScale = next
            constrain()
            invalidate()
            return true
        }
    })

    init { scaleType = ScaleType.MATRIX }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (360 * resources.displayMetrics.density).toInt()
        fun bound(spec: Int) = if (MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED) desired
            else MeasureSpec.getSize(spec)
        val edge = minOf(desired, bound(widthMeasureSpec), bound(heightMeasureSpec))
        setMeasuredDimension(edge, edge)
    }

    fun setCropBitmap(bitmap: Bitmap) {
        source = bitmap
        setImageBitmap(bitmap)
        if (width > 0 && height > 0) resetTransform()
    }

    fun export(size: Int = 1024): Bitmap {
        require(width > 0 && width == height && size > 0 && source != null)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { output ->
            val canvas = Canvas(output)
            canvas.scale(size.toFloat() / width, size.toFloat() / height)
            canvas.drawBitmap(source!!, transform, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (source != null && w > 0 && h > 0) resetTransform()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> if (dragging && !scaleDetector.isInProgress) {
                transform.postTranslate(event.x - lastX, event.y - lastY)
                lastX = event.x
                lastY = event.y
                constrain()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private fun resetTransform() {
        val bitmap = source ?: return
        transform.reset()
        minimumScale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        currentScale = minimumScale
        transform.postScale(minimumScale, minimumScale)
        transform.postTranslate(
            (width - bitmap.width * minimumScale) / 2f,
            (height - bitmap.height * minimumScale) / 2f
        )
        imageMatrix = transform
        invalidate()
    }

    private fun constrain() {
        val bitmap = source ?: return
        val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        transform.mapRect(bounds)
        val dx = when {
            bounds.left > 0 -> -bounds.left
            bounds.right < width -> width - bounds.right
            else -> 0f
        }
        val dy = when {
            bounds.top > 0 -> -bounds.top
            bounds.bottom < height -> height - bounds.bottom
            else -> 0f
        }
        transform.postTranslate(dx, dy)
        imageMatrix = transform
    }
}

