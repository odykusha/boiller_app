package com.boiller.monitor.shared.utils

import kotlinx.serialization.Serializable

@Serializable
data class LightChangeEvent(
    val timestamp: String,
    val hasLight: Boolean
)
