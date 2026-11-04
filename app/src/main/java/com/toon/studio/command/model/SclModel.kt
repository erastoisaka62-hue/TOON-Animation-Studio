package com.toon.studio.command.model

/** Categories recognised by MRP-SCL (Mr. P Scene Command Language). */
enum class SclCategory {
    PROJECT, STORY, SCENE, SHOT, ENVIRONMENT, CHARACTER, ACTION,
    MOTION_STYLE, BODY_LANGUAGE, EXPRESSION, DIALOGUE, VOICE, CAMERA,
    LIGHTING, WEATHER, PHYSICS, VFX, SOUND, MUSIC, TRANSITION,
    TIMELINE, RENDER, EXPORT;

    companion object {
        /** Case-insensitive lookup. Returns null for unknown names. */
        fun fromName(name: String): SclCategory? {
            val wanted = name.trim().uppercase()
            return values().firstOrNull { it.name == wanted }
        }
    }
}

/** A value written after "KEY:". */
sealed class SclValue {
    /** The value as plain text (no quotes). */
    abstract val raw: String

    /** Quoted text, e.g. "Siri ya Msitu". */
    data class Text(val value: String) : SclValue() {
        override val raw: String get() = value
    }

    /** A number, e.g. 20 or 2.5. [source] keeps the original spelling. */
    data class Num(val value: Double, val source: String) : SclValue() {
        override val raw: String get() = source
    }

    /** An unquoted word, e.g. ACTIVE or true. */
    data class Word(val value: String) : SclValue() {
        override val raw: String get() = value
    }
}

/** One "KEY: value" line. [key] is always upper-case. [line] is 1-based. */
data class SclField(val key: String, val value: SclValue, val line: Int)

/** One command block, e.g. SCENE { ... }. */
data class SclBlock(
    val category: SclCategory,
    val fields: List<SclField>,
    val startLine: Int,
    val endLine: Int
) {
    fun field(key: String): SclField? {
        val wanted = key.trim().uppercase()
        return fields.firstOrNull { it.key == wanted }
    }

    fun has(key: String): Boolean = field(key) != null

    /** Value as plain text, whatever its type. */
    fun text(key: String): String? = field(key)?.value?.raw

    /** Value as a number, or null if missing or not numeric. */
    fun number(key: String): Double? = (field(key)?.value as? SclValue.Num)?.value

    /** Line of a field, or the block's first line when the field is absent. */
    fun lineOf(key: String): Int = field(key)?.line ?: startLine
}

enum class SclSeverity { ERROR, WARNING }

/** A problem found while parsing or validating. [line] is 1-based; 0 = no specific line. */
data class SclDiagnostic(
    val severity: SclSeverity,
    val message: String,
    val line: Int,
    val code: String
) {
    val isError: Boolean get() = severity == SclSeverity.ERROR

    fun display(): String = if (line > 0) "Mstari $line: $message" else message
}

/** Result of reading a command file: blocks plus any diagnostics. */
data class SclDocument(
    val blocks: List<SclBlock>,
    val diagnostics: List<SclDiagnostic>
) {
    val hasErrors: Boolean get() = diagnostics.any { it.isError }
    val errors: List<SclDiagnostic> get() = diagnostics.filter { it.isError }
    val warnings: List<SclDiagnostic> get() = diagnostics.filter { !it.isError }

    fun blocksOf(category: SclCategory): List<SclBlock> =
        blocks.filter { it.category == category }

    companion object {
        val EMPTY = SclDocument(emptyList(), emptyList())
    }
}
