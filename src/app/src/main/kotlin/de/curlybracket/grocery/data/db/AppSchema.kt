package de.curlybracket.grocery.data.db

import com.powersync.db.schema.*

val AppSchema = Schema(
  tables = listOf(
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
      ),
      indexes = listOf(
        Index(
          name = "idx_product_kinds_active_group",
          columns = listOf(
            IndexedColumn.ascending("household_id"),
            IndexedColumn.ascending("group_id")
          )
        ),
        Index(
          name = "idx_product_kinds_shopping_state",
          columns = listOf(
            IndexedColumn.ascending("household_id"),
            IndexedColumn.ascending("quantity_to_buy"),
            IndexedColumn.ascending("pending_stock")
          )
        )
      )
    ),
    Table(
      name = "barcodes",
      columns = listOf(
        Column.text("household_id"),
        Column.text("product_kind_id"),
        Column.text("barcode_number")
      ),
      indexes = listOf(
        Index(
          name = "idx_barcodes_lookup",
          columns = listOf(
            IndexedColumn.ascending("household_id"),
            IndexedColumn.ascending("barcode_number")
          )
        )
      )
    ),
  )
)
