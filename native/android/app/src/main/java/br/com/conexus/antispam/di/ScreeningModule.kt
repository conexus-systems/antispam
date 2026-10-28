package br.com.conexus.antispam.di

import android.content.Context
import br.com.conexus.antispam.screening.ScreeningCallback
import br.com.conexus.antispam.screening.ScreeningCallbackImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScreeningModule {

    @Binds
    @Singleton
    abstract fun bindScreeningCallback(impl: ScreeningCallbackImpl): ScreeningCallback
}