package com.toon.studio.command.plan

import com.toon.studio.command.model.SclBlock
import com.toon.studio.command.model.SclCategory
import com.toon.studio.command.model.SclDocument
import com.toon.studio.command.parser.SclParser

/**
 * Turns parsed MRP-SCL commands into a [SharedScenePlan].
 *
 * Works best on a document that passed SclValidator, but never throws: anything
 * that cannot be understood is skipped. Commands that belong to a scene
 * (SHOT, ACTION, CAMERA, DIALOGUE...) attach to the closest SCENE above them.
 */
object ScenePlanBuilder {

    /** Actions that move a character through space; they also appear in ScenePlan.movements. */
    private val MOVEMENT_TYPES: Set<String> = setOf(
        "WALK", "RUN", "JUMP", "CLIMB", "FLY", "SWIM", "FALL", "SNEAK",
        "APPROACH", "RETREAT", "FOLLOW",
        "ENTER", "EXIT", "ENTER_SCENE", "EXIT_SCENE", "ENTER_FOREST", "EXIT_FOREST"
    )

    private class SceneAcc(
        val id: String,
        val title: String,
        val locationId: String,
        val durationSec: Double,
        val timeOfDay: String,
        val description: String,
        val line: Int
    ) {
        val shots = ArrayList<ShotPlan>()
        val actions = ArrayList<ActionPlan>()
        val expressions = ArrayList<ExpressionPlan>()
        val bodyLanguage = ArrayList<BodyLanguagePlan>()
        val dialogue = ArrayList<DialoguePlan>()
        val cameras = ArrayList<CameraPlan>()
        val lighting = ArrayList<LightingPlan>()
        val effects = ArrayList<EffectPlan>()
        val audio = ArrayList<AudioPlan>()
        val transitions = ArrayList<TransitionPlan>()

        /** MOTION_STYLE commands: upper-case character ID to style. */
        val styles = HashMap<String, String>()
    }

    /** Parse and build in one call. */
    fun buildFromSource(source: String): SharedScenePlan = build(SclParser.parse(source))

