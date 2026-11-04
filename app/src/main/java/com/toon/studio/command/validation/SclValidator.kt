package com.toon.studio.command.validation

import com.toon.studio.command.model.SclBlock
import com.toon.studio.command.model.SclCategory
import com.toon.studio.command.model.SclDiagnostic
import com.toon.studio.command.model.SclDocument
import com.toon.studio.command.model.SclSeverity
import com.toon.studio.command.model.SclValue
import com.toon.studio.command.parser.SclParser

/**
 * Lists of values the language knows about. Extend these sets when the
 * animation engines learn new actions, camera moves or expressions.
 */
object SclVocabulary {
    val PROJECT_TYPES: Set<String> = setOf("2D", "3D")
    val FORMATS: Set<String> = setOf("16:9", "9:16", "1:1", "4:3")
    val LANGUAGES: Set<String> = setOf("KISWAHILI", "SWAHILI", "ENGLISH")

    val ACTIONS: Set<String> = setOf(
        "IDLE", "STAND", "SIT", "WALK", "RUN", "JUMP", "TURN", "LOOK_AT", "WAVE", "POINT",
        "NOD", "SHAKE_HEAD", "DANCE", "CLIMB", "FALL", "FLY", "SWIM", "SLEEP", "WAKE_UP",
        "EAT", "DRINK", "PICK_UP", "PUT_DOWN", "THROW", "CATCH", "OPEN", "CLOSE", "HUG",
        "APPROACH", "RETREAT", "FOLLOW", "HIDE", "SNEAK", "LAUGH", "CRY",
        "ENTER", "EXIT", "ENTER_SCENE", "EXIT_SCENE", "ENTER_FOREST", "EXIT_FOREST"
    )

    val CAMERA_TYPES: Set<String> = setOf(
        "WIDE", "MEDIUM", "CLOSE_UP", "EXTREME_CLOSE_UP", "AERIAL", "TRACKING", "STATIC",
        "OVER_SHOULDER", "POV", "TOP_DOWN", "LOW_ANGLE", "HIGH_ANGLE"
    )

    val CAMERA_MOVEMENTS: Set<String> = setOf(
        "STATIC", "PAN_LEFT", "PAN_RIGHT", "TILT_UP", "TILT_DOWN", "ZOOM_IN", "ZOOM_OUT",
        "DOLLY_IN", "DOLLY_OUT", "DESCEND", "ASCEND", "FOLLOW", "ORBIT", "SHAKE"
    )

    val MOTION_STYLES: Set<String> = setOf(
        "NATURAL", "SLOW", "FAST", "SMOOTH", "BOUNCY", "HEAVY", "LIGHT", "SNEAKY",
        "EXAGGERATED", "CARTOONY", "ROBOTIC", "STIFF", "LOOSE"
    )

    val BODY_LANGUAGES: Set<String> = setOf(
        "RELAXED", "TENSE", "CONFIDENT", "SHY", "NERVOUS", "OPEN", "CLOSED",
        "SLOUCHING", "UPRIGHT", "ALERT", "EXCITED", "TIRED"
    )

    val EXPRESSIONS: Set<String> = setOf(
        "NEUTRAL", "HAPPY", "SAD", "ANGRY", "SURPRISED", "CURIOUS", "SCARED", "EXCITED",
        "WORRIED", "THINKING", "LAUGHING", "CRYING", "TIRED", "PROUD", "CONFUSED"
    )

    val TRANSITIONS: Set<String> = setOf(
        "CUT", "FADE", "FADE_IN", "FADE_OUT", "FADE_TO_BLACK", "DISSOLVE", "WIPE", "SLIDE", "ZOOM"
    )

    val EXPORT_FORMATS: Set<String> = setOf("MP4")
}

/**
 * Checks an [SclDocument] and returns it with extra diagnostics added.
 * Never throws. Errors block running; warnings do not.
 */
object SclValidator {

    private class Rule(
        val required: List<String>,
        val optional: List<String> = emptyList(),
        val open: Boolean = false
    )

