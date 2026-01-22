package com.boiller.monitor.api

import com.google.gson.annotations.SerializedName

data class DataRecord(
    @SerializedName("timestamp")
    val timestamp: String,
    
    @SerializedName("battery_soc")
    val batterySoc: Int,
    
    @SerializedName("grid_load")
    val gridLoad: Int,
    
    @SerializedName("home_load")
    val homeLoad: Int,
    
    @SerializedName("grid_status")
    val gridStatus: Boolean
)
