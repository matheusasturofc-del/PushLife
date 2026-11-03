package com.pushlife.app.core.steps

import kotlin.math.max

// por que um trecho de passos não contou
enum class StepReject {
    ARM_SWING, // o celular girou demais: balanço de braço
    TOO_FAST, // ritmo impossível de andar (sacudindo o celular)
    VEHICLE, // GPS rápido demais: carro, ônibus, moto
    NOT_MOVING, // GPS bom e parado no lugar
    TOO_SHORT, // menos de 20 s seguidos andando
}

// confere os passos do sensor em janelas de 10 s:
// 1) ritmo de caminhada de verdade, 20 s seguidos no mínimo
// 2) giro do celular (giroscópio): andar com o celular no bolso/mão gira pouco, balançar o braço gira muito
// 3) GPS (se a pessoa deixou): rápido demais = veículo; sinal bom e parado = não andou
// tempos em ms desde o boot (mesma base do sensor e do GPS)
class StepValidator(
    private val windowMs: Long = 10_000,
    private val minBoutWindows: Int = 2,
    private val minCadence: Float = 30f, // passos por minuto
    private val maxCadence: Float = 200f,
    private val armSwingRadS: Float = 3.5f, // giro médio acima disso = balanço de braço
    private val vehicleMps: Float = 3.0f, // ~11 km/h
    private val stillMps: Float = 0.3f,
    private val goodAccuracyM: Float = 15f,
    private val usableAccuracyM: Float = 25f,
) {
    var validSteps = 0
        private set
    var lastReject: StepReject? = null
        private set
    private val rejected = mutableMapOf<StepReject, Int>()
    val rejectedSteps: Map<StepReject, Int> get() = rejected
    val totalRejected: Int get() = rejected.values.sum()

    private var windowStart: Long? = null
    private var stepsAtWindowStart = 0
    private var lastTotal = 0
    private var rotationSum = 0.0
    private var rotationCount = 0
    private val speeds = mutableListOf<Float>()
    private var allAccurate = true

    // trecho andando ainda não confirmado (precisa de 20 s seguidos)
    private var pendingSteps = 0
    private var pendingWindows = 0
    private var inBout = false

    // total de passos do sensor desde o começo da caminhada
    fun onSteps(total: Int, timeMs: Long) {
        roll(timeMs)
        lastTotal = max(lastTotal, total)
    }

    // velocidade de giro do celular (rad/s, módulo do giroscópio)
    fun onRotation(radPerSec: Float, timeMs: Long) {
        roll(timeMs)
        rotationSum += radPerSec
        rotationCount++
    }

    // ponto do GPS: velocidade (m/s) e precisão (m)
    fun onLocation(speedMps: Float, accuracyM: Float, timeMs: Long) {
        roll(timeMs)
        if (accuracyM > usableAccuracyM) return
        speeds += speedMps
        if (accuracyM > goodAccuracyM) allAccurate = false
    }

    // chamado de tempos em tempos (fecha as janelas que já passaram)
    fun tick(timeMs: Long) = roll(timeMs)

    // fim da caminhada: fecha a janela atual e descarta o que não chegou a 20 s
    fun finish(timeMs: Long) {
        roll(timeMs)
        val start = windowStart ?: return
        if (timeMs - start >= windowMs / 2) closeWindow(timeMs - start)
        flushPending()
    }

    private fun roll(timeMs: Long) {
        val start = windowStart
        if (start == null) {
            windowStart = timeMs
            return
        }
        var s: Long = start
        while (timeMs - s >= windowMs) {
            closeWindow(windowMs)
            s += windowMs
            windowStart = s
        }
    }

    private fun closeWindow(durationMs: Long) {
        val steps = lastTotal - stepsAtWindowStart
        stepsAtWindowStart = lastTotal
        val rotation = if (rotationCount > 0) (rotationSum / rotationCount).toFloat() else null
        val speed = speeds.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val gpsGood = allAccurate && speeds.isNotEmpty()
        rotationSum = 0.0
        rotationCount = 0
        speeds.clear()
        allAccurate = true

        if (steps <= 0) {
            // parou de andar: trecho curto não conta
            flushPending()
            return
        }
        val cadence = steps * 60_000f / durationMs
        val reason = when {
            cadence > maxCadence -> StepReject.TOO_FAST
            rotation != null && rotation > armSwingRadS -> StepReject.ARM_SWING
            speed != null && speed > vehicleMps -> StepReject.VEHICLE
            gpsGood && speed != null && speed < stillMps -> StepReject.NOT_MOVING
            cadence < minCadence -> StepReject.TOO_SHORT
            else -> null
        }
        if (reason != null) {
            flushPending()
            reject(reason, steps)
            return
        }
        if (inBout) {
            validSteps += steps
        } else {
            pendingSteps += steps
            pendingWindows++
            if (pendingWindows >= minBoutWindows) {
                validSteps += pendingSteps
                pendingSteps = 0
                pendingWindows = 0
                inBout = true
            }
        }
        lastReject = null
    }

    private fun flushPending() {
        if (pendingSteps > 0) reject(StepReject.TOO_SHORT, pendingSteps)
        pendingSteps = 0
        pendingWindows = 0
        inBout = false
    }

    private fun reject(reason: StepReject, steps: Int) {
        rejected[reason] = (rejected[reason] ?: 0) + steps
        lastReject = reason
    }
}