    private val RULES: Map<SclCategory, Rule> = mapOf(
        SclCategory.PROJECT to Rule(
            listOf("NAME", "TYPE"),
            listOf("FORMAT", "LANGUAGE", "FPS", "QUALITY", "AUTHOR", "DESCRIPTION")
        ),
        SclCategory.STORY to Rule(listOf("TEXT"), listOf("TITLE", "LANGUAGE", "SUMMARY")),
        SclCategory.SCENE to Rule(
            listOf("ID", "LOCATION", "DURATION"),
            listOf("TITLE", "TIME_OF_DAY", "DESCRIPTION")
        ),
        SclCategory.SHOT to Rule(listOf("ID"), listOf("START", "DURATION", "DESCRIPTION")),
        SclCategory.ENVIRONMENT to Rule(listOf("LOCATION"), emptyList(), true),
        SclCategory.CHARACTER to Rule(
            listOf("ID"),
            listOf("NAME", "APPEARANCE", "DESCRIPTION", "OUTFIT", "VOICE", "SPECIES", "AGE")
        ),
        SclCategory.ACTION to Rule(
            listOf("CHARACTER", "TYPE"),
            listOf("MOTION_STYLE", "START", "DURATION", "TARGET", "DIRECTION", "SPEED", "DESCRIPTION")
        ),
        SclCategory.MOTION_STYLE to Rule(listOf("CHARACTER", "TYPE"), listOf("START", "DURATION")),
        SclCategory.BODY_LANGUAGE to Rule(listOf("CHARACTER", "TYPE"), listOf("START", "DURATION")),
        SclCategory.EXPRESSION to Rule(
            listOf("CHARACTER", "TYPE"),
            listOf("START", "DURATION", "INTENSITY")
        ),
        SclCategory.DIALOGUE to Rule(
            listOf("CHARACTER", "TEXT"),
            listOf("START", "DURATION", "EMOTION", "VOICE")
        ),
        SclCategory.VOICE to Rule(
            listOf("CHARACTER"),
            listOf("TYPE", "LANGUAGE", "PITCH", "SPEED", "VOICE_ID", "STYLE")
        ),
        SclCategory.CAMERA to Rule(
            listOf("TYPE"),
            listOf("MOVEMENT", "TARGET", "START", "DURATION", "SPEED", "FOV")
        ),
        SclCategory.LIGHTING to Rule(
            listOf("TYPE"),
            listOf("INTENSITY", "COLOR", "TIME_OF_DAY", "START", "DURATION")
        ),
        SclCategory.WEATHER to Rule(listOf("TYPE"), emptyList(), true),
        SclCategory.PHYSICS to Rule(emptyList(), emptyList(), true),
        SclCategory.VFX to Rule(listOf("TYPE"), emptyList(), true),
        SclCategory.SOUND to Rule(
            listOf("TYPE"),
            listOf("FILE", "START", "DURATION", "VOLUME", "LOOP")
        ),
        SclCategory.MUSIC to Rule(
            emptyList(),
            listOf("TYPE", "STYLE", "FILE", "START", "DURATION", "VOLUME", "LOOP")
        ),
        SclCategory.TRANSITION to Rule(
            listOf("TYPE"),
            listOf("START", "DURATION", "FROM", "TO")
        ),
        SclCategory.TIMELINE to Rule(emptyList(), listOf("TOTAL_DURATION", "FPS")),
        SclCategory.RENDER to Rule(emptyList(), listOf("QUALITY", "FPS", "RESOLUTION", "ENGINE")),
        SclCategory.EXPORT to Rule(emptyList(), listOf("FORMAT", "FILE_NAME", "DESTINATION"))
    )

    /** Commands that only make sense inside a scene. */
    private val SCENE_REQUIRED: Set<SclCategory> = setOf(
        SclCategory.SHOT, SclCategory.ACTION, SclCategory.MOTION_STYLE,
        SclCategory.BODY_LANGUAGE, SclCategory.EXPRESSION, SclCategory.DIALOGUE,
        SclCategory.CAMERA
    )

