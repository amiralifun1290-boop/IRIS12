package com.iris.assistant

import android.app.Application
import com.iris.assistant.data.db.AppDatabase
import com.iris.assistant.util.TextToSpeechHelper

class IrisApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val ttsHelper by lazy { TextToSpeechHelper(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: IrisApp
            private set
    }
}
