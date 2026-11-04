package com.toon.studio.command.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toon.studio.command.engine.EngineSet
import com.toon.studio.command.engine.ExecutionReport
import com.toon.studio.command.engine.SceneExecutor
import com.toon.studio.command.model.SclDocument
import com.toon.studio.command.plan.ProjectKind
import com.toon.studio.command.plan.ScenePlanBuilder
import com.toon.studio.command.plan.SharedScenePlan
import com.toon.studio.command.validation.SclValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Where the 2D and 3D animation engines plug in (Task 02 and Task 03).
 * While this is an empty EngineSet, "Run Commands" honestly reports that
 * nothing could be executed.
 */
object CommandEngineRegistry {
    var engines: EngineSet = EngineSet()
}

private const val MAX_BYTES = 200 * 1024

private enum class Pending { NEW_PROJECT, CLEAR, OPEN }

private class OpenWritable : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private class CreateTextDocument : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, input)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private class ReadResult(val text: String, val error: String?)

private fun readText(context: Context, uri: Uri): ReadResult {
    try {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Not every provider allows persistable permission; reading still works.
        }
        val stream = context.contentResolver.openInputStream(uri)
            ?: return ReadResult("", "Imeshindwa kufungua faili.")
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        stream.use { input ->
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) {
                    return ReadResult("", "Faili ni kubwa mno (zaidi ya 200 KB) kwa Command Studio.")
                }
                out.write(chunk, 0, n)
            }
        }
        return ReadResult(String(out.toByteArray(), Charsets.UTF_8), null)
    } catch (e: Exception) {
        return ReadResult("", "Imeshindwa kusoma faili: ${e.message ?: "hitilafu isiyojulikana"}")
    }
}

/** Returns an error message, or null when the file was written. */
private fun writeText(context: Context, uri: Uri, content: String): String? {
    try {
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: return "Imeshindwa kufungua faili la kuandika. Jaribu Save As."
        out.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        return null
    } catch (e: Exception) {
        return "Imeshindwa kuhifadhi: ${e.message ?: "hitilafu isiyojulikana"}. Jaribu Save As."
    }
}

private fun displayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) {
                    val n = c.getString(i)
                    if (!n.isNullOrEmpty()) return n
                }
            }
        }
    } catch (e: Exception) {
        // fall through to the path-based name
    }
    return uri.lastPathSegment ?: "faili"
}

private fun suggestName(text: String): String {
    val raw = ScenePlanBuilder.buildFromSource(text).project.name.trim()
    val clean = raw.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_')
    return (if (clean.isEmpty()) "command_project" else clean) + ".mrpscl"
}

private fun template(type: String): String = """
# MRP-SCL: mfano wa mradi. Futa au badilisha upendavyo.
PROJECT {
  NAME: "Siri ya Msitu"
  TYPE: "$type"
  FORMAT: "16:9"
  LANGUAGE: "KISWAHILI"
}

SCENE {
  ID: "SCENE_01"
  LOCATION: "FOREST_01"
  DURATION: 20
}

CHARACTER {
  ID: "RABBIT_01"
  APPEARANCE: "EXISTING_CHARACTER"
}

ACTION {
  CHARACTER: "RABBIT_01"
  TYPE: "ENTER_FOREST"
  MOTION_STYLE: "NATURAL"
  START: 0
  DURATION: 5
}

EXPRESSION {
  CHARACTER: "RABBIT_01"
  TYPE: "CURIOUS"
}

CAMERA {
  TYPE: "AERIAL"
  MOVEMENT: "DESCEND"
  TARGET: "FOREST_01"
}

CAMERA {
  TYPE: "TRACKING"
  TARGET: "RABBIT_01"
  MOVEMENT: "FOLLOW"
}

ENVIRONMENT {
  LOCATION: "FOREST_01"
  WIND: "ACTIVE"
  BIRDS: "FLYING"
  BUTTERFLIES: "ACTIVE"
}

DIALOGUE {
  CHARACTER: "RABBIT_01"
  TEXT: "Msitu huu unaonekana kuwa na siri nyingi!"
  START: 8
}
""".trimIndent()

