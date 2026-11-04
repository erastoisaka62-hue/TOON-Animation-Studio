package com.toon.studio.screens

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.toon.studio.export.Mp4Exporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Outcome(val ok: Boolean, val message: String, val path: String?)

@Composable
fun SampleVideoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var vertical by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var status by remember {
        mutableStateOf("Bonyeza kitufe ili kutengeneza video ya sampuli ya sekunde 8.")
    }
    var previewPath by remember { mutableStateOf<String?>(null) }

    fun generate() {
        scope.launch {
            busy = true
            progress = 0f
            previewPath = null
            status = "Inatengeneza video..."
            val w = if (vertical) 720 else 1280
            val h = if (vertical) 1280 else 720
            val outcome = withContext(Dispatchers.IO) {
                try {
                    val file = File(context.cacheDir, "mrp_sample.mp4")
                    Mp4Exporter.export(file, w, h, 30) { p -> progress = p }
                    Outcome(true, saveToGallery(context, file), file.absolutePath)
                } catch (e: Throwable) {
                    Outcome(false, "Imeshindwa: " + (e.message ?: e.javaClass.simpleName), null)
                }
            }
            status = outcome.message
            previewPath = outcome.path
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Video ya sampuli", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Sungura wa katuni anatembea msituni (sekunde 8). Hii ni jaribio la mfumo wa video: " +
                "kuchora, kufunga MP4 na kuhifadhi kwenye Gallery. Ni katuni ya 2D, si 3D bado.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text("Aina ya video", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!vertical) {
                Button(onClick = { }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("YouTube 16:9")
                }
                OutlinedButton(
                    onClick = { vertical = true },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("TikTok 9:16")
                }
            } else {
                OutlinedButton(
                    onClick = { vertical = false },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("YouTube 16:9")
                }
                Button(onClick = { }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("TikTok 9:16")
                }
            }
        }

        Button(
            onClick = { generate() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Inatengeneza..." else "Tengeneza video")
        }

        if (busy) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }

        Text(status, style = MaterialTheme.typography.bodyMedium)

        previewPath?.let { path ->
            key(path) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(path)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (vertical) 420.dp else 220.dp)
                )
            }
        }

        Text(
            "Hali: inajaribiwa. Sampuli hii ni katuni ya 2D iliyochorwa kwa code.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun saveToGallery(context: Context, file: File): String {
    val name = "MrP_sample_" + System.currentTimeMillis() + ".mp4"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/MrP")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Imeshindwa kutengeneza faili kwenye Gallery")
        val out = resolver.openOutputStream(uri)
            ?: throw IllegalStateException("Imeshindwa kufungua faili la Gallery")
        out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
        return "Imehifadhiwa kwenye Gallery (folda Movies/MrP): " + name
    } else {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        val dest = File(dir, name)
        file.copyTo(dest, overwrite = true)
        return "Imehifadhiwa: " + dest.absolutePath
    }
}
