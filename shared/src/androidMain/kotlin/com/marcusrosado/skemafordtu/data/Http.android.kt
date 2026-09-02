package com.marcusrosado.skemafordtu.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

actual fun httpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 45_000
        socketTimeoutMillis = 45_000
    }
    followRedirects = true
}
