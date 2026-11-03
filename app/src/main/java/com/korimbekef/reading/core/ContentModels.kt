package com.korimbekef.reading.core

import kotlinx.serialization.Serializable

@Serializable
data class PassageJson(
    val id: String,
    val level: Int,
    val title: String,
    val emoji: String,
    val lines: List<String>,
)
