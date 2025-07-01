package com.sau.uranai  // Update with your actual package name

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate

class UranAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Force light theme regardless of system settings
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Additional force for API 29+ (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setTheme(R.style.Theme_UranAi)
        }
    }
}