private fun sec(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

private fun mmss(total: Double): String {
    val t = total.toInt()
    return "${t / 60}:${(t % 60).toString().padStart(2, '0')}"
}

private fun timing(start: Double, dur: Double?): String =
    "s ${sec(start)}" + (if (dur != null) "–${sec(start + dur)}" else "")

@Composable
fun CommandStudioScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf("") }
    var projectType by rememberSaveable { mutableStateOf("2D") }
    var fileUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var fileName by rememberSaveable { mutableStateOf("") }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(0) }

    var message by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf<SclDocument?>(null) }
    var plan by remember { mutableStateOf<SharedScenePlan?>(null) }
    var planReady by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ExecutionReport?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }

    fun resetResults() {
        doc = null
        plan = null
        planReady = false
        report = null
        message = ""
        selected = 0
    }

    val openLauncher = rememberLauncherForActivityResult(OpenWritable()) { uri ->
        if (uri != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) { readText(context, uri) }
                if (r.error != null) {
                    message = r.error
                } else {
                    resetResults()
                    text = r.text
                    fileUri = uri
                    fileName = displayName(context, uri)
                    dirty = false
                    message = "Faili limefunguliwa: $fileName"
                }
            }
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(CreateTextDocument()) { uri ->
        if (uri != null) {
            val content = text
            scope.launch {
                val err = withContext(Dispatchers.IO) { writeText(context, uri, content) }
                if (err != null) {
                    message = err
                } else {
                    fileUri = uri
                    fileName = displayName(context, uri)
                    dirty = false
                    message = "Imehifadhiwa: $fileName"
                }
            }
        }
    }

    fun saveAs() {
        saveAsLauncher.launch(suggestName(text))
    }

    fun save() {
        val uri = fileUri
        if (uri == null) {
            saveAs()
        } else {
            val content = text
            scope.launch {
                val err = withContext(Dispatchers.IO) { writeText(context, uri, content) }
                if (err != null) {
                    message = err
                } else {
                    dirty = false
                    message = "Imehifadhiwa: $fileName"
                }
            }
        }
    }

    /** Validates and builds the plan. Returns true when there are no errors. */
    fun runValidate(): Boolean {
        val d = SclValidator.validateSource(text)
        doc = d
        plan = ScenePlanBuilder.build(d)
        report = null
        planReady = false
        return !d.hasErrors
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Command Studio (MRP-SCL)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (fileName.isEmpty()) "Faili: hakuna (rasimu haijahifadhiwa)" else "Faili: $fileName" + (if (dirty) " *" else ""),
            style = MaterialTheme.typography.bodySmall
        )

        // A. Project type (used for new projects; the PROJECT command decides the real type)
        Text("Project Type", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (projectType == "2D") {
                Button(onClick = { projectType = "2D" }) { Text("2D Animation") }
                OutlinedButton(onClick = { projectType = "3D" }) { Text("3D Animation") }
            } else {
                OutlinedButton(onClick = { projectType = "2D" }) { Text("2D Animation") }
                Button(onClick = { projectType = "3D" }) { Text("3D Animation") }
            }
        }

        // B. Editor
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                dirty = true
                resetResults()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Commands (andika au paste)") },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            minLines = 12,
            maxLines = 30
        )

        // C. Actions
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = {
                if (dirty && text.isNotEmpty()) pending = Pending.NEW_PROJECT
                else {
                    resetResults()
                    text = template(projectType)
                    fileUri = null
                    fileName = ""
                    dirty = true
                }
            }) { Text("New Command Project") }
            OutlinedButton(onClick = {
                if (dirty && text.isNotEmpty()) pending = Pending.OPEN else openLauncher.launch(Unit)
            }) { Text("Open Command File") }
            OutlinedButton(onClick = { save() }) { Text("Save") }
            OutlinedButton(onClick = { saveAs() }) { Text("Save As") }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                if (text.isBlank()) message = "Editor ni tupu. Andika au fungua commands kwanza."
                else {
                    val ok = runValidate()
                    message = if (ok) "Uhakiki umekamilika: hakuna makosa." else "Uhakiki umekamilika: kuna makosa. Yarekebishe kisha jaribu tena."
                }
            }) { Text("Validate Commands") }
            Button(onClick = {
                if (text.isBlank()) message = "Editor ni tupu. Andika au fungua commands kwanza."
                else if (runValidate()) {
                    planReady = true
                    message = "Scene Plan imeandaliwa."
                } else {
                    message = "Scene Plan haikuandaliwa: rekebisha makosa kwanza."
                }
            }) { Text("Generate Scene Plan") }
            Button(onClick = {
                if (text.isBlank()) message = "Editor ni tupu. Andika au fungua commands kwanza."
                else if (runValidate()) {
                    val p = plan
                    if (p != null) {
                        val r = SceneExecutor.run(p, CommandEngineRegistry.engines)
                        report = r
                        planReady = true
                        message = if (r.executedCount == 0) {
                            "Hakuna kilichotekelezwa bado: animation engines (2D/3D) hazijajengwa. Scene Plan iko tayari kwao."
                        } else {
                            "Commands zimetumwa kwa engines. Angalia ripoti hapa chini."
                        }
                    }
                } else {
                    message = "Commands hazikuendeshwa: rekebisha makosa kwanza."
                }
            }) { Text("Run Commands") }
            OutlinedButton(onClick = { pending = Pending.CLEAR }) { Text("Clear Editor") }
        }

        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }

        // D. Output panel
        OutputPanel(doc = doc, plan = plan, report = report, chosenType = projectType)

        // E. Scene breakdown
        val p = plan
        if (planReady && p != null) {
            SceneBreakdown(
                plan = p,
                selected = selected,
                onSelect = { selected = it }
            )
        }
    }

    val ask = pending
    if (ask != null) {
        val body = when (ask) {
            Pending.NEW_PROJECT -> "Una mabadiliko ambayo hayajahifadhiwa. Mradi mpya utayafuta."
            Pending.CLEAR -> "Editor itafutwa. Faili ulilolifungua halitaguswa hadi ubonyeze Save."
            Pending.OPEN -> "Una mabadiliko ambayo hayajahifadhiwa. Kufungua faili jingine kutayafuta."
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Una uhakika?") },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    when (ask) {
                        Pending.NEW_PROJECT -> {
                            resetResults()
                            text = template(projectType)
                            fileUri = null
                            fileName = ""
                            dirty = true
                        }
                        Pending.CLEAR -> {
                            resetResults()
                            text = ""
                            fileUri = null
                            fileName = ""
                            dirty = false
                        }
                        Pending.OPEN -> openLauncher.launch(Unit)
                    }
                }) { Text("Endelea") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Ghairi") } }
        )
    }
}

