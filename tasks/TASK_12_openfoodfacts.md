# Task 12: Open Food Facts Integration (`network/`)

> **Depends on:** Task 5 (Hilt DI)
> **Status:** [ ]

---

## Description

Create `network/OpenFoodFactsClient.kt` and `network/OFModels.kt`.

### Dedicated `HttpClient` for Open Food Facts

Do NOT reuse the Supabase internal Ktor client. Create a separate singleton in `di/AppModule.kt`:

```kotlin
@Provides
@Singleton
@Named("offs")
fun provideOpenFoodFactsHttpClient(): HttpClient = HttpClient(Android) {
  install(ContentNegotiation) {
    json(Json { ignoreUnknownKeys = true; isLenient = true })
  }
  install(HttpTimeout) {
    requestTimeoutMillis = 10_000
    connectTimeoutMillis = 10_000
    socketTimeoutMillis = 10_000
  }
}
```

### New dependencies

Supabase-kt 3.x uses Ktor 3.x. Add to `libs.versions.toml`:

```toml
[versions]
ktor = "3.1.3"   # must match Supabase-kt 3.6.0 transitive dep; verify with ./gradlew dependencies | grep ktor

[libraries]
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json          = { module = "io.ktor:ktor-serialization-kotlinx-json",  version.ref = "ktor" }
```

Add both to `app/build.gradle.kts`.

---

### `network/OFModels.kt`

```kotlin
@Serializable
data class OFResponse(
  val status: String,      // "ok" or "not found"
  val product: OFProduct? = null
)

@Serializable
data class OFProduct(
  @SerialName("product_name") val productName: String? = null,
  @SerialName("brands")       val brands: String? = null
)
```

---

### `network/OpenFoodFactsClient.kt`

```kotlin
class OpenFoodFactsClient @Inject constructor(
  @Named("offs") private val httpClient: HttpClient
) {
  private val baseUrl   = "https://world.openfoodfacts.org/api/v3.6/product"
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
      200  -> {
        val body = response.body<OFResponse>()
        if (body.status == "ok") {
          val name = body.product?.productName?.takeIf { it.isNotBlank() }
            ?: body.product?.brands?.takeIf { it.isNotBlank() }
            ?: "Unknown Item"
          OFResult.Hit(name)
        } else OFResult.Miss
      }
      503  -> OFResult.RateLimit
      else -> OFResult.Miss
    }
  }
}

sealed class OFResult {
  data class Hit(val productName: String) : OFResult()
  data object Miss : OFResult()
  internal data object RateLimit : OFResult()  // internal; resolved in lookupBarcode
}
```

### Downstream behaviour

- `OFResult.Miss` or exception: `prefillName = "Unknown Item"` → `ScannerState.CaptureRequired`; Save button is disabled until photo captured.
- `OFResult.Hit`: `prefillName = productName` → `ScannerState.CaptureRequired`; Save button is immediately enabled (shutter optional).

---

## Review Criteria

- Dedicated `@Named("offs")` `HttpClient` with `ContentNegotiation` and `HttpTimeout` plugins.
- Custom `User-Agent` header on every request.
- 503 triggers a single 2 s delayed retry; further 503 → `OFResult.Miss`.
- All exceptions caught inside `lookupBarcode`; never propagate to UI.
- `OFResult.Miss` path: Save button disabled until photo captured.
