package de.curlybracket.grocery.data.db

import com.powersync.db.schema.*

val AppSchema = Schema(
  tables = listOf(
    Table(
      name = "households",
      columns = listOf(
        Column("id", ColumnType.TEXT),
        Column("current_state", ColumnType.TEXT),
        Column("shopping_started_at", ColumnType.TEXT)
      )
    ),
    Table(
      name = "product_groups",
      columns = listOf(
        Column("id", ColumnType.TEXT),
        Column("household_id", ColumnType.TEXT),
        Column("name", ColumnType.TEXT),
        Column("deleted_at", ColumnType.TEXT)
      )
    ),
    Table(
      name = "product_kinds",
      columns = listOf(
        Column("id", ColumnType.TEXT),
        Column("household_id", ColumnType.TEXT),
        Column("group_id", ColumnType.TEXT),
        Column("name", ColumnType.TEXT),
        Column("current_stock", ColumnType.INTEGER),
        Column("minimum_stock", ColumnType.INTEGER),
        Column("quantity_to_buy", ColumnType.INTEGER),
        Column("pending_stock", ColumnType.INTEGER),
        Column("image_path", ColumnType.TEXT),
        Column("unload_open", ColumnType.INTEGER),
        Column("deleted_at", ColumnType.TEXT)
      )
    ),
    Table(
      name = "barcodes",
      columns = listOf(
        Column("id", ColumnType.TEXT),
        Column("household_id", ColumnType.TEXT),
        Column("product_kind_id", ColumnType.TEXT),
        Column("barcode_number", ColumnType.TEXT)
      )
    )
  )
)