@Composable
private fun OutputPanel(
    doc: SclDocument?,
    plan: SharedScenePlan?,
    report: ExecutionReport?,
    chosenType: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Output", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (doc == null) {
                Text("Bado haijahakikiwa. Bonyeza Validate Commands.")
                return@Column
            }
            val errors = doc.errors
            val warnings = doc.warnings
            if (errors.isEmpty()) {
                Text("Hali: commands ni sahihi" + (if (warnings.isEmpty()) "." else " (zina maonyo ${warnings.size})."))
            } else {
                Text("Hali: makosa ${errors.size}, maonyo ${warnings.size}", color = MaterialTheme.colorScheme.error)
            }
            for (e in errors) {
                Text("• KOSA: ${e.display()}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            for (w in warnings) {
                Text("• Onyo: ${w.display()}", style = MaterialTheme.typography.bodySmall)
            }

            if (plan != null) {
                val actual = if (plan.project.kind == ProjectKind.ANIMATION_3D) "3D" else "2D"
                if (actual != chosenType) {
                    Text(
                        "Kumbuka: umechagua $chosenType, lakini PROJECT ndani ya commands inasema $actual. Commands ndizo zinazotumika.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val draft = if (errors.isEmpty()) "" else " (makadirio; kuna makosa)"
                Text("Scenes: ${plan.sceneCount}$draft")
                Text("Characters: ${plan.characterCount}")
                Text("Locations: ${plan.locationCount}")
                Text(
                    "Actions: ${plan.actionCount}" +
                        (if (plan.actionTypes.isEmpty()) "" else " (${plan.actionTypes.joinToString(", ")})")
                )
                Text("Dialogue: ${plan.dialogueCount} · Camera: ${plan.cameraCount}")
                Text("Muda makadirio: sekunde ${sec(plan.estimatedDurationSec)} (${mmss(plan.estimatedDurationSec)})")
            }

            if (report != null) {
                Text("Ripoti ya Run Co