    /** Commands whose START/DURATION are measured inside the current scene. */
    private val TIMED: Set<SclCategory> = setOf(
        SclCategory.SHOT, SclCategory.ACTION, SclCategory.MOTION_STYLE,
        SclCategory.BODY_LANGUAGE, SclCategory.EXPRESSION, SclCategory.DIALOGUE,
        SclCategory.CAMERA, SclCategory.LIGHTING, SclCategory.SOUND, SclCategory.MUSIC,
        SclCategory.TRANSITION, SclCategory.VFX
    )

    /** Commands that point to a CHARACTER by ID. */
    private val CHARACTER_REF: Set<SclCategory> = setOf(
        SclCategory.ACTION, SclCategory.MOTION_STYLE, SclCategory.BODY_LANGUAGE,
        SclCategory.EXPRESSION, SclCategory.DIALOGUE, SclCategory.VOICE
    )

    /** Parse then validate in one call. */
    fun validateSource(source: String): SclDocument = validate(SclParser.parse(source))

    fun validate(doc: SclDocument): SclDocument {
        val out = ArrayList<SclDiagnostic>()
        out.addAll(doc.diagnostics)

        val characters = HashMap<String, Int>()
        val locations = HashMap<String, Int>()
        val scenes = HashMap<String, Int>()
        val shots = HashMap<String, Int>()

        // Pass 1: field checks and collect declared IDs (so order does not matter).
        for (b in doc.blocks) {
            checkFields(b, out)
            checkNumbers(b, out)
            checkValues(b, out)
            when (b.category) {
                SclCategory.CHARACTER -> declare(b, "ID", characters, "Character", out)
                SclCategory.SCENE -> declare(b, "ID", scenes, "Scene", out)
                SclCategory.SHOT -> declare(b, "ID", shots, "Shot", out)
                SclCategory.ENVIRONMENT -> {
                    val loc = b.text("LOCATION")?.trim()
                    if (loc != null && loc.isNotEmpty() && !locations.containsKey(loc.uppercase())) {
                        locations[loc.uppercase()] = b.lineOf("LOCATION")
                    }
                }
                else -> {}
            }
        }

        val projects = doc.blocksOf(SclCategory.PROJECT)
        if (projects.isEmpty()) {
            err(out, 0, "NO_PROJECT", "PROJECT haipo. Anza na PROJECT { NAME: \"...\" TYPE: \"2D\" }.")
        }
        for (i in 1 until projects.size) {
            err(out, projects[i].startLine, "DUPLICATE_PROJECT", "PROJECT moja tu inaruhusiwa kwa kila faili.")
        }
        if (doc.blocksOf(SclCategory.SCENE).isEmpty()) {
            warn(out, 0, "NO_SCENE", "Hakuna SCENE; Scene Plan haitakuwa na cha kuandaa.")
        }

        // Pass 2: references and timing, scene by scene.
        var inScene = false
        var sceneDuration: Double? = null
        for (b in doc.blocks) {
            if (b.category == SclCategory.SCENE) {
                inScene = true
                sceneDuration = b.number("DURATION")
                val loc = b.text("LOCATION")?.trim()
                if (loc != null && loc.isNotEmpty() && !locations.containsKey(loc.uppercase())) {
                    err(
                        out, b.lineOf("LOCATION"), "UNKNOWN_LOCATION",
                        "Location '$loc' haijatangazwa. Ongeza ENVIRONMENT { LOCATION: \"$loc\" }."
                    )
                }
                continue
            }
            if (SCENE_REQUIRED.contains(b.category) && !inScene) {
                err(
                    out, b.startLine, "OUTSIDE_SCENE",
                    "${b.category.name} lazima iandikwe baada ya SCENE { ... } inayohusika."
                )
            }
            if (CHARACTER_REF.contains(b.category)) checkCharacterRef(b, characters, out)
            if (b.category == SclCategory.CAMERA) checkCamera(b, characters, locations, out)
            if (inScene && TIMED.contains(b.category)) checkTiming(b, sceneDuration, out)
        }

        return SclDocument(doc.blocks, out.sortedBy { it.line })
    }

    // ---------- helpers ----------

