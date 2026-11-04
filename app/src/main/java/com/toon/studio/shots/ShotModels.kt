package com.toon.studio.shots

/** How well the 2D renderer can really show a feature. */
enum class Support2D { REAL, APPROXIMATE, NOT_AVAILABLE }

enum class ShotType(val command: String, val support2D: Support2D) {
    EXTREME_WIDE("EXTREME_WIDE", Support2D.REAL),
    WIDE("WIDE", Support2D.REAL),
    FULL("FULL", Support2D.REAL),
    MEDIUM("MEDIUM", Support2D.REAL),
    MEDIUM_CLOSE_UP("MEDIUM_CLOSE_UP", Support2D.REAL),
    CLOSE_UP("CLOSE_UP", Support2D.REAL),
    EXTREME_CLOSE_UP("EXTREME_CLOSE_UP", Support2D.REAL),
    OVER_THE_SHOULDER("OVER_THE_SHOULDER", Support2D.APPROXIMATE),
    POINT_OF_VIEW("POINT_OF_VIEW", Support2D.NOT_AVAILABLE),
    BIRDS_EYE("BIRDS_EYE", Support2D.APPROXIMATE),
    AERIAL("AERIAL", Support2D.APPROXIMATE),
    LOW_ANGLE("LOW_ANGLE", Support2D.APPROXIMATE),
    HIGH_ANGLE("HIGH_ANGLE", Support2D.APPROXIMATE),
    SIDE("SIDE", Support2D.REAL),
    TRACKING("TRACKING", Support2D.REAL);

    companion object {
        fun fromCommand(text: String): ShotType? {
            val key = text.trim().uppercase().replace(' ', '_').replace('-', '_')
            return values().firstOrNull { it.command == key }
        }
    }
}

enum class CameraMovement(val command: String, val support2D: Support2D) {
    STATIC("STATIC", Support2D.REAL),
    PAN_LEFT("PAN_LEFT", Support2D.REAL),
    PAN_RIGHT("PAN_RIGHT", Support2D.REAL),
    TILT_UP("TILT_UP", Support2D.REAL),
    TILT_DOWN("TILT_DOWN", Support2D.REAL),
    ZOOM_IN("ZOOM_IN", Support2D.REAL),
    ZOOM_OUT("ZOOM_OUT", Support2D.REAL),
    DOLLY_IN("DOLLY_IN", Support2D.REAL),
    DOLLY_OUT("DOLLY_OUT", Support2D.REAL),
    TRACKING("TRACKING", Support2D.REAL),
    FOLLOW("FOLLOW", Support2D.REAL),
    ORBIT("ORBIT", Support2D.NOT_AVAILABLE),
    CRANE_UP("CRANE_UP", Support2D.REAL),
    CRANE_DOWN("CRANE_DOWN", Support2D.REAL),
    AERIAL_DESCEND("AERIAL_DESCEND", Support2D.APPROXIMATE),
    AERIAL_RISE("AERIAL_RISE", Support2D.APPROXIMATE);

    companion object {
        fun fromCommand(text: String): CameraMovement? {
            val key = text.trim().uppercase().replace(' ', '_').replace('-', '_')
            val alias = when (key) {
                "DESCEND" -> "AERIAL_DESCEND"
                "RISE" -> "AERIAL_RISE"
                "PAN" -> "PAN_RIGHT"
                "TILT" -> "TILT_UP"
                else -> key
            }
            return values().firstOrNull { it.command == alias }
        }
    }
}

enum class CameraAngle { EYE_LEVEL, LOW, HIGH, TOP_DOWN }

enum class Transition { CUT, DISSOLVE, FADE_BLACK }

enum class ActionType { IDLE, WALK, RUN, JUMP, LOOK, SURPRISED }

data class EnvironmentVisibility(
    val background: Boolean = true,
    val middleground: Boolean = true,
    val foreground: Boolean = true,
    val wildlife: Boolean = true
)

