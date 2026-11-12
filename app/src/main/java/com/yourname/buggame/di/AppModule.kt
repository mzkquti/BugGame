package com.yourname.buggame.di

import com.yourname.buggame.GameSettings
import com.yourname.buggame.data.AppDatabase
import com.yourname.buggame.game.GameViewModel
import com.yourname.buggame.gold.GoldRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Все зависимости приложения в одном месте (Koin)
val appModule = module {
    single { GameSettings(androidContext()) }
    single { GoldRepository(androidContext()) }
    single { AppDatabase.get(androidContext()) }
    viewModel { GameViewModel(get(), get(), get()) }
}
