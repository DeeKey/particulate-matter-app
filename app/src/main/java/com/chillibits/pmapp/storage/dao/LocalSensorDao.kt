/*
 * Copyright © Marc Auberer 2017 - 2020. All rights reserved
 */

package com.chillibits.pmapp.storage.dao

import androidx.room.Dao
import androidx.room.Query
import com.chillibits.pmapp.model.Sensor
import com.chillibits.pmapp.storage.model.LocalSensor

@Dao
interface LocalSensorDao {
    @Query("SELECT * FROM sensor")
    fun getAll(): List<LocalSensor>

    @Query("SELECT * FROM sensor WHERE chip_id = (:chipId) LIMIT 1")
    fun getSensor(chipId: Int): Sensor
}