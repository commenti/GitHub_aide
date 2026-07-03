package com.adobs.ide

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application subclass required by Hilt for dependency injection.
 * Must be the first class initialized on app startup.
 */
@HiltAndroidApp
class IdeApplication : Application()