    fun build(doc: SclDocument): SharedScenePlan {
        val charIds = LinkedHashMap<String, String>()
        val characters = ArrayList<CharacterDef>()
        val locIds = LinkedHashMap<String, String>()
        val locSettings = LinkedHashMap<String, LinkedHashMap<String, String>>()
        val voiceBlocks = ArrayList<SclBlock>()
        var project = ProjectInfo()
        var projectSeen = false
        var story: StoryInfo? = null
        var timelineTotal: Double? = null
        var timelineFps: Int? = null
        var render = RenderSettings()
        var renderSeen = false
        var export = ExportSettings()
        var exportSeen = false

        // Pass 1: project-wide information, so order in the file does not matter.
        for (b in doc.blocks) {
            when (b.category) {
                SclCategory.PROJECT -> {
                    if (!projectSeen) {
                        projectSeen = true
                        project = ProjectInfo(
                            name = b.t("NAME"),
                            kind = ProjectKind.fromText(b.t("TYPE")) ?: ProjectKind.ANIMATION_2D,
                            format = b.t("FORMAT").ifEmpty { "16:9" },
                            language = b.t("LANGUAGE").uppercase().ifEmpty { "KISWAHILI" },
                            fps = positiveInt(b.number("FPS")) ?: 30,
                            description = b.t("DESCRIPTION")
                        )
                    }
                }
                SclCategory.STORY -> {
                    if (story == null) {
                        story = StoryInfo(
                            title = b.t("TITLE"),
                            text = b.t("TEXT"),
                            language = b.t("LANGUAGE")
                        )
                    }
                }
                SclCategory.CHARACTER -> {
                    val id = b.t("ID")
                    if (id.isNotEmpty() && !charIds.containsKey(id.uppercase())) {
                        charIds[id.uppercase()] = id
                        characters.add(
                            CharacterDef(
                                id = id,
                                name = b.t("NAME"),
                                appearance = b.t("APPEARANCE"),
                                description = b.t("DESCRIPTION"),
                                outfit = b.t("OUTFIT"),
                                species = b.t("SPECIES"),
                                voice = b.t("VOICE")
                            )
                        )
                    }
                }
                SclCategory.ENVIRONMENT -> {
                    val id = b.t("LOCATION")
                    if (id.isNotEmpty()) {
                        val key = id.uppercase()
                        if (!locIds.containsKey(key)) locIds[key] = id
                        locSettings.getOrPut(key) { LinkedHashMap() }
                            .putAll(mapSettings(b, setOf("LOCATION")))
                    }
                }
                SclCategory.VOICE -> voiceBlocks.add(b)
                SclCategory.TIMELINE -> {
                    if (timelineTotal == null) {
                        timelineTotal = b.number("TOTAL_DURATION")?.takeIf { it > 0.0 }
                        timelineFps = positiveInt(b.number("FPS"))
                    }
                }
                SclCategory.RENDER -> {
                    if (!renderSeen) {
                        renderSeen = true
                        render = RenderSettings(
                            quality = b.t("QUALITY"),
                            fps = positiveInt(b.number("FPS")),
                            resolution = b.t("RESOLUTION"),
                            engine = b.t("ENGINE")
                        )
                    }
                }
                SclCategory.EXPORT -> {
                    if (!exportSeen) {
                        exportSeen = true
                        export = ExportSettings(
                            format = b.t("FORMAT").uppercase().ifEmpty { "MP4" },
                            fileName = b.t("FILE_NAME"),
                            destination = b.t("DESTINATION")
                        )
                    }
                }
                else -> {}
            }
        }

        val locations = ArrayList<LocationDef>()
        for (entry in locIds.entries) {
            locations.add(LocationDef(entry.value, locSettings[entry.key] ?: emptyMap()))
        }

        val voices = ArrayList<VoiceDef>()
        for (b in voiceBlocks) {
            val ref = b.t("CHARACTER")
            if (ref.isEmpty()) continue
            voices.add(
                VoiceDef(
                    characterId = resolve(charIds, ref),
                    type = b.t("TYPE"),
                    language = b.t("LANGUAGE"),
                    style = b.t("STYLE"),
                    pitch = b.t("PITCH"),
                    speed = b.t("SPEED"),
                    voiceId = b.t("VOICE_ID")
                )
            )
        }

        // Pass 2: scenes and everything that belongs to a scene.
        val accs = ArrayList<SceneAcc>()
        var cur: SceneAcc? = null
        for (b in doc.blocks) {
            if (b.category == SclCategory.SCENE) {
                val id = b.t("ID").ifEmpty { "SCENE_" + (accs.size + 1) }
                val acc = SceneAcc(
                    id = id,
                    title = b.t("TITLE"),
                    locationId = resolve(locIds, b.t("LOCATION")),
                    durationSec = b.number("DURATION")?.takeIf { it > 0.0 } ?: 0.0,
                    timeOfDay = b.t("TIME_OF_DAY"),
                    description = b.t("DESCRIPTION"),
                    line = b.startLine
                )
                accs.add(acc)
                cur = acc
                continue
            }
            val scene = cur ?: continue
            when (b.category) {
                SclCategory.SHOT -> scene.shots.add(
                    ShotPlan(
                        id = b.t("ID"),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        description = b.t("DESCRIPTION"),
                        line = b.startLine
                    )
                )
                SclCategory.ACTION -> scene.actions.add(
                    ActionPlan(
                        characterId = resolve(charIds, b.t("CHARACTER")),
                        type = b.t("TYPE").uppercase(),
                        motionStyle = b.t("MOTION_STYLE").uppercase(),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        target = resolve(locIds, resolve(charIds, b.t("TARGET"))),
                        direction = b.t("DIRECTION").uppercase(),
                        line = b.startLine
                    )
                )
                SclCategory.MOTION_STYLE -> {
                    val who = resolve(charIds, b.t("CHARACTER")).uppercase()
                    val style = b.t("TYPE").uppercase()
                    if (who.isNotEmpty() && style.isNotEmpty()) scene.styles[who] = style
                }
                SclCategory.EXPRESSION -> scene.expressions.add(
                    ExpressionPlan(
                        characterId = resolve(charIds, b.t("CHARACTER")),
                        type = b.t("TYPE").uppercase(),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        intensity = b.number("INTENSITY"),
                        line = b.startLine
                    )
                )
                SclCategory.BODY_LANGUAGE -> scene.bodyLanguage.add(
                    BodyLanguagePlan(
                        characterId = resolve(charIds, b.t("CHARACTER")),
                        type = b.t("TYPE").uppercase(),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        line = b.startLine
                    )
                )
                SclCategory.DIALOGUE -> scene.dialogue.add(
                    DialoguePlan(
                        characterId = resolve(charIds, b.t("CHARACTER")),
                        text = b.t("TEXT"),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        emotion = b.t("EMOTION"),
                        voice = b.t("VOICE"),
                        line = b.startLine
                    )
                )
                SclCategory.CAMERA -> scene.cameras.add(
                    CameraPlan(
                        type = b.t("TYPE").uppercase(),
                        movement = b.t("MOVEMENT").uppercase(),
                        target = resolve(locIds, resolve(charIds, b.t("TARGET"))),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        speed = b.t("SPEED"),
                        fov = b.number("FOV"),
                        line = b.startLine
                    )
                )
                SclCategory.LIGHTING -> scene.lighting.add(
                    LightingPlan(
                        type = b.t("TYPE").uppercase(),
                        intensity = b.number("INTENSITY"),
                        color = b.t("COLOR"),
                        timeOfDay = b.t("TIME_OF_DAY"),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        line = b.startLine
                    )
                )
                SclCategory.VFX, SclCategory.WEATHER, SclCategory.PHYSICS -> scene.effects.add(
                    EffectPlan(
                        kind = b.category.name,
                        type = b.t("TYPE").uppercase(),
                        params = mapSettings(b, setOf("TYPE", "START", "DURATION")),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        line = b.startLine
                    )
                )
                SclCategory.SOUND, SclCategory.MUSIC -> scene.audio.add(
                    AudioPlan(
                        kind = if (b.category == SclCategory.MUSIC) AudioKind.MUSIC else AudioKind.SOUND,
                        type = b.t("TYPE").uppercase(),
                        file = b.t("FILE"),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        volume = b.number("VOLUME"),
                        loop = isTrue(b.t("LOOP")),
                        line = b.startLine
                    )
                )
                SclCategory.TRANSITION -> scene.transitions.add(
                    TransitionPlan(
                        type = b.t("TYPE").uppercase(),
                        startSec = startOf(b),
                        durationSec = durationOf(b),
                        from = b.t("FROM"),
                        to = b.t("TO"),
                        line = b.startLine
                    )
                )
                else -> {}
            }
        }

        val plans = ArrayList<ScenePlan>()
        var offset = 0.0
        for (a in accs) {
            plans.add(finish(a, offset))
            offset += a.durationSec
        }

        val total = timelineTotal ?: offset
        return SharedScenePlan(
            project = project,
            story = story,
            characters = characters,
            locations = locations,
            voices = voices,
            scenes = plans,
            timeline = TimelineInfo(total, timelineFps ?: project.fps),
            render = render,
            export = export
        )
    }

