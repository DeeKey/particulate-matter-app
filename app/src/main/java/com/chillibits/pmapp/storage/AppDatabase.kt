/*
 * Copyright © Marc Auberer 2017 - 2020. All rights reserved
 */

package com.chillibits.pmapp.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chillibits.pmapp.storage.dao.LocalSensorDao
import com.chillibits.pmapp.storage.model.LocalSensor

@Database(entities = arrayOf(LocalSensor::class), version = 1) // Increase version whenever the structure of the local db changes
abstract class AppDatabase : RoomDatabase() {
    abstract fun sensorDao(): LocalSensorDao
}