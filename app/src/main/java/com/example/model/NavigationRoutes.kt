package com.example.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface StudioRoute {
    @Serializable
    data object Home : StudioRoute

    @Serializable
    data class Editor(val projectId: Long? = null) : StudioRoute

    @Serializable
    data object Effects : StudioRoute

    @Serializable
    data object Plugins : StudioRoute

    @Serializable
    data object Projects : StudioRoute

    @Serializable
    data object Settings : StudioRoute
}
