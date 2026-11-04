package com.toon.studio.command.engine

import com.toon.studio.command.plan.*

/** The parts of the app that will carry out a Shared Scene Plan. */
enum class EngineArea(val label: String) {
    CHARACTER_ANIMATION("Character Animation System"),
    CAMERA("Camera System"),
    ENVIRONMENT("Environment System"),
    AUDIO("Audio System"),
    SCENE_RENDERER("Animation Renderer (2D/3D)"),
    VIDEO_EXPORT("Video Renderer")
}

/** What an engine answers after being asked to do something. */
sealed class EngineResult {
    /** The engine really performed the work. */
    object Done : EngineResult()

    /** The engine exists but cannot do this particular thing yet. */
    data class Unsupported(val reason: String) : EngineResult()

    /** The engine tried and failed. */
    data class Failed(val reason: String) : EngineResult()
}

interface AnimationEngine {
    val name: String

    /** Must stay false until the engine really performs its work. */
    val isImplemented: Boolean
}

interface CharacterAnimationSystem : AnimationEngine {
    fun applyAction(sceneId: String, action: ActionPlan): EngineResult
    fun moveCharacter(sceneId: String, move: MovementPlan): EngineResult
    fun applyExpression(sceneId: String, expression: ExpressionPlan): EngineResult
    fun applyBodyLanguage(sceneId: String, body: BodyLanguagePlan): EngineResult
}

interface CameraSystem : AnimationEngine {
    fun applyCamera(sceneId: String, camera: CameraPlan): EngineResult
}

interface EnvironmentSystem : AnimationEngine {
    fun loadLocation(location: LocationDef): EngineResult
    fun applyLighting(sceneId: String, lighting: LightingPlan): EngineResult
    fun applyEffect(sceneId: String, effect: EffectPlan): EngineResult
}

interface AudioSystem : AnimationEngine {
    fun speak(sceneId: String, dialogue: DialoguePlan): EngineResult
    fun play(sceneId: String, audio: AudioPlan): EngineResult
}

/** A 2D or 3D renderer. [kind] says which projects it can draw. */
interface SceneRenderer : AnimationEngine {
    val kind: ProjectKind
    fun prepare(plan: SharedScenePlan): EngineResult
    fun renderScene(scene: ScenePlan): EngineResult
}

interface VideoRenderer : AnimationEngine {
    fun export(plan: SharedScenePlan): EngineResult
}

/** The engines currently available. A null engine means "not built yet". */
data class EngineSet(
    val characters: CharacterAnimationSystem? = null,
    val camera: CameraSystem? = null,
    val environment: EnvironmentSystem? = null,
    val audio: AudioSystem? = null,
    val renderer2d: SceneRenderer? = null,
    val renderer3d: SceneRenderer? = null,
    val video: VideoRenderer? = null
)

data class AreaReport(
    val area: EngineArea,
    val total: Int,
    val executed: Int,
    val notImplemented: Int,
    val failed: Int,
    val messages: List<String>
)

/** Honest result of running a plan: what was done and what still needs an engine. */
data class ExecutionReport(val areas: List<AreaReport>) {
    val executedCount: Int get() = areas.fold(0) { acc, a -> acc + a.executed }
    val notImplementedCount: Int get() = areas.fold(0) { acc, a -> acc + a.notImplemented }
    val failedCount: Int get() = areas.fold(0) { acc, a -> acc + a.failed }

    /** True only when there was work and all of it was really executed. */
    val allExecuted: Boolean
        get() = areas.any { it.total > 0 } && notImplementedCount == 0 && failedCount == 0

    fun summaryLines(): List<String> {
        val lines = ArrayList<String>()
        for (a in areas) {
            if (a.total == 0) continue
            var line = "${a.area.label}: ${a.executed}/${a.total} zimetekelezwa"
            if (a.notImplemented > 0) line += " · ${a.notImplemented} bado hazijatekelezwa (engine haipo au haiwezi)"
            if (a.failed > 0) line += " · ${a.failed} zimeshindwa"
            lines.add(line)
            for (m in a.messages) lines.add("  - $m")
        }
        if (lines.isEmpty()) lines.add("Hakuna cha kutekeleza.")
        return lines
    }
}

