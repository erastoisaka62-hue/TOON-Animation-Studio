package com.toon.studio.camera.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Math primitives of the Camera Engine. Pure Kotlin, no Android dependencies,
 * and fully deterministic: the same inputs always give the same outputs.
 *
 * World convention (left-handed, like Unity):
 *   X = right, Y = up, Z = forward.
 *   yaw   = rotation around Y in degrees; positive turns to the right (towards +X).
 *   pitch = degrees above the horizon; positive looks up.
 *   roll  = degrees around the viewing direction.
 * A renderer that uses another convention (for example OpenGL, which looks along -Z)
 * converts at its own boundary; the camera engine itself never changes convention.
 */
object CamMath {
    const val DEG_TO_RAD: Double = PI / 180.0
    const val RAD_TO_DEG: Double = 180.0 / PI
    const val EPSILON: Double = 1e-9

    fun clamp(v: Double, lo: Double, hi: Double): Double =
        if (v < lo) lo else if (v > hi) hi else v

    fun clamp01(v: Double): Double = clamp(v, 0.0, 1.0)

    fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    /** Wraps an angle in degrees into the range (-180, 180]. */
    fun wrap180(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180.0) d -= 360.0 else if (d <= -180.0) d += 360.0
        return d
    }

    /** Interpolates angles along the shortest way round, so 350 to 10 goes through 0. */
    fun lerpAngle(a: Double, b: Double, t: Double): Double = a + wrap180(b - a) * t

    fun approx(a: Double, b: Double, eps: Double = 1e-6): Boolean = abs(a - b) <= eps
}

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2): Vec2 = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2): Vec2 = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double): Vec2 = Vec2(x * s, y * s)

    val length: Double get() = sqrt(x * x + y * y)

    fun distanceTo(o: Vec2): Double = (this - o).length

    fun lerp(o: Vec2, t: Double): Vec2 =
        Vec2(CamMath.lerp(x, o.x, t), CamMath.lerp(y, o.y, t))

    companion object {
        val ZERO = Vec2(0.0, 0.0)
    }
}

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3): Vec3 = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3): Vec3 = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double): Vec3 = Vec3(x * s, y * s, z * s)
    operator fun div(s: Double): Vec3 = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 =
        Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    val lengthSquared: Double get() = dot(this)

    val length: Double get() = sqrt(lengthSquared)

    /** Unit vector, or ZERO when the length is practically zero (never NaN). */
    fun normalized(): Vec3 {
        val l = length
        return if (l < CamMath.EPSILON) ZERO else this / l
    }

    fun distanceTo(o: Vec3): Double = (this - o).length

    /** Distance ignoring height (X and Z only). */
    fun horizontalDistanceTo(o: Vec3): Double {
        val dx = x - o.x
        val dz = z - o.z
        return sqrt(dx * dx + dz * dz)
    }

    fun lerp(o: Vec3, t: Double): Vec3 =
        Vec3(CamMath.lerp(x, o.x, t), CamMath.lerp(y, o.y, t), CamMath.lerp(z, o.z, t))

    fun withY(newY: Double): Vec3 = Vec3(x, newY, z)

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UP = Vec3(0.0, 1.0, 0.0)
        val RIGHT = Vec3(1.0, 0.0, 0.0)
        val FORWARD = Vec3(0.0, 0.0, 1.0)
    }
}

/** Camera orientation in degrees. See [CamMath] for the conventions. */
data class Rotation(
    val yaw: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0
) {
    /** Unit vector the camera looks along. */
    fun forward(): Vec3 {
        val y = yaw * CamMath.DEG_TO_RAD
        val p = pitch * CamMath.DEG_TO_RAD
        return Vec3(sin(y) * cos(p), sin(p), cos(y) * cos(p))
    }

    private fun levelRight(): Vec3 {
        val y = yaw * CamMath.DEG_TO_RAD
        return Vec3(cos(y), 0.0, -sin(y))
    }

    /** Unit vector pointing to the right of the camera image (roll applied). */
    fun right(): Vec3 {
        val r = levelRight()
        val u = forward().cross(r)
        val a = roll * CamMath.DEG_TO_RAD
        return r * cos(a) + u * sin(a)
    }

    /** Unit vector pointing to the top of the camera image (roll applied). */
    fun up(): Vec3 {
        val r = levelRight()
        val u = forward().cross(r)
        val a = roll * CamMath.DEG_TO_RAD
        return u * cos(a) - r * sin(a)
    }

    /** Blends two orientations; yaw and roll take the shortest way round. */
    fun lerp(o: Rotation, t: Double): Rotation = Rotation(
        CamMath.lerpAngle(yaw, o.yaw, t),
        CamMath.lerp(pitch, o.pitch, t),
        CamMath.lerpAngle(roll, o.roll, t)
    )

    companion object {
        val ZERO = Rotation(0.0, 0.0, 0.0)

        /** Orientation that looks along [dir]. A zero vector gives ZERO. */
        fun fromDirection(dir: Vec3, roll: Double = 0.0): Rotation {
            val d = dir.normalized()
            if (d.lengthSquared < CamMath.EPSILON) return Rotation(0.0, 0.0, roll)
            val yaw = atan2(d.x, d.z) * CamMath.RAD_TO_DEG
            val pitch = asin(CamMath.clamp(d.y, -1.0, 1.0)) * CamMath.RAD_TO_DEG
            return Rotation(yaw, pitch, roll)
        }

        /** Orientation of a camera at [from] that looks at [to]. */
        fun lookAt(from: Vec3, to: Vec3, roll: Double = 0.0): Rotation =
            fromDirection(to - from, roll)
    }
}

/** How progress from 0 to 1 is shaped over time. */
enum class EasingType {
    LINEAR,
    SMOOTH,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    companion object {
        /** Reads a name such as "ease-in-out" or "SMOOTH". Returns null when unknown. */
        fun fromName(name: String?): EasingType? {
            val wanted = name?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_') ?: return null
            return values().firstOrNull { it.name == wanted }
        }
    }
}

object Easing {
    /** Maps progress [t] (clamped to 0..1) to eased progress in 0..1. Always 0 at 0 and 1 at 1. */
    fun apply(type: EasingType, t: Double): Double {
        val x = CamMath.clamp01(t)
        return when (type) {
            EasingType.LINEAR -> x
            EasingType.SMOOTH -> x * x * (3.0 - 2.0 * x)
            EasingType.EASE_IN -> x * x * x
            EasingType.EASE_OUT -> {
                val k = 1.0 - x
                1.0 - k * k * k
            }
            EasingType.EASE_IN_OUT -> {
                if (x < 0.5) {
                    4.0 * x * x * x
                } else {
                    val k = -2.0 * x + 2.0
                    1.0 - k * k * k / 2.0
                }
            }
        }
    }
}
