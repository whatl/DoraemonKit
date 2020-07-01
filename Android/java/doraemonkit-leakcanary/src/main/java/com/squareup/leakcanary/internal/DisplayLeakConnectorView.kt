/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.leakcanary.internal

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.squareup.leakcanary.R

class DisplayLeakConnectorView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {
    enum class Type {
        HELP, START, START_LAST_REACHABLE, NODE_UNKNOWN, NODE_FIRST_UNREACHABLE, NODE_UNREACHABLE, NODE_REACHABLE, NODE_LAST_REACHABLE, END, END_FIRST_UNREACHABLE
    }

    private val classNamePaint: Paint
    private val leakPaint: Paint
    private val clearPaint: Paint
    private val referencePaint: Paint
    private val strokeSize: Float
    private val circleY: Float
    private var type: Type
    private var cache: Bitmap? = null
    override fun onDraw(canvas: Canvas) {
        val width = measuredWidth
        val height = measuredHeight
        if (cache != null && (cache!!.width != width || cache!!.height != height)) {
            cache!!.recycle()
            cache = null
        }
        if (cache == null) {
            cache = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val cacheCanvas = Canvas(cache!!)
            when (type) {
                Type.NODE_UNKNOWN -> drawItems(cacheCanvas, leakPaint, leakPaint)
                Type.NODE_UNREACHABLE, Type.NODE_REACHABLE -> drawItems(cacheCanvas, referencePaint, referencePaint)
                Type.NODE_FIRST_UNREACHABLE -> drawItems(cacheCanvas, leakPaint, referencePaint)
                Type.NODE_LAST_REACHABLE -> drawItems(cacheCanvas, referencePaint, leakPaint)
                Type.START -> {
                    drawStartLine(cacheCanvas)
                    drawItems(cacheCanvas, null, referencePaint)
                }
                Type.START_LAST_REACHABLE -> {
                    drawStartLine(cacheCanvas)
                    drawItems(cacheCanvas, null, leakPaint)
                }
                Type.END -> drawItems(cacheCanvas, referencePaint, null)
                Type.END_FIRST_UNREACHABLE -> drawItems(cacheCanvas, leakPaint, null)
                Type.HELP -> drawRoot(cacheCanvas)
                else -> throw UnsupportedOperationException("Unknown type $type")
            }
        }
        canvas.drawBitmap(cache!!, 0f, 0f, null)
    }

    private fun drawStartLine(cacheCanvas: Canvas) {
        val width = measuredWidth
        val halfWidth = width / 2f
        cacheCanvas.drawLine(halfWidth, 0f, halfWidth, circleY, classNamePaint)
    }

    private fun drawRoot(cacheCanvas: Canvas) {
        val width = measuredWidth
        val height = measuredHeight
        val halfWidth = width / 2f
        val radiusClear = halfWidth - strokeSize / 2f
        cacheCanvas.drawRect(0f, 0f, width.toFloat(), radiusClear, classNamePaint)
        cacheCanvas.drawCircle(0f, radiusClear, radiusClear, clearPaint)
        cacheCanvas.drawCircle(width.toFloat(), radiusClear, radiusClear, clearPaint)
        cacheCanvas.drawLine(halfWidth, 0f, halfWidth, height.toFloat(), classNamePaint)
    }

    private fun drawItems(cacheCanvas: Canvas, arrowHeadPaint: Paint?, nextArrowPaint: Paint?) {
        arrowHeadPaint?.let { drawArrowHead(cacheCanvas, it) }
        nextArrowPaint?.let { drawNextArrowLine(cacheCanvas, it) }
        drawInstanceCircle(cacheCanvas)
    }

    private fun drawArrowHead(cacheCanvas: Canvas, paint: Paint) {
        // Circle center is at half height
        val width = measuredWidth
        val halfWidth = width / 2f
        val circleRadius = width / 3f
        // Splitting the arrow head in two makes an isosceles right triangle.
        // It's hypotenuse is side * sqrt(2)
        val arrowHeight = halfWidth / 2 * SQRT_TWO
        val halfStrokeSize = strokeSize / 2
        val translateY = circleY - arrowHeight - circleRadius * 2 - strokeSize
        val lineYEnd = circleY - circleRadius - strokeSize / 2
        cacheCanvas.drawLine(halfWidth, 0f, halfWidth, lineYEnd, paint)
        cacheCanvas.translate(halfWidth, translateY)
        cacheCanvas.rotate(45f)
        cacheCanvas.drawLine(0f, halfWidth, halfWidth + halfStrokeSize, halfWidth,
                paint)
        cacheCanvas.drawLine(halfWidth, 0f, halfWidth, halfWidth, paint)
        cacheCanvas.rotate(-45f)
        cacheCanvas.translate(-halfWidth, -translateY)
    }

    private fun drawNextArrowLine(cacheCanvas: Canvas, paint: Paint) {
        val height = measuredHeight
        val width = measuredWidth
        val centerX = width / 2f
        cacheCanvas.drawLine(centerX, circleY, centerX, height.toFloat(), paint)
    }

    private fun drawInstanceCircle(cacheCanvas: Canvas) {
        val width = measuredWidth
        val circleX = width / 2f
        val circleRadius = width / 3f
        cacheCanvas.drawCircle(circleX, circleY, circleRadius, classNamePaint)
    }

    fun setType(type: Type) {
        if (type != this.type) {
            this.type = type
            if (cache != null) {
                cache!!.recycle()
                cache = null
            }
            invalidate()
        }
    }

    companion object {
        private val SQRT_TWO = Math.sqrt(2.0).toFloat()
        private val CLEAR_XFER_MODE = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    init {
        val resources = resources
        type = Type.NODE_UNKNOWN
        circleY = resources.getDimensionPixelSize(R.dimen.leak_canary_connector_center_y).toFloat()
        strokeSize = resources.getDimensionPixelSize(R.dimen.leak_canary_connector_stroke_size).toFloat()
        classNamePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        classNamePaint.color = resources.getColor(R.color.leak_canary_class_name)
        classNamePaint.strokeWidth = strokeSize
        leakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        leakPaint.color = resources.getColor(R.color.leak_canary_leak)
        leakPaint.style = Paint.Style.STROKE
        leakPaint.strokeWidth = strokeSize
        val pathLines = resources.getDimensionPixelSize(R.dimen.leak_canary_connector_leak_dash_line).toFloat()
        val pathGaps = resources.getDimensionPixelSize(R.dimen.leak_canary_connector_leak_dash_gap).toFloat()
        leakPaint.pathEffect = DashPathEffect(floatArrayOf(pathLines, pathGaps), 0f)
        clearPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        clearPaint.color = Color.TRANSPARENT
        clearPaint.xfermode = CLEAR_XFER_MODE
        referencePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        referencePaint.color = resources.getColor(R.color.leak_canary_reference)
        referencePaint.strokeWidth = strokeSize
    }
}