package com.rootapp

import android.app.Application
import com.rootapp.di.AppModule

/** Application entry point. Kept minimal; DI lives in com.rootapp.di.AppModule. */
class RootApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
    }
}
