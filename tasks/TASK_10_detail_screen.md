# Task 10: DetailScreen

> **Depends on:** Task 6
> **Status:** [x]

---

## Description

Create `ui/screens/detail/DetailViewModel.kt` and `ui/screens/detail/DetailScreen.kt`.

### ViewModel

- Receives `productId: String` via Hilt's `SavedStateHandle`.
- Exposes:
  ```kotlin
  val product: StateFlow<ProductKind?>
  val groups: StateFlow<List<ProductGroup>>   // for group picker dropdown
  val barcodes: StateFlow<List<Barcode>>      // current barcode list
  val isSaving: StateFlow<Boolean>
  ```
- Functions:
  ```kotlin
  fun updateName(name: String)
  fun updateGroup(groupId: String?)
  fun updateCurrentStock(value: Int)
  fun updateMinimumStock(value: Int)
  fun addBarcode(barcodeNumber: String)
  fun deleteBarcode(barcode: Barcode)
  fun saveChanges()   // calls repository.updateProductKind with current field values
  ```

### Screen layout

- Full-page `Scaffold` with `TopAppBar` ("Product Detail") and Back arrow.
- Fields:
  - `OutlinedTextField` for `name` (live edit, saves on `saveChanges`).
  - `ExposedDropdownMenuBox` for `group` (shows all active `ProductGroup` names).
  - Integer stepper or `OutlinedTextField` for `currentStock`.
  - Integer stepper or `OutlinedTextField` for `minimumStock`.
  - `image_path` preview: if non-null, display thumbnail with `AsyncImage` (Coil). If null, show placeholder.
  - Barcode list: chips with delete button; `+` button opens input dialog to add a new barcode string.
- `"Save"` FAB or `TopAppBar` action.
- **Accessibility:** All steppers and fields have content descriptions.
- **Absolute Mutability Policy:** This screen is always fully editable regardless of `household.current_state`.

### Coil dependency

Add to `libs.versions.toml`:

```toml
[versions]
coil = "3.2.0"

[libraries]
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
```

Add to `app/build.gradle.kts`:

```kotlin
implementation(libs.coil.compose)
```

### Local file URI handling for `AsyncImage`

`image_path` stores a raw file-system path (e.g., `/data/user/0/de.curlybracket.grocery/cache/photos/img_123.jpg`). Convert to a content URI before passing to Coil:

```kotlin
val imageUri = remember(product.imagePath) {
  product.imagePath?.let { path ->
    FileProvider.getUriForFile(
      context,
      "${BuildConfig.APPLICATION_ID}.fileprovider",
      File(path)
    )
  }
}
AsyncImage(model = imageUri, contentDescription = "Product image")
```

---

## Review Criteria

- `product` is driven by `repository.watchProductKind(productId)` — live stream.
- Saving calls `repository.updateProductKind`; `recalculateQuantityToBuy` is triggered inside the repository (not the ViewModel).
- Screen is navigable from all three main screens.
- Back navigation works correctly (pops back stack).
