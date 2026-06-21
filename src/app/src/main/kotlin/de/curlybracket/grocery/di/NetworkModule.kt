package de.curlybracket.grocery.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import de.curlybracket.grocery.BuildConfig
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

  @Singleton
  @Provides
  fun provideSupabaseClient(): SupabaseClient {
    val url = BuildConfig.SUPABASE_URL
    val anonKey = BuildConfig.SUPABASE_ANON_KEY

    require(url.isNotBlank()) { "SUPABASE_URL is not configured in local.properties" }
    require(anonKey.isNotBlank()) { "SUPABASE_ANON_KEY is not configured in local.properties" }

    return createSupabaseClient(
      supabaseUrl = url,
      supabaseKey = anonKey
    ) {
      install(Auth)
    }
  }
}
