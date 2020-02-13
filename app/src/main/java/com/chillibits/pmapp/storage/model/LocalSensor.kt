/*
 * Copyright © Marc Auberer 2017 - 2020. All rights reserved
 */

package com.chillibits.pmapp.storage.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor")
data class LocalSensor(
    @PrimaryKey
    @ColumnInfo(name = "chip_id") val chipId: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "color") val color: Int
)