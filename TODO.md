# TODO

- [/] Replace foreground service with workmanager
- [/] add dark theme
- [/] AudioFeedback: Use domain model and ENUMs for sounds loaded instead of named fields to be more generic
- [/] Foreground service
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
- [ ] OpenFoodFactsClient: Introduce an interface for client and models to prepare for future replacement of OpenFoodFactsClient with another API
- [ ] Research different barcode APIs for german products. Starting points:
    - Produktsuche.info API: Access: Completely free, no registration or API key required https://produktsuche.info/api/?gtin={EAN}.
    - UPCItemDB: Access: Official REST API, 100 queries per day free of charge (without credit card). Content: name, brand, Google category taxonomy, image URLs, store lists (good EU coverage).
    - EAN-Search.org: Access: Official API with a small, free trial quota for developers. Content: name, category, manufacturer, images.
- [/] ScannerProcessor
    - [/] Refactor with ScannerMode: Don't `when/is` in ScannerProcessor and decide about method to call on Repository but add a polymorph command mechanism encapsulating this part
    - [ ] add barcode in detail screen by scanning it
- [/] xml/backup_rules.xml && xml/data_extraction_rules.xml
- [/] split classes into files
- [/] product delete as button
- [/] add more padding to bottom for FAB
