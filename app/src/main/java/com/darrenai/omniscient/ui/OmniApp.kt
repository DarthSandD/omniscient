package com.darrenai.omniscient.ui

import android.app.Application

/**
 * Application class: intentionally minimal. No work in onCreate beyond super.
 * All initialization happens in Activities when needed.
 */
class OmniApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
