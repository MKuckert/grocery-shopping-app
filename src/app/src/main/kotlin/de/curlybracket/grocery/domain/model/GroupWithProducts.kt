package de.curlybracket.grocery.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class GroupWithProducts(
  val groupName: String,
  val products: List<ProductKind>
)

@Immutable
data class ProductWithGroup(
  val product: ProductKind,
  val groupName: String?
)
