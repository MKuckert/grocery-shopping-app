package de.curlybracket.grocery.di

import android.content.Context
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.connector.supabase.SupabaseConnector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dagger.hilt.components.SingletonComponent
import de.curlybracket.grocery.BuildConfig
import de.curlybracket.grocery.data.db.AppSchema
import de.curlybracket.grocery.data.repository.GroceryRepositoryImpl
import de.curlybracket.grocery.domain.repository.GroceryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Singleton
    @Provides
    fun provideSupabaseConnector(): SupabaseConnector = SupabaseConnector(
        powerSyncEndpoint = BuildConfig.POWERSYNC_URL,
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    )

    @Singleton
    @Provides
    fun providePowerSyncDatabase(
        @ApplicationContext context: Context,
    ): PowerSyncDatabase {
        val factory = DatabaseDriverFactory(context)
        return PowerSyncDatabase(factory = factory, schema = AppSchema, dbFilename = "grocery.db")
    }
}

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class RepositoryModule {

    @ActivityRetainedScoped
    @Binds
    internal abstract fun bindGroceryRepository(impl: GroceryRepositoryImpl): GroceryRepository
}
