package de.curlybracket.grocery.ui.navigation

sealed class Route(val path: String) {
  data object SignIn : Route("sign_in")
  data object Inventory : Route("inventory")
  data object Shopping : Route("shopping")
  data object Unloading : Route("unloading")
  data class Detail(val productId: String) : Route("detail/$productId") {
    companion object {
      const val TEMPLATE = "detail/{productId}"
    }
  }
}
