package com.toon.studio.command.plan

/**
 * Shared Scene Plan: the structured data that Command Mode produces and that
 * the 2D / 3D animation engines (and later Manual Mode) will consume.
 * Pure Kotlin, no Android dependencies. All times are in seconds.
 * "start" values inside a scene are relative to the start of that scene.
 */

enum class ProjectKind {
    ANIMATION_2D,
    ANIMATION_3D;

    companion object {
        fun fromText(text: String?): ProjectKind? = when (text?.trim()?.uppercase()) {
            "2D" -> ANIMATION_2D
            "3D" -> ANIMATION_3D
            else -> null
        }
    }
}

data class ProjectInfo(
    val name: String = "",
    val kind: ProjectKind = ProjectKind.ANIMATION_2D,
    val format: String = "16:9",
    val language: String = "KISWAHILI",
    val fps: Int = 30,
    val description: String = ""
)

data class StoryInfo(
    val title: String = "",
    val text: String = "",
    val language: String = ""
)

data class CharacterDef(
    val id: String,
    val name: String = "",
    val appearance: String = "",
    val description: String = "",
    val outfit: String = "",
    val species: String = "",
    val voice: String = ""
) {
    /** True when the character should come from the app's Character Library. */
    val usesExistingCharacter: Boolean
        get() = appearance.trim().uppercase() == "EXISTING_CHARACTER"
}

/** A place declared by ENVIRONMENT. [settings] holds every other field, e.g. WIND = ACTIVE. */
data class LocationDef(
    val id: String,
    val settings: Map<String, String> = emptyMap()
)

data class VoiceDef(
    val characterId: String,
    val type: String = "",
    val language: String = "",
    val style: String = "",
    val pitch: String = "",
    val speed: String = "",
    val voiceId: String = ""
)

data class ShotPlan(
    val id: String,
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val description: String = "",
    val line: Int = 0
)

data class ActionPlan(
    val characterId: String,
    val type: String,
    val motionStyle: String = "",
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val target: String = "",
    val direction: String = "",
    val line: Int = 0
)

/** An action that moves a character through space (walk, run, enter...). */
data class MovementPlan(
    val characterId: String,
    val type: String,
    val motionStyle: String = "",
    val target: String = "",
    val direction: String = "",
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val line: Int = 0
)

data class ExpressionPlan(
    val characterId: String,
    val type: String,
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val intensity: Double? = null,
    val line: Int = 0
)

data class BodyLanguagePlan(
    val characterId: String,
    val type: String,
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val line: Int = 0
)

data class DialoguePlan(
    val characterId: String,
    val text: String,
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val emotion: String = "",
    val voice: String = "",
    val line: Int = 0
)

data class CameraPlan(
    val type: String,
    val movement: String = "",
    val target: String = "",
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val speed: String = "",
    val fov: Double? = null,
    val line: Int = 0
)

data class LightingPlan(
    val type: String,
    val intensity: Double? = null,
    val color: String = "",
    val timeOfDay: String = "",
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val line: Int = 0
)

/** VFX, WEATHER and PHYSICS commands: free-form parameters kept in [params]. */
data class EffectPlan(
    val kind: String,
    val type: String = "",
    val params: Map<String, String> = emptyMap(),
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val line: Int = 0
)

enum class AudioKind { SOUND, MUSIC }

data class AudioPlan(
    val kind: AudioKind,
    val type: String = "",
    val file: String = "",
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val volume: Double? = null,
    val loop: Boolean = false,
    val line: Int = 0
)

data class TransitionPlan(
    val type: String,
    val startSec: Double = 0.0,
    val durationSec: Double? = null,
    val from: String = "",
    val to: String = "",
    val line: Int = 0
)

data class ScenePlan(
    val id: String,
    val title: String = "",
    val locationId: String = "",
    val durationSec: Double = 0.0,
    /** Where this scene begins on the whole-project timeline. */
    val startOffsetSec: Double = 0.0,
    val timeOfDay: String = "",
    val description: String = "",
    val shots: List<ShotPlan> = emptyList(),
    val actions: List<ActionPlan> = emptyList(),
    val movements: List<MovementPlan> = emptyList(),
    val expressions: List<ExpressionPlan> = emptyList(),
    val bodyLanguage: List<BodyLanguagePlan> = emptyList(),
    val dialogue: List<DialoguePlan> = emptyList(),
    val cameras: List<CameraPlan> = emptyList(),
    val lighting: List<LightingPlan> = emptyList(),
    val effects: List<EffectPlan> = emptyList(),
    val audio: List<AudioPlan> = emptyList(),
    val transitions: List<TransitionPlan> = emptyList(),
    val line: Int = 0
) {
    /** Every character mentioned in this scene, in order of first appearance. */
    val characterIds: List<String>
        get() {
            val ids = ArrayList<String>()
            for (a in actions) ids.add(a.characterId)
            for (e in expressions) ids.add(e.characterId)
            for (b in bodyLanguage) ids.add(b.characterId)
            for (d in dialogue) ids.add(d.characterId)
            return ids.distinct()
        }
}

data class TimelineInfo(
    val totalDurationSec: Double = 0.0,
    val fps: Int = 30
)

data class RenderSettings(
    val quality: String = "",
    val fps: Int? = null,
    val resolution: String = "",
    val engine: String = ""
)

data class ExportSettings(
    val format: String = "MP4",
    val fileName: String = "",
    val destination: String = ""
)

/** The complete plan for one command file. */
data class SharedScenePlan(
    val project: ProjectInfo = ProjectInfo(),
    val story: StoryInfo? = null,
    val characters: List<CharacterDef> = emptyList(),
    val locations: List<LocationDef> = emptyList(),
    val voices: List<VoiceDef> = emptyList(),
    val scenes: List<ScenePlan> = emptyList(),
    val timeline: TimelineInfo = TimelineInfo(),
    val render: RenderSettings = RenderSettings(),
    val export: ExportSettings = ExportSettings()
) {
    val sceneCount: Int get() = scenes.size
    val characterCount: Int get() = characters.size
    val locationCount: Int get() = locations.size

    val actionCount: Int
        get() = scenes.fold(0) { acc, s -> acc + s.actions.size }

    val dialogueCount: Int
        get() = scenes.fold(0) { acc, s -> acc + s.dialogue.size }

    val cameraCount: Int
        get() = scenes.fold(0) { acc, s -> acc + s.cameras.size }

    /** Distinct action types used, sorted, for the output panel. */
    val actionTypes: List<String>
        get() = scenes.flatMap { s -> s.actions.map { a -> a.type } }.distinct().sorted()

    /** Estimated length of the finished video in seconds. */
    val estimatedDurationSec: Double
        get() = timeline.totalDurationSec

    fun sceneById(id: String): ScenePlan? {
        val wanted = id.trim().uppercase()
        return scenes.firstOrNull { it.id.trim().uppercase() == wanted }
    }

    fun characterById(id: String): CharacterDef? {
        val wanted = id.trim().uppercase()
        return characters.firstOrNull { it.id.trim().uppercase() == wanted }
    }
}