/**
 * Sends the commands in a [SharedScenePlan] to the engines.
 * An engine that is null or reports isImplemented = false counts as
 * "not implemented"; nothing is faked and no success is reported for it.
 * Engines are called on the caller's thread; call this from a background thread
 * if an engine does heavy work.
 */
object SceneExecutor {

    private class Tally {
        var total = 0
        var executed = 0
        var missing = 0
        var failed = 0
        val messages = ArrayList<String>()

        fun note(text: String) {
            if (messages.size < 5) messages.add(text)
        }
    }

    private fun <E : AnimationEngine> call(
        engine: E?,
        tally: Tally,
        what: String,
        block: (E) -> EngineResult
    ) {
        tally.total++
        if (engine == null || !engine.isImplemented) {
            tally.missing++
            return
        }
        val result: EngineResult = try {
            block(engine)
        } catch (e: Exception) {
            EngineResult.Failed(e.message ?: "hitilafu isiyojulikana")
        }
        when (result) {
            is EngineResult.Done -> tally.executed++
            is EngineResult.Unsupported -> {
                tally.missing++
                tally.note("$what: ${result.reason}")
            }
            is EngineResult.Failed -> {
                tally.failed++
                tally.note("$what: ${result.reason}")
            }
        }
    }

    private fun report(area: EngineArea, t: Tally): AreaReport =
        AreaReport(area, t.total, t.executed, t.missing, t.failed, t.messages)

    fun run(plan: SharedScenePlan, engines: EngineSet): ExecutionReport {
        val chars = Tally()
        val cam = Tally()
        val env = Tally()
        val aud = Tally()
        val rend = Tally()
        val vid = Tally()

        val renderer: SceneRenderer? =
            if (plan.project.kind == ProjectKind.ANIMATION_3D) engines.renderer3d else engines.renderer2d

        call(renderer, rend, "prepare") { it.prepare(plan) }
        for (loc in plan.locations) {
            call(engines.environment, env, "location ${loc.id}") { it.loadLocation(loc) }
        }

        for (scene in plan.scenes) {
            val sid = scene.id
            val moveLines = HashSet<Int>()
            for (m in scene.movements) moveLines.add(m.line)

            for (m in scene.movements) {
                call(engines.characters, chars, "${m.type} (mstari ${m.line})") { it.moveCharacter(sid, m) }
            }
            for (a in scene.actions) {
                if (moveLines.contains(a.line)) continue
                call(engines.characters, chars, "${a.type} (mstari ${a.line})") { it.applyAction(sid, a) }
            }
            for (e in scene.expressions) {
                call(engines.characters, chars, "${e.type} (mstari ${e.line})") { it.applyExpression(sid, e) }
            }
            for (b in scene.bodyLanguage) {
                call(engines.characters, chars, "${b.type} (mstari ${b.line})") { it.applyBodyLanguage(sid, b) }
            }
            for (c in scene.cameras) {
                call(engines.camera, cam, "${c.type} (mstari ${c.line})") { it.applyCamera(sid, c) }
            }
            for (l in scene.lighting) {
                call(engines.environment, env, "lighting (mstari ${l.line})") { it.applyLighting(sid, l) }
            }
            for (f in scene.effects) {
                call(engines.environment, env, "${f.kind} (mstari ${f.line})") { it.applyEffect(sid, f) }
            }
            for (d in scene.dialogue) {
                call(engines.audio, aud, "dialogue (mstari ${d.line})") { it.speak(sid, d) }
            }
            for (a in scene.audio) {
                call(engines.audio, aud, "${a.kind.name} (mstari ${a.line})") { it.play(sid, a) }
            }
            call(renderer, rend, "scene $sid") { it.renderScene(scene) }
        }

        if (plan.scenes.isNotEmpty()) {
            call(engines.video, vid, "export") { it.export(plan) }
        }

        return ExecutionReport(
            listOf(
                report(EngineArea.CHARACTER_ANIMATION, chars),
                report(EngineArea.CAMERA, cam),
                report(EngineArea.ENVIRONMENT, env),
                report(EngineArea.AUDIO, aud),
                report(EngineArea.SCENE_RENDERER, rend),
                report(EngineArea.VIDEO_EXPORT, vid)
            )
        )
    }
}
