package dk.bandit.skema.data

import io.ktor.client.HttpClient

/** One engine per platform, because Ktor has no common one. */
expect fun httpClient(): HttpClient
