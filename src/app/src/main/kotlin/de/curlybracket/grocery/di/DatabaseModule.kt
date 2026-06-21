package de.curlybracket.grocery.di

import android.content.Context
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.curlybracket.grocery.data.db.AppSchema
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Singleton
  @Provides
  fun providePowerSyncDatabase(
    @ApplicationContext context: Context
  ): PowerSyncDatabase {
    val driverFactory = DatabaseDriverFactory(context);
    val database =
      PowerSyncDatabase(factory = driverFactory, schema = AppSchema, dbFilename = "grocery.db")
    return database
  }
}

