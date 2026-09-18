package com.iris.assistant.util

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager

object FlashlightHelper {
    private var isOn = false

    fun toggle(context: Context): Boolean {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cm.cameraIdList[0]
            isOn = !isOn
            cm.setTorchMode(cameraId, isOn)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return isOn
    }
}