data class CharacterAction(
    val characterId: String,
    val type: ActionType,
    val start: Float,
    val duration: Float,
    val fromX: Float = 0f,
    val toX: Float = 0f
)

data class DialogueLine(
    val characterId: String,
    val text: String,
    val start: Float,
    val duration: Float
)

data class LocationArea(
    val id: String,
    val label: String,
    val worldX: Float,
    val width: Float
)

data class SceneLocation(
    val id: String,
    val areas: List<LocationArea>,
    val worldWidth: Float
) {
    fun area(areaId: String): LocationArea? = areas.firstOrNull { it.id == areaId }
}

data class Shot(
    val id: String,
    val type: ShotType,
    val start: Float,
    val duration: Float,
    val movement: CameraMovement = CameraMovement.STATIC,
    val targetId: String = "",
    val areaId: String = "",
    val angle: CameraAngle = CameraAngle.EYE_LEVEL,
    val cameraOffsetX: Float = 0f,
    val cameraOffsetY: Float = 0f,
    val environment: EnvironmentVisibility = EnvironmentVisibility(),
    val transition: Transition = Transition.DISSOLVE,
    val transitionDuration: Float = 0.4f
) {
    val end: Float get() = start + duration
}

data class ShotScene(
    val id: String,
    val title: String,
    val location: SceneLocation,
    val shots: List<Shot>,
    val actions: List<CharacterAction> = emptyList(),
    val dialogue: List<DialogueLine> = emptyList()
) {
    val duration: Float get() = shots.maxOfOrNull { it.end } ?: 0f

    /** The shot that is active at time t (seconds). Shots are played in start order. */
    fun shotAt(t: Float): Shot? {
        var found: Shot? = null
        for (s in shots.sortedBy { it.start }) {
            if (s.start <= t) found = s
        }
        return found ?: shots.minByOrNull { it.start }
    }
}

/** Checks a scene and returns readable messages starting with ERROR or WARNING. */
object ShotSceneValidator {
    fun validate(scene: ShotScene): List<String> {
        val out = ArrayList<String>()
        if (scene.shots.isEmpty()) {
            out.add("ERROR: scene has no shots")
            return out
        }
        val ordered = scene.shots.sortedBy { it.start }
        val areaIds = scene.location.areas.map { it.id }

        for (i in ordered.indices) {
            val s = ordered[i]
            if (s.duration <= 0f) {
                out.add("ERROR: " + s.id + ": duration must be greater than 0")
            }
            if (s.areaId.isNotEmpty() && !areaIds.contains(s.areaId)) {
                out.add("ERROR: " + s.id + ": unknown area " + s.areaId)
            }
            if (s.type.support2D == Support2D.NOT_AVAILABLE) {
                out.add("WARNING: " + s.id + ": shot type " + s.type.command + " needs 3D")
            }
            if (s.movement.support2D == Support2D.NOT_AVAILABLE) {
                out.add("WARNING: " + s.id + ": movement " + s.movement.command + " needs 3D")
            }
            if (i > 0) {
                val prev = ordered[i - 1]
                if (s.start < prev.end - 0.001f) {
                    out.add("ERROR: " + s.id + " overlaps " + prev.id)
                } else if (s.start > prev.end + 0.001f) {
                    out.add("WARNING: gap between " + prev.id + " and " + s.id)
                }
                if (prev.areaId.isNotEmpty() && s.areaId.isNotEmpty() &&
                    prev.areaId != s.areaId && s.transition == Transition.CUT
                ) {
                    val a = areaIds.indexOf(prev.areaId)
                    val b = areaIds.indexOf(s.areaId)
                    if (a >= 0 && b >= 0 && Math.abs(a - b) > 1) {
                        out.add(
                            "WARNING: " + s.id + ": jumps from " + prev.areaId +
                                " to " + s.areaId + " without a transition"
                        )
                    }
                }
            }
        }
        return out
    }
}
