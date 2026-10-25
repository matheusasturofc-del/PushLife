package com.pushlife.app.core

// flexão com o celular no chão: rosto maior = desceu (relativo ao "lá em cima", que ele aprende)
// rosto sumir de perto também vale; quem desce pouco, o limite se ajusta em 1-2 tentativas
class FacePushupCounter(
    private val downRatio: Float = 1.35f,
    private val lostWhileCloseRatio: Float = 1.2f,
    private val smoothing: Float = 0.5f,
    private val minRepMs: Long = 400,
    private val partialRatio: Float = 1.12f,
    private val minRangeRatio: Float = 1.18f,
) {
    enum class Phase { WAITING, UP, DOWN }

    var count = 0
        private set
    var phase = Phase.WAITING
        private set

    // 0 = em cima, 1 = embaixo (pra barra)
    var depth = 0f
        private set

    // última meia flexão, pro "Desça mais!"
    var lastPartialMs = Long.MIN_VALUE / 2
        private set

    private var top = 0f
    private var size: Float? = null
    private var lastRepAt = Long.MIN_VALUE / 2
    private var peak = 0f

    // até onde a pessoa costuma descer, aprende a cada ciclo
    private var bottom: Float? = null

    private val downThreshold: Float
        get() {
            val fixed = top * downRatio
            val learned = bottom ?: return fixed
            return minOf(fixed, maxOf(top * minRangeRatio, top + 0.6f * (learned - top)))
        }

    // "voltou pra cima" = 35% do caminho até o limite
    private val upThreshold: Float
        get() = top + 0.35f * (downThreshold - top)

    // faceSize 0..1 (null = sem rosto); true quando contou uma rep
    fun update(faceSize: Float?, timeMs: Long): Boolean {
        if (faceSize == null || faceSize <= 0f) {
            val last = size
            if (phase == Phase.UP && last != null && last >= top * lostWhileCloseRatio) {
                phase = Phase.DOWN
                peak = maxOf(peak, last)
                depth = 1f
            }
            size = null
            return false
        }

        val s = size?.let { it + smoothing * (faceSize - it) } ?: faceSize
        size = s

        var counted = false
        when (phase) {
            Phase.WAITING -> {
                top = s
                peak = s
                phase = Phase.UP
            }
            Phase.UP -> {
                peak = maxOf(peak, s)
                if (s >= downThreshold) {
                    phase = Phase.DOWN
                } else if (s < top) {
                    top = s // subiu mais do que antes: novo "lá em cima"
                    peak = s
                } else if (peak >= top * partialRatio && s <= upThreshold) {
                    lastPartialMs = timeMs // desceu só um pouco e voltou
                    learn(peak)
                    peak = s
                } else {
                    top += 0.02f * (s - top) // acompanha devagar se a pessoa mudar de posição
                }
            }
            Phase.DOWN -> {
                peak = maxOf(peak, s)
                if (s <= upThreshold) {
                    phase = Phase.UP
                    learn(peak)
                    peak = s
                    if (timeMs - lastRepAt >= minRepMs) {
                        count++
                        lastRepAt = timeMs
                        counted = true
                    }
                }
            }
        }
        depth = if (phase == Phase.DOWN) 1f else ((s - top) / (downThreshold - top)).coerceIn(0f, 1f)
        return counted
    }

    private fun learn(cyclePeak: Float) {
        bottom = bottom?.let { it + 0.5f * (cyclePeak - it) } ?: cyclePeak
    }

    fun reset() {
        count = 0
        phase = Phase.WAITING
        depth = 0f
        top = 0f
        size = null
        lastRepAt = Long.MIN_VALUE / 2
        peak = 0f
        bottom = null
        lastPartialMs = Long.MIN_VALUE / 2
    }
}
