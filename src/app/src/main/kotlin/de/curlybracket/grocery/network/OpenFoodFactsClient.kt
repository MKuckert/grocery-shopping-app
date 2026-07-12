package de.curlybracket.grocery.network

import android.content.Context
import co.touchlab.kermit.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import de.curlybracket.grocery.R
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class OpenFoodFactsClient @Inject constructor(
    @param:Named("offs") private val httpClient: HttpClient,
    @ApplicationContext private val context: Context,
) {
    private val baseUrl = "https://world.openfoodfacts.org/api/v3.6/product"
    private val userAgent = "GroceryShoppingApp/0.1.0 (grocery@curlybracket.de)"
  private fun rateLimitDelay(): Duration = 2_000.milliseconds

    suspend fun lookupBarcode(barcode: String): OFResult {
        return try {
            val result = attemptLookup(barcode)
            if (result is OFResult.RateLimit) {
                delay(rateLimitDelay())
                attemptLookup(barcode).let {
                    if (it is OFResult.RateLimit) OFResult.Miss else it
                }
            } else result
        } catch (e: Exception) {
            Logger.w("Open Food Facts lookup failed", e)
            OFResult.Miss
        }
    }

  private suspend fun attemptLookup(barcode: String): OFResult {
        val response = httpClient.get("$baseUrl/$barcode.json") {
            header(HttpHeaders.UserAgent, userAgent)
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.body<OFResponse>()
                if (body.status == "ok") {
                    val name = body.product?.productName?.takeIf { it.isNotBlank() }
                        ?: body.product?.brands?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.scanner_unknown_item)
                    OFResult.Hit(name)
                } else OFResult.Miss
            }
            HttpStatusCode.ServiceUnavailable -> OFResult.RateLimit
            else -> OFResult.Miss
        }
    }
}

sealed class OFResult {
    data class Hit(val productName: String) : OFResult()
    data object Miss : OFResult()
    internal data object RateLimit : OFResult()
}
