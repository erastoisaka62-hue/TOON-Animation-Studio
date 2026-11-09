package com.toon.studio.command.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toon.studio.command.plan.ProjectKind
import com.toon.studio.command.plan.SharedScenePlan
import com.toon.studio.export.Mp4Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun PlanExportPanel(
    plan: SharedScenePlan?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var status by remember {
        mutableStateOf("Tengeneza Scene Plan ili uweze ku-export video.")
    }

    val is3D = plan?.project?.kind == ProjectKind.ANIMATION_3D

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "VIDEO EXPORT",
                style = MaterialTheme.typography.titleMedium
            )

            if (plan == null) {
                Text(
                    "Hakuna Scene Plan bado. Andaa commands na ubonyeze Generate Scene Plan."
                )
            } else {
                Text("Project: ${plan.project.name}")
                Text("Scenes: ${plan.sceneCount}")
                Text(
                    "Duration: ${
                        String.format(
                            Locale.US,
                            "%.1f",
                            plan.estimatedDurationSec
                        )
                    } seconds"
                )
                Text("Format: ${plan.project.format}")

                if (is3D) {
                    Text(
                        "3D export bado haipatikani. 3D renderer haijaunganishwa.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "Renderer: Plan-based 2D"
                    )
                }
            }

            if (exporting) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Button(
                onClick = {
                    val currentPlan = plan

                    if (currentPlan == null) {
                        status = "Tengeneza Scene Plan kwanza."
                        return@Button
                    }

                    if (currentPlan.project.kind == ProjectKind.ANIMATION_3D) {
                        status = "3D export bado haijaunganishwa."
                        return@Button
                    }

                    if (currentPlan.scenes.isEmpty()) {
                        status = "Hakuna scenes za ku-export."
                        return@Button
                    }

                    val cleanName = currentPlan.project.name
                        .trim()
                        .replace(Regex("[^A-Za-z0-9_-]+"), "_")
                        .trim('_')
                        .ifBlank { "toon_video" }

                    val fileName = "${cleanName}_${System.currentTimeMillis()}.mp4"
                    val temporaryFile = File(context.cacheDir, fileName)

                    exporting = true
                    progress = 0f
                    status = "Inaandaa video kutoka kwenye Scene Plan..."

                    scope.launch {
                        try {
                            val resultMessage = withContext(Dispatchers.IO) {
                                var lastPercent = -1

                                val result = Mp4Exporter.export(
                                    plan = currentPlan,
                                    output = temporaryFile,
                                    onProgress = { value ->
                                        val safeValue = value.coerceIn(0f, 1f)
                                        val percent = (safeValue * 100).toInt()

                                        if (percent != lastPercent) {
                                            lastPercent = percent

                                            scope.launch {
                                                progress = safeValue
                                            }
                                        }
                                    }
                                )

                                check(
                                    temporaryFile.exists() &&
                                        temporaryFile.length() > 0L
                                ) {
                                    "MP4 haikutengenezwa au haina data."
                                }

                                val savedLocation = saveVideo(
                                    context = context,
                                    source = temporaryFile,
                                    fileName = fileName
                                )

                                temporaryFile.delete()

                                "${result.message}\n$savedLocation"
                            }

                            progress = 1f
                            status = resultMessage

                        } catch (e: Exception) {
                            status = "Export imeshindwa: ${
                                e.message ?: e.javaClass.simpleName
                            }"
                        } finally {
                            exporting = false
                        }
                    }
                },
                enabled = plan != null && !is3D && !exporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (exporting) "Inatengeneza video..." else "EXPORT MP4"
                )
            }

            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Saves an exported MP4 in Movies/TOON Studio on Android 10+.
 * On older Android versions it uses the app-specific Movies directory.
 */
private fun saveVideo(
    context: Context,
    source: File,
    fileName: String
): String {

    check(source.exists() && source.length() > 0L) {
        "Faili la video halipo au ni tupu."
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/TOON Studio"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException(
            "Imeshindwa kutengeneza video kwenye Gallery."
        )

        try {
            val output = resolver.openOutputStream(uri)
                ?: throw IllegalStateException(
                    "Imeshindwa kufungua faili la Gallery."
                )

            output.use { stream ->
                source.inputStream().use { input ->
                    input.copyTo(stream)
                }
            }

            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }

            resolver.update(uri, publishValues, null, null)

            return "Imehifadhiwa kwenye Gallery: Movies/TOON Studio/$fileName"

        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    val moviesDirectory = context.getExternalFilesDir(
        Environment.DIRECTORY_MOVIES
    ) ?: File(context.filesDir, "Movies")

    val toonDirectory = File(moviesDirectory, "TOON Studio")

    check(toonDirectory.exists() || toonDirectory.mkdirs()) {
        "Imeshindwa kutengeneza folder la video."
    }

    val destination = File(toonDirectory, fileName)
    source.copyTo(destination, overwrite = true)

    return "Imehifadhiwa kwenye: ${destination.absolutePath}"
}
