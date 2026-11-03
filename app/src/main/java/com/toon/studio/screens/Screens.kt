package com.toon.studio.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.toon.studio.navigation.Routes

private data class ScreenInfo(
    val title: String,
    val description: String,
    val status: String
)

private val screens: Map<String, ScreenInfo> = mapOf(
    Routes.HOME to ScreenInfo(
        "Karibu TOON Studio",
        "Dashboard ya miradi yako itaonekana hapa.",
        "Planned (Phase 3)"
    ),
    Routes.CREATE_FILM to ScreenInfo(
        "Create New Film",
        "Fomu ya kuanzisha film mpya: jina, lugha, aina ya video na ubora.",
        "Planned (Phase 3)"
    ),
    Routes.PROJECTS to ScreenInfo(
        "My Projects",
        "Orodha ya miradi yako iliyohifadhiwa.",
        "Planned (Phase 3)"
    ),
    Routes.COMMAND_STUDIO to ScreenInfo(
        "Command Studio",
        "Mahali pa kuandika na kuendesha commands za film.",
        "Planned (Phase 2)"
    ),
    Routes.CHARACTERS to ScreenInfo(
        "Character Library",
        "Wahusika wako wa kudumu.",
        "Planned (Phase 4)"
    ),
    Routes.ENVIRONMENTS to ScreenInfo(
        "Environment Library",
        "Mandhari na maeneo ya kutumia tena.",
        "Planned (Phase 4)"
    ),
    Routes.ANIMATION_STUDIO to ScreenInfo(
        "Animation Studio",
        "Eneo la 3D la kuhuisha wahusika na kamera.",
        "Planned (Phase 5)"
    ),
    Routes.VOICE_AUDIO to ScreenInfo(
        "Voice & Audio Studio",
        "Kurekodi sauti, mazungumzo na muziki.",
        "Planned (Phase 6)"
    ),
    Routes.MEDIA_LIBRARY to ScreenInfo(
        "Media Library",
        "Picha, sauti na modeli za 3D ulizoingiza.",
        "Planned (Phase 4)"
    ),
    Routes.TIMELINE to ScreenInfo(
        "Timeline Editor",
        "Kupanga matukio, sauti na kamera kwa muda.",
        "Planned (Phase 6)"
    ),
    Routes.EXPORT_CENTER to ScreenInfo(
        "Export Center",
        "Kutoa video ya MP4.",
        "Planned (Phase 7)"
    ),
    Routes.SETTINGS to ScreenInfo(
        "Settings",
        "Mipangilio ya app, lugha, mwonekano na hifadhi.",
        "Planned (Phase 1C)"
    ),
    Routes.HELP to ScreenInfo(
        "Help & Support",
        "Msaada na maelezo ya kutumia TOON.",
        "Planned"
    ),
    Routes.ABOUT to ScreenInfo(
        "About TOON",
        "TOON Studio, toleo 0.1.0.",
        "Planned"
    ),
    Routes.STUDIO to ScreenInfo(
        "Studio",
        "Kitovu cha zana zote za uzalishaji wa film.",
        "Planned (Phase 3)"
    ),
    Routes.ACCOUNT to ScreenInfo(
        "Account",
        "Wasifu na mipangilio ya akaunti.",
        "Planned"
    ),
    Routes.SEARCH to ScreenInfo(
        "Search",
        "Tafuta miradi, wahusika na mafaili.",
        "Planned"
    ),
    Routes.NOTIFICATIONS to ScreenInfo(
        "Notifications",
        "Taarifa za app zitaonekana hapa.",
        "Planned"
    )
)

fun screenTitle(route: String?): String =
    screens[route]?.title ?: "TOON"

@Composable
fun ScreenContent(route: String) {
    val info = screens[route] ?: ScreenInfo("TOON", "Skrini haipatikani.", "Unknown")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(info.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(info.description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "Hali: ${info.status}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
