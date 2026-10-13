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
 * - Subiu: voltou para até [upRatio] vezes o tamanho lá de cima. Aí conta 1 repetição.
 */
class FacePushupCounter(
    private val downRatio: Float = 1.45f,
    private val upRatio: Float = 1.15f,
    private val lostWhileCloseRatio: Float = 1.25f,
    private val smoothing: Float = 0.5f,
    private val minRepMs: Long = 400,
) {
    enum class Phase { WAITING, UP, DOWN }

    var count = 0
        private set
    var phase = Phase.WAITING
        private set

    /** 0 = em cima, 1 = desceu o suficiente. Serve para a barra de profundidade. */
    var depth = 0f
        private set

    private var top = 0f
    private var size: Float? = null
    private var lastRepAt = Long.MIN_VALUE / 2

    /**
     * @param faceSize largura do rosto em relação à imagem (0..1), ou null se nenhum rosto foi visto.
     * @return true quando uma repetição acabou de ser contada.
     */
    fun update(faceSize: Float?, timeMs: Long): Boolean {
        if (faceSize == null || faceSize <= 0f) {
            val last = size
            if (phase == Phase.UP && last != null && last >= top * lostWhileCloseRatio) {
                phase = Phase.DOWN
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
                phase = Phase.UP
            }
            Phase.UP -> {
                if (s >= top * downRatio) {
                    phase = Phase.DOWN
                } else if (s < top) {
                    top = s // subiu mais do que antes: novo "lá em cima"
                } else {
                    top += 0.02f * (s - top) // acompanha devagar se a pessoa mudar de posição
                }
            }
            Phase.DOWN -> {
                if (s <= top * upRatio) {
                    phase = Phase.UP
                    if (timeMs - lastRepAt >= minRepMs) {
                        count++
                        lastRepAt = timeMs
                        counted = true
                    }
                }
            }
        }
        depth = if (top > 0f) ((s / top - 1f) / (downRatio - 1f)).coerceIn(0f, 1f) else 0f
        if (phase == Phase.DOWN) depth = 1f
        return counted
    }

    fun reset() {
        count = 0
        phase = Phase.WAITING
        depth = 0f
        top = 0f
        size = null
        lastRepAt = Long.MIN_VALUE / 2
    }
}
