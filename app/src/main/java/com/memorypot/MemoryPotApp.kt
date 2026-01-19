package com.memorypot

import android.app.Application
import com.memorypot.data.AppDatabase

class MemoryPotApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.get(this) }
}
