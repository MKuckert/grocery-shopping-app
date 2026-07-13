# TODO

- [/] Database schema has to include `created_at` and `updated_at` for every table to enable proper synchronization and conflict resolution.
- [/] `deleted_at IS NULL` wird nicht überall überprüft (`GroceryRepositoryImpl`)
- [ ] Replace foreground service with workmanager
- [/] Following sql is copied multiple times in `GroceryRepositoryImpl`:
      ```
      tx.execute(
          sql = "UPDATE product_kinds SET quantity_to_buy = MAX(0, minimum_stock - current_stock) WHERE id = ?",
          parameters = listOf(productId),
        )
        ```
- [/] lock to portrait only
- [ ] add dark screen
- [/] add deletion for products
- [/] remove signup screen (incl. `Route.SignUp`)
- [/] product details
    - [/] renaming/changing product immediately saves, no FAB necessary
    - [/] add creation of product groups
- [/] inventory
    - [/] scanning unknown barcode triggers adding to existing product
- [/] sign in has to be password manager fillup, currently only triggered for password
- [/] Camera screen overlaps bottom sheet
- [/] move all texts to strings.xml and add german translation
- [ ] Research different barcode api
- [ ] AudioFeedback: Use domain model and ENUMs for sounds loaded instead of named fields to be more generic
- [/] Add .editorconfig
- [ ] Foreground service
    - For apps targeting Android 14+ you must declare valid Foreground Service (FGS) types in the manifest and Play Console, providing descriptions, user impact, and a demo video justifying their use based on user-initiated, perceptible actions.
          Dos:
          Run FGS only for as long as necessary to complete the task.
          Ensure FGS provides a user-beneficial core app feature, is initiated by the user, is visible in notifications or is user perceptible (for example, audio from playing a song).
          Submit a declaration form in your Play Console if targeting Android 14+ and describe the use case for each Foreground Services (FGS) permission used. Ensure the appropriate FGS type is selected. Examples listed here.
          Don'ts:
          Use FGS if system management of your task doesn’t break the user experience in your app. Consider alternatives like WorkManager
          Declare invalid or inaccurate FGS types in your app’s manifest.
          Helpful Links:
          See Policy page: https://goo.gle/play-policy-foreground-service
          See Help Center article: https://goo.gle/play-help-foreground-service
          See Declaration form: https://goo.gle/play-permission-decl-form
- [ ] Add multiple household selection
- [ ] OpenFoodFactsClient: Move base url and user agent out of client and into constants collection. Make Version and E-Mail in User Agent separate. Use App Version for User Agent Version instead of separate field
- [ ] ScannerProcessor
    - [ ] Refactor with ScannerMode: Don't `when/is` in ScannerProcessor and decide about method to call on Repository but add a polymorph command mechanism encapsulating this part
    - [ ] add barcode in detail screen by scanning it
- [ ] xml/backup_rules.xml && xml/data_extraction_rules.xml
- [ ] split classes into files
- [ ] product delete as button
- [ ] add more pending to bottom for FAB
