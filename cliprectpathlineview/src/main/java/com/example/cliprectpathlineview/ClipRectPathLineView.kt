package com.example.cliprectpathlineview

import android.view.View
import android.view.MotionEvent
import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path


val colors : Array<Int> = arrayOf(
    "#f44336",
    "#673AB7",
    "#F57F17",
    "#0277BD",
    "#00C853"
).map {
    Color.parseColor(it)
}.toTypedArray()
val parts : Int = 4
val scGap : Float = 0.02f / parts
val strokeFactor : Float = 90f
val delay : Long = 20
val backColor : Int = Color.parseColor("#BDBDBD")

fun Int.inverse() : Float = 1f / this
fun Float.maxScale(i : Int, n : Int) : Float = Math.max(0f, this - i * n.inverse())
fun Float.divideScale(i : Int, n : Int) : Float = Math.min(n.inverse(), maxScale(i, n)) * n
fun Float.sinify() : Float = Math.sin(this * Math.PI).toFloat()

fun Canvas.drawClipRectPathLine(scale : Float, w : Float, h : Float, paint : Paint) {
    val sf : Float = scale.sinify()
    val sf1 : Float = sf.divideScale(0, parts)
    val sf2 : Float = sf.divideScale(1, parts)
    val sf3 : Float = sf.divideScale(2, parts)
    val sf4 : Float = sf.divideScale(3, parts)
    save()
    translate(w / 2, h / 2)
    for (j in 0..1) {
        save()
        scale(1f - 2 * j, 1f)
        drawLine(-w / 2, 0f, -w / 2 + w * 0.25f * sf1, 0f, paint)
        drawLine(-w / 4, 0f, -w / 4 + (w / 4) * sf2, -(h / 2) * sf2, paint)
        save()
        val path : Path = Path()
        path.moveTo(-w / 2, 0f)
        path.lineTo(-w / 4, 0f)
        path.lineTo(0f, -h / 2)
        path.lineTo(-w / 2, -h / 2)
        path.lineTo(-w / 2, 0f)
        clipPath(path)
        drawRect(
            RectF(
                -w / 2,
                -h / 2,
                -w / 2 + (w * 0.5f) * sf3,
                0f
            ),
            paint
        )
        restore()
        val triPath : Path = Path()
        triPath.moveTo(0f, -h / 2)
        triPath.lineTo((-w / 4) * sf4, -h / 2 + (h / 2) * sf4)
        triPath.lineTo(0f, -h / 2 + (h / 2)* sf4)
        triPath.lineTo(0f, - h / 2)
        drawPath(triPath, paint)
        restore()
    }
    restore()
}

fun Canvas.drawCRPLNode(i : Int, scale : Float, paint : Paint) {
    val w : Float = width.toFloat()
    val h : Float = height.toFloat()
    paint.color = colors[i]
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeWidth = Math.min(w, h) / strokeFactor
    drawClipRectPathLine(scale, w, h, paint)
}

class ClipRectPathLineView(ctx : Context) : View(ctx) {

    private val renderer : Renderer = Renderer(this)

    override fun onDraw(canvas : Canvas) {
        renderer.render(canvas)
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                renderer.handleTap()
            }
        }
        return true
    }

    data class State(var scale : Float = 0f, var dir : Float = 0f, var prevScale : Float = 0f) {

        fun update(cb : (Float) -> Unit) {
            scale += scGap * dir
            if (Math.abs(scale - prevScale) > 1) {
                scale = prevScale + dir
                dir = 0f
                prevScale = scale
                cb(prevScale)
            }
        }

        fun startUpdating(cb : () -> Unit) {
            if (dir == 0f) {
                dir = 1f - 2 * prevScale
                cb()
            }
        }
    }

    data class Animator(var view : View, var animated : Boolean = false) {

        fun animate(cb : () -> Unit) {
            if (animated) {
                cb()
                try {
                    Thread.sleep(delay)
                    view.invalidate()
                } catch(ex : Exception) {

                }
            }
        }

        fun start() {
            if (!animated) {
                animated = true
                view.postInvalidate()
            }
        }

        fun stop() {
            if (animated) {
                animated = false
            }
        }
    }

    data class CRPLNode(var i : Int, val state : State = State()) {

        private var next : CRPLNode? = null
        private var prev : CRPLNode? = null

        init {
            addNeighbor()
        }

        fun addNeighbor() {
            if (i < colors.size - 1) {
                next = CRPLNode(i + 1)
                next?.prev = this
            }
        }

        fun draw(canvas : Canvas, paint : Paint) {
            canvas.drawCRPLNode(i, state.scale, paint)
        }

        fun update(cb : (Float) -> Unit) {
            state.update(cb)
        }

        fun startUpdating(cb : () -> Unit) {
            state.startUpdating(cb)
        }

        fun getNext(dir : Int, cb : () -> Unit) : CRPLNode {
            var curr: CRPLNode? = prev
            if (dir == 1) {
                curr = next
            }
            if (curr != null) {
                return curr
            }
            cb()
            return this
        }
    }

    data class ClipRectPathLine(var i : Int) {

        private var curr : CRPLNode = CRPLNode(0)
        private var dir : Int = 1

        fun draw(canvas : Canvas, paint : Paint) {
            curr.draw(canvas, paint)
        }

        fun update(cb : (Float) -> Unit) {
            curr.update {
                curr = curr.getNext(dir) {
                    dir *= -1
                }
                cb(it)
            }
        }

        fun startUpdating(cb : () -> Unit) {
            curr.startUpdating(cb)
        }
    }

    data class Renderer(var view : ClipRectPathLineView) {

        private val paint : Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val crpl : ClipRectPathLine = ClipRectPathLine(0)
        private val animator : Animator = Animator(view)

        fun render(canvas : Canvas) {
            canvas.drawColor(backColor)
            crpl.draw(canvas, paint)
            animator.animate {
                crpl.update {
                    animator.stop()
                }
            }
        }

        fun handleTap() {
            crpl.startUpdating {
                animator.start()
            }
        }
    }

    companion object {

        fun create(activity : Activity) : ClipRectPathLineView {
            val view : ClipRectPathLineView = ClipRectPathLineView(activity)
            activity.setContentView(view)
            return view
        }
    }
}