package com.pushlife.app.core

/**
 * Decide o que fazer a cada "tique" (≈1 s) do serviço de bloqueio, a partir do app que está
 * na tela. Não sabe nada de Android: quem chama informa o app da frente, o saldo e a hora.
 *
 * - Entrou num app bloqueado com saldo: [Event.Entered] (o app avisa quanto tempo resta).
 * - Entrou sem saldo, ou o saldo acabou enquanto usava: [Event.Block].
 * - Enquanto fica num app bloqueado, o tempo vai para [pendingSeconds] para ser descontado.
 * - Durante uma emergência, nada é descontado nem bloqueado.
 */
class BlockerEngine(
    private val maxGapMs: Long = 5_000,
    private val reblockAfterMs: Long = 3_000,
) {
    sealed interface Event {
        data class Entered(val packageName: String, val remainingSeconds: Long) : Event
        data class Block(val packageName: String) : Event
        data class Left(val packageName: String) : Event
    }

    /** Segundos gastos que ainda não foram descontados do saldo. */
    var pendingSeconds = 0L
        private set

    private var current: String? = null
    private var lastTickMs = 0L
    private var carryMs = 0L
    private var lastBlockMs = Long.MIN_VALUE / 2

    fun tick(
        foreground: String?,
        blocked: Set<String>,
        nowMs: Long,
        availableSeconds: Long,
        emergencyUntilMs: Long,
    ): List<Event> {
        val events = mutableListOf<Event>()
        val previous = current
        val inBlocked = foreground != null && foreground in blocked
        if (previous != null && previous != foreground) events += Event.Left(previous)

        if (!inBlocked) {
            current = null
            lastTickMs = nowMs
            carryMs = 0
            return events
        }
        val app = foreground!!
        val emergency = nowMs < emergencyUntilMs

        if (previous != app) {
            current = app
            lastTickMs = nowMs
            carryMs = 0
            val remaining = availableSeconds - pendingSeconds
            events += if (remaining <= 0 && !emergency) block(app, nowMs) ?: return events else Event.Entered(app, remaining)
            return events
        }

        val elapsed = (nowMs - lastTickMs).coerceIn(0, maxGapMs) // celular dormiu: não cobra o buraco
        lastTickMs = nowMs
        if (emergency) return events

        carryMs += elapsed
        pendingSeconds += carryMs / 1000
        carryMs %= 1000
        if (availableSeconds - pendingSeconds <= 0) {
            pendingSeconds = availableSeconds.coerceAtLeast(0)
            block(app, nowMs)?.let { events += it }
        }
        return events
    }

    /** Entrega o gasto acumulado para ser salvo e zera o contador. */
    fun takePending(): Long = pendingSeconds.also { pendingSeconds = 0 }

    // Evita abrir a tela de bloqueio várias vezes enquanto ela ainda está aparecendo.
    private fun block(app: String, nowMs: Long): Event.Block? {
        if (nowMs - lastBlockMs < reblockAfterMs) return null
        lastBlockMs = nowMs
        return Event.Block(app)
    }
}
