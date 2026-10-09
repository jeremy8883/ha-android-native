package net.jeremycasey.homeassistantnative.login

import android.content.Context
import android.net.nsd.NsdManager
import androidx.core.content.getSystemService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface LoginModule {
    companion object {
        @Provides
        @Singleton
        fun providesNsdManager(@ApplicationContext context: Context): NsdManager =
            checkNotNull(context.getSystemService<NsdManager>()) { "Impossible to get NsdManager" }
    }

    @Binds
    @Singleton
    fun bindsHomeAssistantSearcher(impl: HomeAssistantSearcherImpl): HomeAssistantSearcher
}
