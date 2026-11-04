package com.toon.studio.navigation

object Routes {
    const val HOME = "home"
    const val CREATE_FILM = "create_film"
    const val PROJECTS = "projects"
    const val COMMAND_STUDIO = "command_studio"
    const val CHARACTERS = "characters"
    const val ENVIRONMENTS = "environments"
    const val ANIMATION_STUDIO = "animation_studio"
    const val VOICE_AUDIO = "voice_audio"
    const val MEDIA_LIBRARY = "media_library"
    const val TIMELINE = "timeline"
    const val EXPORT_CENTER = "export_center"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val ABOUT = "about"
    const val STUDIO = "studio"
    const val ACCOUNT = "account"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
}

data class NavItem(val route: String, val label: String)

// Settings is intentionally NOT listed here: it is reachable only from
// the gear icon in the top bar.
val drawerItems = listOf(
    NavItem(Routes.HOME, "Home"),
    NavItem(Routes.CREATE_FILM, "Create New Film"),
    NavItem(Routes.PROJECTS, "My Projects"),
    NavItem(Routes.COMMAND_STUDIO, "Command Studio"),
    NavItem(Routes.CHARACTERS, "Character Library"),
    NavItem(Routes.ENVIRONMENTS, "Environment Library"),
    NavItem(Routes.ANIMATION_STUDIO, "Animation Studio"),
    NavItem(Routes.VOICE_AUDIO, "Voice & Audio Studio"),
    NavItem(Routes.MEDIA_LIBRARY, "Media Library"),
    NavItem(Routes.TIMELINE, "Timeline Editor"),
    NavItem(Routes.EXPORT_CENTER, "Export Center"),
    NavItem(Routes.HELP, "Help & Support"),
    NavItem(Routes.ABOUT, "About TOON")
)
