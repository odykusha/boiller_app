package com.boiller.monitor.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DataRecord(
    @SerialName("timestamp")
    val timestamp: String,
    
    @SerialName("battery_soc")
    val batterySoc: Int,
    
    @SerialName("grid_load")
    val gridLoad: Int,
    
    @SerialName("home_load")
    val homeLoad: Int,
    
    @SerialName("grid_status")
    val gridStatus: Boolean
)
