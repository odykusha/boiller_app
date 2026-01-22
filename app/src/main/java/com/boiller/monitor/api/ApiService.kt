package com.boiller.monitor.api

import retrofit2.Response
import retrofit2.http.GET

interface ApiService {
    @GET("api/data")
    suspend fun getData(): Response<DataResponse>
    
    @GET("api/latest")
    suspend fun getLatest(): Response<DataRecord>
}
