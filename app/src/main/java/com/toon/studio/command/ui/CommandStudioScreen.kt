
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
 * Registry for the animation engines.
 *
 * The actual 2D and 3D engines will be connected here
 * when their implementations are ready.
 */
object CommandEngineRegistry {
    var engines: EngineSet = EngineSet()
}

private const val MAX_BYTES = 200 * 1024

private enum class Pending {
    NEW_PROJECT,
    CLEAR,
    OPEN
}

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

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private class CreateTextDocument : ActivityResultContract<String, Uri?>() {

    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, input)
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private class ReadResult(
    val text: String,
    val error: String?
)

private fun readText(
    context: Context,
    uri: Uri
): ReadResult {
    try {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some document providers do not support persistable permissions.
        }

        val stream = context.contentResolver.openInputStream(uri)
            ?: return ReadResult("", "Imeshindwa kufungua faili.")

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0

        stream.use { input ->
            while (true) {
                val count = input.read(buffer)

                if (count < 0) break

                total += count

                if (total > MAX_BYTES) {
                    return ReadResult(
                        "",
                        "Faili ni kubwa mno (zaidi ya 200 KB) kwa Command Studio."
                    )
                }

                output.write(buffer, 0, count)
            }
        }

        return ReadResult(
            String(output.toByteArray(), Charsets.UTF_8),
            null
        )
    } catch (e: Exception) {
        return ReadResult(
            "",
            "Imeshindwa kusoma faili: ${
                e.message ?: "hitilafu isiyojulikana"
            }"
        )
    }
}

/**
 * Returns null when writing succeeds.
 * Returns an error message when writing fails.
 */
private fun writeText(
    context: Context,
    uri: Uri,
    content: String
): String? {
    return try {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: return "Imeshindwa kufungua faili la kuandika. Jaribu Save As."

        output.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        }

        null
    } catch (e: Exception) {
        "Imeshindwa kuhifadhi: ${
            e.message ?: "hitilafu isiyojulikana"
        }. Jaribu Save As."
    }
}

private fun displayName(
    context: Context,
    uri: Uri
): String {
    try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                if (index >= 0) {
                    val name = cursor.getString(index)

                    if (!name.isNullOrEmpty()) {
                        return name
                    }
                }
            }
        }
    } catch (_: Exception) {
        // Use fallback name below.
    }

    return uri.lastPathSegment ?: "faili"
}

