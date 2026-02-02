package com.example.pocu

import android.app.Application
import android.util.Log

class PocuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("PocuApplication", "App initialized successfully")
        
        // Set up uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("PocuCrash", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
