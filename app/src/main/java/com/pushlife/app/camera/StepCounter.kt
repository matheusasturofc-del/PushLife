package com.pushlife.app.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.pushlife.app.core.steps.StepValidator
import kotlinx.coroutines.delay
import kotlin.math.sqrt

object StepSensor {
    fun isAvailable(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
}

// passos desde que a tela abriu, pelo sensor do próprio celular
// (conta até com a tela apagada; o valor chega quando o app volta)
@Composable
fun StepCounterEffect(onSteps: (Int) -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onSteps)
    DisposableEffect(Unit) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        var start: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val total = event.values.firstOrNull() ?: return
                val first = start ?: total.also { start = it }
                callback((total - first).toInt().coerceAtLeast(0))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { manager.unregisterListener(listener) }
    }
}

// caminhada conferida: passos do sensor + giro do celular + GPS (se a pessoa deixou)
// mantém o processador acordado enquanto a tela da caminhada está aberta, senão com a tela
// apagada os sensores param de mandar dados e não dá pra conferir
@SuppressLint("MissingPermission")
@Composable
fun WalkSensorsEffect(validator: StepValidator, useGps: Boolean, onChange: () -> Unit) {
    val context = LocalContext.current
    val changed by rememberUpdatedState(onChange)
    DisposableEffect(useGps) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PushLife:walk").apply { acquire(2 * 60 * 60 * 1000L) }
        var start: Float? = null
        // tempo do evento → ms desde o boot (mesma base do GPS)
        fun ms(nanos: Long) = nanos / 1_000_000
        val stepListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val total = event.values.firstOrNull() ?: return
                val first = start ?: total.also { start = it }
                validator.onSteps((total - first).toInt().coerceAtLeast(0), ms(event.timestamp))
                changed()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val gyroListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                validator.onRotation(sqrt(x * x + y * y + z * z), ms(event.timestamp))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let { sensors.registerListener(stepListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(gyroListener, it, SensorManager.SENSOR_DELAY_GAME) }

        val locations = context.getSystemService(LocationManager::class.java)
        val locationListener = LocationListener { loc ->
            if (loc.hasSpeed()) validator.onLocation(loc.speed, if (loc.hasAccuracy()) loc.accuracy else 99f, ms(loc.elapsedRealtimeNanos))
        }
        val gpsAllowed = useGps &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (gpsAllowed) {
            runCatching { locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, locationListener, Looper.getMainLooper()) }
        }
        onDispose {
            sensors.unregisterListener(stepListener)
            sensors.unregisterListener(gyroListener)
            if (gpsAllowed) locations.removeUpdates(locationListener)
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
    // fecha as janelas de 10 s mesmo sem passo novo
    LaunchedEffect(validator) {
        while (true) {
            delay(1_000)
            validator.tick(SystemClock.elapsedRealtime())
            changed()
        }
    }
}
