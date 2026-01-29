package com.boiller.monitor.shared.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ApiService(private val baseUrl: String) {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    suspend fun getData(): DataResponse {
        return client.get("$baseUrl/api/data").body()
    }
    
    suspend fun getLatest(): DataRecord {
        return client.get("$baseUrl/api/latest").body()
    }
}
