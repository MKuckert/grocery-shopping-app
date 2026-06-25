package de.curlybracket.grocery.data.db

import com.powersync.db.schema.*
import de.curlybracket.grocery.powersync.listsTable
import de.curlybracket.grocery.powersync.todosTable

val AppSchema = Schema(
  tables = listOf(
    // --- Grocery domain tables ---
    Table(
      name = "households",
      columns = listOf(
        Column.text("current_state"),
        Column.text("shopping_started_at")
      )
    ),
    Table(
      name = "product_groups",
      columns = listOf(
        Column.text("household_id"),
        Column.text("name"),
        Column.text("deleted_at")
      )
    ),
    Table(
      name = "product_kinds",
      columns = listOf(
        Column.text("household_id"),
        Column.text("group_id"),
        Column.text("name"),
        Column.integer("current_stock"),
        Column.integer("minimum_stock"),
        Column.integer("quantity_to_buy"),
        Column.integer("pending_stock"),
        Column.text("image_path"),
        Column.integer("unload_open"),
        Column.text("deleted_at")
      )
    ),
    Table(
      name = "barcodes",
      columns = listOf(
        Column.text("household_id"),
        Column.text("product_kind_id"),
        Column.text("barcode_number")
      )
    ),
    listsTable,
    todosTable,
  )
)
