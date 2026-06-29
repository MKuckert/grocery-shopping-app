package de.curlybracket.grocery.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.delay

class OpenFoodFactsClient @Inject constructor(
    @param:Named("offs") private val httpClient: HttpClient
) {
    private val baseUrl = "https://world.openfoodfacts.org/api/v3.6/product"
    private val userAgent = "GroceryShoppingApp/0.1.0 (grocery@curlybracket.de)"

    suspend fun lookupBarcode(barcode: String): OFResult {
        return try {
            val result = attemptLookup(barcode)
            if (result is OFResult.RateLimit) {
                delay(2_000)
                attemptLookup(barcode).let {
                    if (it is OFResult.RateLimit) OFResult.Miss else it
                }
            } else result
        } catch (e: Exception) {
            OFResult.Miss
        }
    }

    private suspend fun attemptLookup(barcode: String): OFResult {
        val response = httpClient.get("$baseUrl/$barcode.json") {
            header(HttpHeaders.UserAgent, userAgent)
        }
        return when (response.status.value) {
            200 -> {
                val body = response.body<OFResponse>()
                if (body.status == "ok") {
                    val name = body.product?.productName?.takeIf { it.isNotBlank() }
                        ?: body.product?.brands?.takeIf { it.isNotBlank() }
                        ?: "Unknown Item"
                    OFResult.Hit(name)
                } else OFResult.Miss
            }
            503 -> OFResult.RateLimit
            else -> OFResult.Miss
        }
    }
}

sealed class OFResult {
    data class Hit(val productName: String) : OFResult()
    data object Miss : OFResult()
    internal data object RateLimit : OFResult()  // internal; resolved in lookupBarcode
}