private fun suggestName(text: String): String {
    val raw = ScenePlanBuilder
        .buildFromSource(text)
        .project.name.trim()

    val clean = raw
        .replace(Regex("[^A-Za-z0-9_-]+"), "_")
        .trim('_')

    return (if (clean.isEmpty()) "command_project" else clean) +
        ".mrpscl"
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

private fun sec(duration: Double): String {
    return if (duration == duration.toLong().toDouble()) {
        duration.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", duration)
    }
}

private fun mmss(total: Double): String {
    val seconds = total.toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
fun CommandStudioScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf("") }
    var projectType by rememberSaveable { mutableStateOf("2D") }
    var fileUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var fileName by rememberSaveable { mutableStateOf("") }
    var dirty by rememberSaveable { mutableStateOf(false) }

    var message by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf<SclDocument?>(null) }
    var plan by remember { mutableStateOf<SharedScenePlan?>(null) }
    var report by remember { mutableStateOf<ExecutionReport?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }

    fun resetResults() {
        doc = null
        plan = null
        report = null
        message = ""
    }

    val openLauncher =
        rememberLauncherForActivityResult(OpenWritable()) { uri ->

            if (uri != null) {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        readText(context, uri)
                    }

                    if (result.error != null) {
                        message = result.error
                    } else {
                        resetResults()
                        text = result.text
                        fileUri = uri
                        fileName = displayName(context, uri)
                        dirty = false
                        message = "Faili limefunguliwa: $fileName"
                    }
                }
            }
        }

    val saveAsLauncher =
        rememberLauncherForActivityResult(CreateTextDocument()) { uri ->

            if (uri != null) {
                val content = text

                scope.launch {
                    val error = withContext(Dispatchers.IO) {
                        writeText(context, uri, content)
                    }

                    if (error != null) {
                        message = error
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
                val error = withContext(Dispatchers.IO) {
                    writeText(context, uri, content)
                }

                if (error != null) {
                    message = error
                } else {
                    dirty = false
                    message = "Imehifadhiwa: $fileName"
                }
            }
        }
    }

    /**
     * Validates the command source and builds a scene plan.
     * Returns true only when validation has no errors.
     */
    fun runValidate(): Boolean {
        val document = SclValidator.validateSource(text)

        doc = document
        plan = ScenePlanBuilder.build(document)
        report = null

        return !document.hasErrors
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Command Studio (MRP-SCL)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (fileName.isEmpty()) {
                "Faili: hakuna (rasimu haijahifadhiwa)"
            } else {
                "Faili: $fileName" + if (dirty) " *" else ""
            },
            style = MaterialTheme.typography.bodySmall
        )

        // A. Project type

        Text(
            text = "Project Type",
            style = MaterialTheme.typography.titleSmall
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (projectType == "2D") {
                Button(
                    onClick = { projectType = "2D" }
                ) {
                    Text("2D Animation")
                }

                OutlinedButton(
                    onClick = { projectType = "3D" }
                ) {
                    Text("3D Animation")
                }
            } else {
                OutlinedButton(
                    onClick = { projectType = "2D" }
                ) {
                    Text("2D Animation")
                }

                Button(
                    onClick = { projectType = "3D" }
                ) {
                    Text("3D Animation")
                }
            }
        }

        // B. Command editor

        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                dirty = true
                resetResults()
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Commands (andika au paste)")
            },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            minLines = 12,
            maxLines = 30
        )

        // C. File actions

        Row(
            modifier = Modifier.horizontalScroll(
                rememberScrollState()
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedButton(
                onClick = {
                    if (dirty && text.isNotEmpty()) {
                        pending = Pending.NEW_PROJECT
                    } else {
                        resetResults()
                        text = template(projectType)
                        fileUri = null
                        fileName = ""
                        dirty = true
                    }
                }
            ) {
                Text("New Command Project")
            }

            OutlinedButton(
                onClick = {
                    if (dirty && text.isNotEmpty()) {
                        pending = Pending.OPEN
                    } else {
                        openLauncher.launch(Unit)
                    }
                }
            ) {
                Text("Open Command File")
            }

            OutlinedButton(
                onClick = { save() }
            ) {
                Text("Save")
            }

            OutlinedButton(
                onClick = { saveAs() }
            ) {
                Text("Save As")
            }
        }

        // D. Command execution actions

        Row(
            modifier = Modifier.horizontalScroll(
                rememberScrollState()
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick = {
                    if (text.isBlank()) {
                        message = "Editor ni tupu. Andika au fungua commands kwanza."
                    } else {
                        val valid = runValidate()

                        message = if (valid) {
                            "Uhakiki umekamilika: hakuna makosa."
                        } else {
                            "Uhakiki umekamilika: kuna makosa. Yarekebishe kisha jaribu tena."
                        }
                    }
                }
            ) {
                Text("Validate Commands")
            }

            Button(
                onClick = {
                    if (text.isBlank()) {
                        message = "Editor ni tupu. Andika au fungua commands kwanza."
                    } else if (runValidate()) {
                        message = "Scene Plan imeandaliwa."
                    } else {
                        message = "Scene Plan haikuandaliwa: rekebisha makosa kwanza."
                    }
                }
            ) {
                Text("Generate Scene Plan")
            }

            Button(
                onClick = {
                    if (text.isBlank()) {
                        message = "Editor ni tupu. Andika au fungua commands kwanza."
                    } else if (runValidate()) {

                        val currentPlan = plan

                        if (currentPlan != null) {
                            val executionReport = SceneExecutor.run(
                                currentPlan,
                                CommandEngineRegistry.engines
                            )

                            report = executionReport

                            message = if (
                                executionReport.executedCount == 0
                            ) {
                                "Hakuna kilichotekelezwa bado: animation engines (2D/3D) hazijajengwa. Scene Plan iko tayari kwao."
                            } else {
                                "Commands zimetumwa kwa engines. Angalia ripoti hapa chini."
                            }
                        }
                    } else {
                        message = "Commands hazikuendeshwa: rekebisha makosa kwanza."
                    }
                }
            ) {
                Text("Run Commands")
            }

            OutlinedButton(
                onClick = {
                    pending = Pending.CLEAR
                }
            ) {
                Text("Clear Editor")
            }
        }

        if (message.isNotEmpty()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        // E. Output panel

        OutputPanel(
            doc = doc,
            plan = plan,
            report = report,
            chosenType = projectType
        )
    }

    // F. Confirmation dialog

    val currentPending = pending

    if (currentPending != null) {

        val body = when (currentPending) {
            Pending.NEW_PROJECT ->
                "Una mabadiliko ambayo hayajahifadhiwa. Mradi mpya utayafuta."

            Pending.CLEAR ->
                "Editor itafutwa. Faili ulilolifungua halitaguswa hadi ubonyeze Save."

            Pending.OPEN ->
                "Una mabadiliko ambayo hayajahifadhiwa. Kufungua faili jingine kutayafuta."
        }

        AlertDialog(
            onDismissRequest = {
                pending = null
            },
            title = {
                Text("Una uhakika?")
            },
            text = {
                Text(body)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null

                        when (currentPending) {

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
                   
