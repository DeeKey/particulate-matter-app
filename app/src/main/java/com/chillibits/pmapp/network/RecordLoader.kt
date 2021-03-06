/*
 * Copyright © Marc Auberer 2017 - 2020. All rights reserved
 */

package com.chillibits.pmapp.network

import android.content.Context
import com.chillibits.pmapp.model.DataRecord
import com.chillibits.pmapp.model.DataRecordCompressedList
import com.chillibits.pmapp.tool.StorageUtils
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.readText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.collections.ArrayList

suspend fun loadDataRecords(context: Context, chipId: String, from: Long, to: Long): ArrayList<DataRecord>? {
    try{
        val client = getNetworkClient()
        val params = Parameters.build {
            append("id", chipId)
            append("from", (from / 1000).toString())
            append("to", (to / 1000).toString())
            append("minimize", "true")
            append("gps", "true")
        }
        val request = client.submitForm<HttpStatement>(getBackendDataUrl(context), params, encodeInQuery = true)
        val response = request.execute()
        client.close()
        if(response.status == HttpStatusCode.OK) {
            val records = ArrayList(Json.parse(DataRecordCompressedList.serializer(), response.readText()).items.map {
                DataRecord(Date(it.time * 1000), it.p1, it.p2, it.t, it.h, it.p / 100, it.la, it.ln, it.a)
            })
            StorageUtils(context).saveRecords(chipId, records)
            return records
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return ArrayList()
}