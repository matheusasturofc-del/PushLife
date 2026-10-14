package com.pushlife.app.core

/**
 * Conta flexões com o celular no chão, embaixo do rosto, usando só o tamanho do rosto na
 * câmera frontal: quanto mais perto (descendo), maior o rosto.
 *
 * Tudo é relativo ao tamanho do rosto "lá em cima" (braços esticados), que o contador aprende
 * sozinho; por isso funciona com qualquer altura de pessoa ou posição do celular.
 *
 * - Desceu: o rosto ficou [downRatio] vezes maior que lá em cima. Se o rosto some bem perto
 *   da câmera (fica grande demais para ser detectado), também conta como "desceu".
 * - Subiu: voltou perto do tamanho lá de cima. Aí conta 1 repetição.
 * - Calibração: o contador guarda até onde a pessoa costuma descer. Se for menos que
 *   [downRatio] (celular longe, braços longos…), o limite baixa para 60% dessa amplitude,
 *   nunca abaixo de [minRangeRatio]. Assim, depois de 1 ou 2 tentativas, ele passa a contar.
 */
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

    /** 0 = em cima, 1 = desceu o suficiente. Serve para a barra de profundidade. */
    var depth = 0f
        private set

    /** Quando foi a última meia flexão (desceu um pouco e voltou), para o aviso "Desça mais!". */
    var lastPartialMs = Long.MIN_VALUE / 2
        private set

    private var top = 0f
    private var size: Float? = null
    private var lastRepAt = Long.MIN_VALUE / 2
    private var peak = 0f

    /** Até onde a pessoa costuma descer (tamanho do rosto no fundo), aprendido a cada ciclo. */
    private var bottom: Float? = null

    private val downThreshold: Float
        get() {
            val fixed = top * downRatio
            val learned = bottom ?: return fixed
            return minOf(fixed, maxOf(top * minRangeRatio, top + 0.6f * (learned - top)))
        }

    /** Voltou "lá para cima": a 35% do caminho até o limite de descida. */
    private val upThreshold: Float
        get() = top + 0.35f * (downThreshold - top)

    /**
     * @param faceSize tamanho do rosto em relação à imagem (0..1), ou null se nenhum rosto foi visto.
     * @return true quando uma repetição acabou de ser contada.
     */
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
