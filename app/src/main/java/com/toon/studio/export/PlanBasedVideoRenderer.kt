
package com.toon.studio.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.toon.studio.command.plan.ActionPlan
import com.toon.studio.command.plan.CharacterDef
import com.toon.studio.command.plan.DialoguePlan
import com.toon.studio.command.plan.ProjectKind
import com.toon.studio.command.plan.ScenePlan
import com.toon.studio.command.plan.SharedScenePlan
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Plan-based 2D frame renderer.
 *
 * Renders frames from the real SharedScenePlan. It does not use the
 * hardcoded SampleVideoRenderer demo.
 *
 * IMPORTANT:
 * - This is a 2D renderer, not a 3D engine.
 * - It draws procedural illustrative characters. It does not yet load
 *   custom character models, rigged artwork, or user reference images.
 * - Audio is not encoded here.
 * - Mp4Exporter must be adapted to call draw(plan, ...) before this
 *   renderer can produce a complete MP4 through the app UI.
 */
object PlanBasedVideoRenderer {

    data class RenderSize(
        val width: Int,
        val height: Int
    )

    data class RenderResult(
        val success: Boolean,
        val message: String,
        val durationSeconds: Double,
        val frameRate: Int,
        val size: RenderSize
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val skyTop = Color.rgb(92, 174, 226)
    private val skyBottom = Color.rgb(207, 232, 241)
    private val grassColor = Color.rgb(75, 151, 76)
    private val groundColor = Color.rgb(105, 151, 76)
    private val treeColor = Color.rgb(45, 119, 59)
    private val trunkColor = Color.rgb(105, 73, 47)

    /**
     * Returns a validated output size based on the project's requested format.
     * The caller may supply explicit dimensions to override the defaults.
     */
    fun recommendedSize(
        plan: SharedScenePlan,
        width: Int? = null,
        height: Int? = null
    ): RenderSize {
        if (width != null && height != null && width > 0 && height > 0) {
            return RenderSize(width, height)
        }

        val format = plan.project.format.trim()
        return when {
            format == "9:16" -> RenderSize(720, 1280)
            format == "1:1" -> RenderSize(1080, 1080)
            else -> RenderSize(1280, 720)
        }
    }

    /**
     * Total duration in seconds.
     *
     * Uses timeline duration when available; otherwise derives duration
     * from scene offsets and scene durations.
     */
    fun durationSeconds(plan: SharedScenePlan): Double {
        val declared = plan.timeline.totalDurationSec
        if (declared.isFinite() && declared > 0.0) return declared

        return plan.scenes.maxOfOrNull {
            it.startOffsetSec + it.durationSec
        }?.coerceAtLeast(0.0) ?: 0.0
    }

    /**
     * Draws one frame at project time [timeSeconds].
     *
     * The time is measured from the beginning of the complete project.
     * Returns false when the plan cannot be rendered by this 2D renderer.
     */
    fun draw(
        canvas: Canvas,
        plan: SharedScenePlan,
        timeSeconds: Float,
        width: Float,
        height: Float
    ): Boolean {
        if (width <= 0f || height <= 0f) return false
        if (plan.project.kind == ProjectKind.ANIMATION_3D) return false
        if (plan.scenes.isEmpty()) return false

        val time = timeSeconds.coerceAtLeast(0f).toDouble()
        val scene = findScene(plan, time) ?: return false
        val localTime = (time - scene.startOffsetSec).coerceAtLeast(0.0)
        val sceneDuration = scene.durationSec.coerceAtLeast(0.01)

        canvas.save()
        canvas.drawColor(Color.BLACK)

        val camera = activeCamera(scene, localTime)
        val zoom = cameraZoom(camera)
        val pan = cameraPan(camera, width, height, localTime)

        canvas.save()
        canvas.translate(width / 2f + pan.first, height / 2f + pan.second)
        canvas.scale(zoom, zoom)
        canvas.translate(-width / 2f, -height / 2f)

        drawBackground(
            canvas = canvas,
            scene = scene,
            plan = plan,
            width = width,
            height = height,
            localTime = localTime
        )

        drawEnvironmentDetails(
            canvas,
            plan,
            scene,
            width,
            height,
            localTime
        )

        val characters = charactersForScene(plan, scene)
        val positions = characterPositions(characters.size, width, height)

        characters.forEachIndexed { index, character ->
            val position = positions[index]
            val action = activeAction(scene, character.id, localTime)
            val expression = activeExpression(scene, character.id, localTime)
            val body = activeBodyLanguage(scene, character.id, localTime)
            val speaking = activeDialogue(scene, character.id, localTime) != null

            val movement = movementOffset(
                action = action,
                localTime = localTime,
                sceneDuration = sceneDuration,
                width = width
            )

            drawCharacter(
                canvas = canvas,
                character = character,
                centerX = position.first + movement.first,
                groundY = position.second + movement.second,
                time = localTime,
                speaking = speaking,
                expression = expression,
                bodyLanguage = body,
                action = action,
                scale = characterScale(height, characters.size)
            )
        }

        canvas.restore()

        drawShotTitle(canvas, scene, width, height)
        drawDialogueSubtitle(canvas, scene, plan, localTime, width, height)
        drawTransition(canvas, scene, localTime, width, height)
        drawSceneFade(canvas, localTime, sceneDuration, width, height)

        canvas.restore()
        return true
    }

    private fun findScene(
        plan: SharedScenePlan,
        time: Double
    ): ScenePlan? {
        return plan.scenes.firstOrNull {
            time >= it.startOffsetSec &&
                time < it.startOffsetSec + it.durationSec
        } ?: plan.scenes.lastOrNull {
            time >= it.startOffsetSec
        }?.takeIf {
            it.durationSec <= 0.0 ||
                time <= it.startOffsetSec + it.durationSec
        }
    }

    private fun charactersForScene(
        plan: SharedScenePlan,
        scene: ScenePlan
    ): List<CharacterDef> {
        val ids = scene.characterIds
            .filter { it.isNotBlank() }
            .distinct()

        if (ids.isEmpty()) return emptyList()

        return ids.mapNotNull { id ->
            plan.characterById(id)
        }
    }

    private fun characterPositions(
        count: Int,
        width: Float,
        height: Float
    ): List<Pair<Float, Float>> {
        if (count <= 0) return emptyList()

        val ground = height * 0.84f
        val spacing = width / (count + 1)

        return (0 until count).map { index ->
            Pair(spacing * (index + 1), ground)
        }
    }

    private fun characterScale(
        height: Float,
        count: Int
    ): Float {
        val base = height / 720f
        val crowdFactor = if (count > 2) 0.72f else 1f
        return (base * crowdFactor).coerceIn(0.45f, 2.2f)
    }

    private fun drawBackground(
        canvas: Canvas,
        scene: ScenePlan,
        plan: SharedScenePlan,
        width: Float,
        height: Float,
        localTime: Double
    ) {
        val location = plan.locations.firstOrNull {
            it.id.equals(scene.locationId, ignoreCase = true)
        }

        val locationText = (
            scene.locationId + " " +
                location?.settings.toString() + " " +
                scene.description
            ).uppercase()

        val isNight = scene.timeOfDay.contains("NIGHT", true) ||
            locationText.contains("NIGHT")

        val top = if (isNight) {
            Color.rgb(12, 25, 57)
        } else {
            skyTop
        }

        val bottom = if (isNight) {
            Color.rgb(48, 65, 94)
        } else {
            skyBottom
        }

        paint.shader = LinearGradient(
            0f, 0f, 0f, height * 0.72f,
            top, bottom, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null

        if (isNight) {
            drawStars(canvas, width, height, localTime)
        } else {
            paint.color = Color.rgb(255, 218, 103)
            val pulse = 3f * sin(localTime * 1.8).toFloat()
            canvas.drawCircle(
                width * 0.83f,
                height * 0.15f,
                height * 0.055f + pulse,
                paint
            )
        }

        val water = locationText.contains("LAKE") ||
            locationText.contains("WATER") ||
            locationText.contains("RIVER")

        if (water) {
            paint.color = Color.rgb(67, 151, 188)
            canvas.drawRect(
                0f, height * 0.57f,
                width, height * 0.88f, paint
            )
            drawWaterLines(canvas, width, height, localTime)
        } else {
            paint.color = Color.rgb(118, 177, 104)
            canvas.drawOval(
                RectF(
                    -width * 0.2f,
                    height * 0.48f,
                    width * 0.65f,
                    height * 0.83f
                ),
                paint
            )
            canvas.drawOval(
                RectF(
                    width * 0.35f,
                    height * 0.50f,
                    width * 1.2f,
                    height * 0.84f
                ),
                paint
            )
            paint.color = grassColor
            canvas.drawRect(
                0f, height * 0.69f,
                width, height, paint
            )
        }

        if (locationText.contains("FOREST") ||
            locationText.contains("WOOD")) {
            drawForest(canvas, width, height, localTime)
        } else if (locationText.contains("HOME") ||
            locationText.contains("HOUSE")) {
            drawHouse(canvas, width, height)
        } else {
            drawDistantTrees(canvas, width, height, localTime)
        }

        paint.color = groundColor
        canvas.drawRect(
            0f, height * 0.87f,
            width, height, paint
        )
    }

    private fun drawForest(
        canvas: Canvas,
        width: Float,
        height: Float,
        time: Double
    ) {
        for (i in 0 until 8) {
            val x = (i * width / 7f) - width * 0.06f
            val sway = sin(time * 1.2 + i) * 4f
            drawTree(
                canvas,
                x + sway.toFloat(),
                height * (0.72f - (i % 3) * 0.035f),
                height * (0.19f + (i % 3) * 0.025f)
            )
        }
    }

    private fun drawDistantTrees(
        canvas: Canvas,
        width: Float,
        height: Float,
        time: Double
    ) {
        for (i in 0 until 5) {
            val x = width * (i + 0.5f) / 5f
            val sway = sin(time + i * 0.8) * 3f
            drawTree(
                canvas,
                x + sway.toFloat(),
                height * 0.73f,
                height * 0.17f
            )
        }
    }

    private fun drawTree(
        canvas: Canvas,
        x: Float,
        baseY: Float,
        size: Float
    ) {
        paint.color = trunkColor
        canvas.drawRect(
            x - size * 0.075f,
            baseY - size * 0.65f,
            x + size * 0.075f,
            baseY,
            paint
        )

        paint.color = treeColor
        canvas.drawCircle(x, baseY - size * 0.72f, size * 0.35f, paint)
        canvas.drawCircle(
            x - size * 0.23f,
            baseY - size * 0.56f,
            size * 0.27f,
            paint
        )
        canvas.drawCircle(
            x + size * 0.23f,
            baseY - size * 0.56f,
            size * 0.27f,
            paint
        )
    }

    private fun drawHouse(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        val left = width * 0.58f
        val top = height * 0.40f
        val right = width * 0.91f
        val bottom = height * 0.72f

        paint.color = Color.rgb(224, 190, 139)
        canvas.drawRect(left, top, right, bottom, paint)

        paint.color = Color.rgb(130, 70, 55)
        val roof = Path().apply {
            moveTo(left - width * 0.04f, top)
            lineTo((left + right) / 2f, height * 0.25f)
            lineTo(right + width * 0.04f, top)
            close()
        }
        canvas.drawPath(roof, paint)

        paint.color = Color.rgb(104, 69, 43)
        canvas.drawRect(
            width * 0.70f,
            height * 0.55f,
            width * 0.79f,
            bottom,
            paint
        )
    }

    private fun drawStars(
        canvas: Canvas,
        width: Float,
        height: Float,
        time: Double
    ) {
        paint.color = Color.WHITE

        for (i in 0 until 35) {
            val x = ((i * 73) % 997) / 997f * width
            val y = ((i * 41) % 421) / 421f * height * 0.48f
            val twinkle = 1.2f + abs(sin(time * 2.0 + i)).toFloat() * 1.5f
            canvas.drawCircle(x, y, twinkle, paint)
        }
    }

    private fun drawWaterLines(
        canvas: Canvas,
        width: Float,
        height: Float,
        time: Double
    ) {
        paint.color = Color.argb(90, 255, 255, 255)
        paint.strokeWidth = max(1f, height * 0.002f)

        for (i in 0 until 12) {
            val y = height * (0.61f + i * 0.018f)
            val x = ((time * 20 + i * 97) % (width + 120)).toFloat()
            canvas.drawLine(x, y, x + width * 0.12f, y, paint)
        }
    }

    private fun drawEnvironmentDetails(
        canvas: Canvas,
        plan: SharedScenePlan,
        scene: ScenePlan,
        width: Float,
        height: Float,
        localTime: Double
    ) {
        val location = plan.locations.firstOrNull {
            it.id.equals(scene.locationId, true)
        }

        val settings = location?.settings?.toString()?.uppercase() ?: ""
        val effects = scene.effects.joinToString(" ") {
            it.kind + " " + it.type + " " + it.params.toString()
        }.uppercase()

        val activeEnvironment = settings + " " + effects

        if (activeEnvironment.contains("WIND") ||
            activeEnvironment.contains("BREEZE")) {
            paint.color = Color.argb(100, 245, 255, 220)
            paint.strokeWidth = max(1f, height * 0.002f)

            for (i in 0 until 7) {
                val y = height * (0.23f + i * 0.055f)
                val x = ((localTime * 65 + i * 140) % (width + 100)).toFloat()
                canvas.drawLine(x, y, x + width * 0.08f, y - 3f, paint)
            }
        }

        if (activeEnvironment.contains("RAIN")) {
            paint.color = Color.argb(130, 210, 230, 255)
            paint.strokeWidth = max(1f, height * 0.0025f)

            for (i in 0 until 55) {
                val x = ((i * 83 + localTime * 180) % width).toFloat()
                val y = ((i * 47 + localTime * 330) % height).toFloat()
                canvas.drawLine(x, y, x - 7f, y + height * 0.025f, paint)
            }
        }

        if (activeEnvironment.contains("BIRDS")) {
            for (i in 0 until 3) {
                val x = ((localTime * 38 + i * width * 0.35) % (width + 80)).toFloat()
                val y = height * (0.19f + i * 0.06f)
                drawBird(canvas, x, y, localTime + i)
            }
        }

        if (activeEnvironment.contains("BUTTERFLY")) {
            for (i in 0 until 4) {
                val x = (width * (0.15f + i * 0.2f) +
                    sin(localTime * 2 + i) * 25).toFloat()
                val y = (height * 0.55f +
                    sin(localTime * 3 + i) * 20).toFloat()
                drawButterfly(canvas, x, y, localTime)
            }
        }

        scene.lighting.lastOrNull {
            localTime >= it.startSec &&
                (it.durationSec == null ||
                    localTime <= it.startSec + it.durationSec)
        }?.let { light ->
            val color = parseColor(light.color)
            val alpha = ((light.intensity ?: 0.12).coerceIn(0.0, 0.5) * 255)
                .toInt()
            paint.color = Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }

    private fun drawBird(
        canvas: Canvas,
        x: Float,
        y: Float,
        time: Double
    ) {
        val flap = abs(sin(time * 8)).toFloat() * 7f
        paint.color = Color.rgb(48, 55, 59)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawArc(
            RectF(x - 12f, y - flap, x, y + 8f),
            200f, 140f, false, paint
        )
        canvas.drawArc(
            RectF(x, y - flap, x + 12f, y + 8f),
            200f, 140f, false, paint
        )
        paint.style = Paint.Style.FILL
    }

    private fun drawButterfly(
        canvas: Canvas,
        x: Float,
        y: Float,
        time: Double
    ) {
        val wing = 4f + abs(sin(time * 13)).toFloat() * 5f
        paint.color = Color.rgb(236, 151, 65)
        canvas.drawOval(
            RectF(x - wing - 5f, y - 6f, x - 2f, y + 6f),
            paint
        )
        canvas.drawOval(
            RectF(x + 2f, y - 6f, x + wing + 5f, y + 6f),
            paint
        )
        paint.color = Color.rgb(70, 53, 44)
        canvas.drawRect(x - 1f, y - 6f, x + 1f, y + 6f, paint)
    }

    private fun drawCharacter(
        canvas: Canvas,
        character: CharacterDef,
        centerX: Float,
        groundY: Float,
        time: Double,
        speaking: Boolean,
        expression: String,
        bodyLanguage: String,
        action: ActionPlan?,
        scale: Float
    ) {
        val identity = (
            character.species + " " +
                character.name + " " +
                character.appearance + " " +
                character.description
            ).lowercase()

        canvas.save()
        canvas.translate(centerX, groundY)
        canvas.scale(scale, scale)

        val bob = sin(time * 5.5).toFloat() * 3f
        canvas.translate(0f, bob)

        drawCharacterShadow(canvas)

        when {
            identity.contains("rabbit") ||
                identity.contains("sungura") -> drawRabbit(
                    canvas, time, speaking, expression
                )

            identity.contains("bird") ||
                identity.contains("ndege") -> drawAnimalBody(
                    canvas, time, speaking, expression, true
                )

            identity.contains("monkey") ||
                identity.contains("tumbili") -> drawMonkey(
                    canvas, time, speaking, expression
                )

            identity.contains("human") ||
                identity.contains("person") ||
                identity.contains("child") ||
                identity.contains("mtoto") ||
                identity.contains("boy") ||
                identity.contains("girl") -> drawHuman(
                    canvas, time, speaking, expression, bodyLanguage
                )

            else -> drawAnimalBody(
                canvas, time, speaking, expression, false
            )
        }

        if (action != null) {
            drawActionAccent(canvas, action.type, time)
        }

        canvas.restore()
    }

    private fun drawCharacterShadow(canvas: Canv
