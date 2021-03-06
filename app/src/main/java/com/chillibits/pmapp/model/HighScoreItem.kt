/*
 * Copyright © Marc Auberer 2017 - 2020. All rights reserved
 */

package com.chillibits.pmapp.model

import kotlinx.serialization.Serializable

@Serializable
data class HighScoreItem (
    val country: String,
    val city: String,
    val sensors: Int
)

@Serializable
data class HighScoreList (
    val items: List<HighScoreItem>
)