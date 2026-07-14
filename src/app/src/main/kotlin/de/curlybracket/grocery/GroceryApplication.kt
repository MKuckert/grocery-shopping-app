package de.curlybracket.grocery

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.curlybracket.grocery.sync.SyncLifecycleManager
import javax.inject.Inject

@HiltAndroidApp
class GroceryApplication : Application() {

    @Inject lateinit var syncLifecycleManager: SyncLifecycleManager

    override fun onCreate() {
        super.onCreate()
        syncLifecycleManager.initialize()
    }
}
