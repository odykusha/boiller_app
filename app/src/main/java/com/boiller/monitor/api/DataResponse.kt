package com.boiller.monitor.api

data class DataResponse(
    val data: List<DataRecord>,
    val count: Int
)
