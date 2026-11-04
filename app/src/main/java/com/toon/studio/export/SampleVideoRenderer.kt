package com.toon.studio.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.sin

object SampleVideoRenderer {
    const val DURATION_SECONDS = 8

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private val lines = listOf(
        Triple(0.8f, 4.0f, "Sungura: Habari za asubuhi, msitu!"),
        Triple(4.2f, 7.4f, "Sungura: Hii ni video yangu ya kwanza!")
    )

    fun draw(c: Canvas, t: Float, w: Float, h: Float) {
        val horizon = h * 0.68f

        p.shader = LinearGradient(
            0f, 0f, 0f, horizon,
            Color.rgb(120, 200, 255), Color.rgb(225, 245, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, horizon, p)
        p.shader = null

        p.color = Color.rgb(255, 214, 64)
        c.drawCircle(w * 0.85f, h * 0.17f, 62f + 4f * sin(t * 3f), p)

        p.color = Color.WHITE
        cloud(c, wrap(w * 0.2f - t * 25f, w, 200f), h * 0.14f, 1f)
        cloud(c, wrap(w * 0.6f - t * 18f, w, 200f), h * 0.26f, 0.8f)

        p.color = Color.rgb(110, 190, 110)
        c.drawOval(RectF(-200f, horizon - 120f, w * 0.6f, horizon + 80f), p)
        c.drawOval(RectF(w * 0.4f, horizon - 90f, w + 250f, horizon + 80f), p)

        p.color = Color.rgb(76, 160, 80)
        c.drawRect(0f, horizon, w, h, p)

        for (i in 0 until 4) {
            tree(c, wrap(i * 380f - t * 140f, w, 150f), horizon + 10f)
        }

        p.color = Color.rgb(196, 160, 100)
        c.drawRect(0f, h * 0.90f, w, h, p)

        val talking = lines.any { t >= it.first && t <= it.second }
        rabbit(c, w * 0.38f, h * 0.86f, t, talking)
        subtitle(c, w, h, t)
        fade(c, w, h, t)
    }

    private fun wrap(x: Float, w: Float, margin: Float): Float {
        val span = w + 2f * margin
        var v = (x + margin) % span
        if (v < 0f) v += span
        return v - margin
    }

    private fun cloud(c: Canvas, x: Float, y: Float, s: Float) {
        c.drawCircle(x, y, 34f * s, p)
        c.drawCircle(x + 38f * s, y - 12f * s, 42f * s, p)
        c.drawCircle(x + 80f * s, y, 34f * s, p)
        c.drawRect(x, y, x + 80f * s, y + 34f * s, p)
    }

    private fun tree(c: Canvas, x: Float, baseY: Float) {
        p.color = Color.rgb(110, 75, 45)
        c.drawRect(x - 12f, baseY - 95f, x + 12f, baseY, p)
        p.color = Color.rgb(40, 130, 60)
        c.drawCircle(x, baseY - 125f, 58f, p)
        c.drawCircle(x - 42f, baseY - 95f, 42f, p)
        c.drawCircle(x + 42f, baseY - 95f, 42f, p)
    }

    private fun rabbit(c: Canvas, cx: Float, baseY: Float, t: Float, talking: Boolean) {
        val bob = abs(sin(t * 7f)) * 26f
        val y = baseY - bob
        val swing = sin(t * 7f) * 18f

        p.color = Color.argb(60, 0, 0, 0)
        c.drawOval(RectF(cx - 110f, baseY - 6f, cx + 90f, baseY + 14f), p)

        p.color = Color.WHITE
        c.drawCircle(cx - 100f, y - 95f, 22f, p)

        p.color = Color.rgb(130, 85, 50)
        c.drawOval(RectF(cx - 80f + swing, y - 30f, cx - 25f + swing, y + 12f), p)
        c.drawOval(RectF(cx + 25f - swing, y - 30f, cx + 75f - swing, y + 12f), p)

        p.color = Color.rgb(150, 100, 60)
        c.drawOval(RectF(cx - 100f, y - 140f, cx + 60f, y - 30f), p)
        p.color = Color.rgb(230, 205, 170)
        c.drawOval(RectF(cx - 60f, y - 110f, cx + 30f, y - 45f), p)

        c.save()
        c.rotate(sin(t * 7f) * 6f, cx + 75f, y - 190f)
        p.color = Color.rgb(150, 100, 60)
        c.drawOval(RectF(cx + 50f, y - 265f, cx + 78f, y - 195f), p)
        c.drawOval(RectF(cx + 82f, y - 270f, cx + 110f, y - 198f), p)
        p.color = Color.rgb(255, 170, 190)
        c.drawOval(RectF(cx + 57f, y - 255f, cx + 71f, y - 205f), p)
        c.drawOval(RectF(cx + 89f, y - 260f, cx + 103f, y - 208f), p)
        c.restore()

        p.color = Color.rgb(160, 110, 70)
        c.drawCircle(cx + 75f, y - 150f, 52f, p)

        val eyeOpen = (t % 3f) >= 0.12f
        if (eyeOpen) {
            p.color = Color.WHITE
            c.drawCircle(cx + 95f, y - 160f, 14f, p)
            p.color = Color.BLACK
            c.drawCircle(cx + 99f, y - 160f, 7f, p)
        } else {
            p.color = Color.BLACK
            c.drawRect(cx + 83f, y - 162f, cx + 109f, y - 158f, p)
        }

        p.color = Color.rgb(255, 120, 150)
        c.drawCircle(cx + 122f, y - 142f, 7f, p)

        if (talking) {
            val open = abs(sin(t * 14f)) * 10f
            p.color = Color.rgb(90, 30, 40)
            c.drawOval(RectF(cx + 100f, y - 128f, cx + 120f, y - 124f + open), p)
        }
    }

    private fun subtitle(c: Canvas, w: Float, h: Float, t: Float) {
        val line = lines.firstOrNull { t >= it.first && t <= it.second } ?: return
        p.textAlign = Paint.Align.CENTER
        p.textSize = 46f
        p.typeface = Typeface.DEFAULT_BOLD
        p.style = Paint.Style.STROKE
        p.strokeWidth = 10f
        p.color = Color.BLACK
        c.drawText(line.third, w / 2f, h - 50f, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawText(line.third, w / 2f, h - 50f, p)
    }

    private fun fade(c: Canvas, w: Float, h: Float, t: Float) {
        val d = DURATION_SECONDS.toFloat()
        val a = when {
            t < 0.6f -> 1f - t / 0.6f
            t > d - 0.6f -> (t - (d - 0.6f)) / 0.6f
            else -> 0f
        }
        if (a > 0f) {
            p.color = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0)
            c.drawRect(0f, 0f, w, h, p)
        }
    }
}