    private fun finish(a: SceneAcc, offset: Double): ScenePlan {
        val acts = a.actions.map { act ->
            if (act.motionStyle.isEmpty()) {
                val style = a.styles[act.characterId.uppercase()]
                if (style != null) act.copy(motionStyle = style) else act
            } else {
                act
            }
        }.sortedBy { it.startSec }

        val moves = acts.filter { MOVEMENT_TYPES.contains(it.type) }.map {
            MovementPlan(
                characterId = it.characterId,
                type = it.type,
                motionStyle = it.motionStyle,
                target = it.target,
                direction = it.direction,
                startSec = it.startSec,
                durationSec = it.durationSec,
                line = it.line
            )
        }

        return ScenePlan(
            id = a.id,
            title = a.title,
            locationId = a.locationId,
            durationSec = a.durationSec,
            startOffsetSec = offset,
            timeOfDay = a.timeOfDay,
            description = a.description,
            shots = a.shots.sortedBy { it.startSec },
            actions = acts,
            movements = moves,
            expressions = a.expressions.sortedBy { it.startSec },
            bodyLanguage = a.bodyLanguage.sortedBy { it.startSec },
            dialogue = a.dialogue.sortedBy { it.startSec },
            cameras = a.cameras.sortedBy { it.startSec },
            lighting = a.lighting.sortedBy { it.startSec },
            effects = a.effects.sortedBy { it.startSec },
            audio = a.audio.sortedBy { it.startSec },
            transitions = a.transitions.sortedBy { it.startSec },
            line = a.line
        )
    }

    // ---------- helpers ----------

    /** Trimmed text of a field, or "" when missing. */
    private fun SclBlock.t(key: String): String = this.text(key)?.trim() ?: ""

    private fun startOf(b: SclBlock): Double = (b.number("START") ?: 0.0).coerceAtLeast(0.0)

    private fun durationOf(b: SclBlock): Double? = b.number("DURATION")?.takeIf { it > 0.0 }

    private fun positiveInt(v: Double?): Int? {
        if (v == null) return null
        val i = v.toInt()
        return if (i > 0) i else null
    }

    private fun isTrue(text: String): Boolean = when (text.trim().uppercase()) {
        "TRUE", "YES", "ON", "1", "LOOP" -> true
        else -> false
    }

    /** Returns the declared spelling of an ID (case-insensitive), or the text as written. */
    private fun resolve(declared: Map<String, String>, ref: String): String {
        val r = ref.trim()
        return declared[r.uppercase()] ?: r
    }

    private fun mapSettings(b: SclBlock, skip: Set<String>): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for (f in b.fields) {
            if (!skip.contains(f.key)) m[f.key] = f.value.raw
        }
        return m
    }
}
