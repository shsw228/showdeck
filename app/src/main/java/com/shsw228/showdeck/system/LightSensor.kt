package com.shsw228.showdeck.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/**
 * 照度センサ（lux）の値を流す。
 *
 * 実機には `android.sensor.light`（on-change / non-wakeUp）が載っている。
 * 消灯中でも Android 側は Awake のままなので、非 wakeUp センサでも値は届く。
 *
 * on-change センサなので、値が変わらない限りイベントは来ない。
 * 「明かりが点いた」という変化を捉える用途にはこの性質がちょうど合う。
 */
fun lightSensorFlow(context: Context): Flow<Float> {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val sensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return flowOf()

    return callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let { trySend(it) }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        // 消灯の解除判定にしか使わないので、最も遅いレートで十分。
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }
}
