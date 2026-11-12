package com.yourname.buggame

import android.app.Application
import com.yourname.buggame.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BugGameApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BugGameApp)
            modules(appModule)
        }
    }
}
