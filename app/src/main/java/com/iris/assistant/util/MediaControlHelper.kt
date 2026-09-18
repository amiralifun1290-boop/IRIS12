package com.iris.assistant.util

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

object MediaControlHelper {
    fun playPause(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.isMusicActive) {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else {
            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    fun next(context: Context) = dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous(context: Context) = dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(android.content.Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        context.sendOrderedBroadcast(intent, null)
    }
}
