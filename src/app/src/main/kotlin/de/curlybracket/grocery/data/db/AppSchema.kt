package de.curlybracket.grocery.data.db

import com.powersync.db.schema.*

// TODO (Task 2): Complete schema definition for grocery domain
// This is a temporary placeholder. The full schema with all tables
// (households, product_groups, product_kinds, barcodes) will be
// defined in Task 2: Update PowerSync Schema.

val AppSchema = Schema(
  tables = listOf(
    // --- Temporary demo schema ---
    // Will be replaced in Task 2
    Table(
      name = "households",
      columns = listOf(
        Column.text("current_state"),
        Column.text("shopping_started_at")
      )
    ),
  )
)
