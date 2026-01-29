package com.boiller.monitor.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class DataResponse(
    val data: List<DataRecord>,
    val count: Int
)
