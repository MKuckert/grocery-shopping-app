package de.curlybracket.grocery.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OFResponse(
    val status: String,
    val product: OFProduct? = null,
)

@Serializable
data class OFProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("brands") val brands: String? = null,
)