    private fun err(out: MutableList<SclDiagnostic>, line: Int, code: String, message: String) {
        out.add(SclDiagnostic(SclSeverity.ERROR, message, line, code))
    }

    private fun warn(out: MutableList<SclDiagnostic>, line: Int, code: String, message: String) {
        out.add(SclDiagnostic(SclSeverity.WARNING, message, line, code))
    }

    private fun num(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun declare(
        b: SclBlock,
        key: String,
        into: HashMap<String, Int>,
        label: String,
        out: MutableList<SclDiagnostic>
    ) {
        val id = b.text(key)?.trim() ?: return
        if (id.isEmpty()) return
        val k = id.uppercase()
        val first = into[k]
        if (first != null) {
            err(
                out, b.lineOf(key), "DUPLICATE_ID",
                "$label '$id' imetangazwa tena (mara ya kwanza ni mstari $first). IDs lazima ziwe za kipekee."
            )
        } else {
            into[k] = b.lineOf(key)
        }
    }

    private fun checkFields(b: SclBlock, out: MutableList<SclDiagnostic>) {
        val rule = RULES[b.category] ?: return
        val name = b.category.name
        for (req in rule.required) {
            if (!b.has(req)) {
                err(out, b.startLine, "MISSING_FIELD", "$name inahitaji field '$req'.")
            } else {
                val t = b.text(req)
                if (t != null && t.isBlank()) {
                    err(out, b.lineOf(req), "EMPTY_FIELD", "Field '$req' ndani ya $name haina maandishi.")
                }
            }
        }
        if (!rule.open) {
            for (f in b.fields) {
                if (!rule.required.contains(f.key) && !rule.optional.contains(f.key)) {
                    warn(out, f.line, "UNKNOWN_FIELD", "Field '${f.key}' haitambuliki ndani ya $name; itapuuzwa.")
                }
            }
        }
    }

    private fun checkNumbers(b: SclBlock, out: MutableList<SclDiagnostic>) {
        for (f in b.fields) {
            val positive = f.key == "DURATION" || f.key == "TOTAL_DURATION" || f.key == "FPS"
            val nonNegative = f.key == "START"
            if (!positive && !nonNegative) continue
            val v = f.value
            if (v !is SclValue.Num) {
                err(out, f.line, "NOT_A_NUMBER", "'${f.key}' lazima iwe namba (mfano 5 au 2.5), lakini umeandika '${v.raw}'.")
                continue
            }
            if (positive && v.value <= 0.0) {
                err(out, f.line, "BAD_NUMBER", "'${f.key}' lazima iwe kubwa kuliko 0.")
            }
            if (nonNegative && v.value < 0.0) {
                err(out, f.line, "BAD_NUMBER", "'START' haiwezi kuwa hasi.")
            }
        }
    }

    private fun enumCheck(
        b: SclBlock,
        key: String,
        allowed: Set<String>,
        isError: Boolean,
        out: MutableList<SclDiagnostic>
    ) {
        val value = b.text(key)?.trim() ?: return
        if (value.isEmpty() || allowed.contains(value.uppercase())) return
        val msg = "Thamani '$value' ya $key ndani ya ${b.category.name} haitambuliki. " +
            "Zinazotambulika: ${allowed.joinToString(", ")}."
        if (isError) err(out, b.lineOf(key), "UNKNOWN_VALUE", msg)
        else warn(out, b.lineOf(key), "UNKNOWN_VALUE", msg)
    }

    private fun checkValues(b: SclBlock, out: MutableList<SclDiagnostic>) {
        when (b.category) {
            SclCategory.PROJECT -> {
                enumCheck(b, "TYPE", SclVocabulary.PROJECT_TYPES, true, out)
                enumCheck(b, "FORMAT", SclVocabulary.FORMATS, true, out)
                enumCheck(b, "LANGUAGE", SclVocabulary.LANGUAGES, false, out)
            }
            SclCategory.STORY -> enumCheck(b, "LANGUAGE", SclVocabulary.LANGUAGES, false, out)
            SclCategory.ACTION -> {
                enumCheck(b, "TYPE", SclVocabulary.ACTIONS, true, out)
                enumCheck(b, "MOTION_STYLE", SclVocabulary.MOTION_STYLES, false, out)
            }
            SclCategory.MOTION_STYLE -> enumCheck(b, "TYPE", SclVocabulary.MOTION_STYLES, false, out)
            SclCategory.BODY_LANGUAGE -> enumCheck(b, "TYPE", SclVocabulary.BODY_LANGUAGES, false, out)
            SclCategory.EXPRESSION -> enumCheck(b, "TYPE", SclVocabulary.EXPRESSIONS, false, out)
            SclCategory.CAMERA -> {
                enumCheck(b, "TYPE", SclVocabulary.CAMERA_TYPES, true, out)
                enumCheck(b, "MOVEMENT", SclVocabulary.CAMERA_MOVEMENTS, true, out)
            }
            SclCategory.TRANSITION -> enumCheck(b, "TYPE", SclVocabulary.TRANSITIONS, false, out)
            SclCategory.EXPORT -> enumCheck(b, "FORMAT", SclVocabulary.EXPORT_FORMATS, false, out)
            else -> {}
        }
    }

    private fun checkCharacterRef(
        b: SclBlock,
        characters: HashMap<String, Int>,
        out: MutableList<SclDiagnostic>
    ) {
        val ref = b.text("CHARACTER")?.trim() ?: return
        if (ref.isEmpty() || characters.containsKey(ref.uppercase())) return
        val known = if (characters.isEmpty()) {
            "Hakuna CHARACTER iliyotangazwa bado."
        } else {
            "IDs zilizopo: ${characters.keys.sorted().joinToString(", ")}."
        }
        err(
            out, b.lineOf("CHARACTER"), "UNKNOWN_CHARACTER",
            "Character '$ref' haijatangazwa. Ongeza CHARACTER { ID: \"$ref\" } au tumia ID iliyopo. $known"
        )
    }

    private fun checkCamera(
        b: SclBlock,
        characters: HashMap<String, Int>,
        locations: HashMap<String, Int>,
        out: MutableList<SclDiagnostic>
    ) {
        val type = b.text("TYPE")?.trim()?.uppercase() ?: ""
        val move = b.text("MOVEMENT")?.trim()?.uppercase() ?: ""
        val target = b.text("TARGET")?.trim() ?: ""
        if (target.isEmpty()) {
            if (type == "TRACKING") {
                err(out, b.startLine, "CAMERA_NEEDS_TARGET", "Camera ya TRACKING inahitaji TARGET (character au location).")
            } else if (move == "FOLLOW" || move == "ORBIT") {
                err(out, b.startLine, "CAMERA_NEEDS_TARGET", "MOVEMENT $move inahitaji TARGET (character au location).")
            }
        } else if (!characters.containsKey(target.uppercase()) && !locations.containsKey(target.uppercase())) {
            err(
                out, b.lineOf("TARGET"), "UNKNOWN_TARGET",
                "TARGET '$target' si character wala location iliyotangazwa."
            )
        }
    }

    private fun checkTiming(b: SclBlock, sceneDuration: Double?, out: MutableList<SclDiagnostic>) {
        if (sceneDuration == null || sceneDuration <= 0.0) return
        val start = b.number("START")
        val dur = b.number("DURATION")
        val name = b.category.name
        if (start != null && start >= 0.0 && start >= sceneDuration) {
            err(
                out, b.lineOf("START"), "START_OUT_OF_SCENE",
                "$name: START (${num(start)}) iko nje ya scene inayodumu sekunde ${num(sceneDuration)}."
            )
            return
        }
        if (dur != null && dur > 0.0) {
            val s = if (start != null && start >= 0.0) start else 0.0
            if (s + dur > sceneDuration + 0.0001) {
                warn(
                    out, b.lineOf("DURATION"), "ENDS_AFTER_SCENE",
                    "$name inaisha sekunde ${num(s + dur)}, baada ya mwisho wa scene (sekunde ${num(sceneDuration)})."
                )
            }
        }
    }
}
