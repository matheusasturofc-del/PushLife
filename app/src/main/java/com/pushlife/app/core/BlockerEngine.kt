package com.pushlife.app.core

// roda a cada ~1 s com o app da frente, saldo e hora (nada de Android aqui)
// com saldo = Entered, sem saldo = Block; em emergência não cobra nem bloqueia
class BlockerEngine(
    private val maxGapMs: Long = 5_000,
    private val reblockAfterMs: Long = 3_000,
) {
    sealed interface Event {
        data class Entered(val packageName: String, val remainingSeconds: Long) : Event
        data class Block(val packageName: String) : Event
        data class Left(val packageName: String) : Event
    }

    // segundos gastos que ainda não saíram do saldo
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

    // entrega o gasto pra salvar e zera
    fun takePending(): Long = pendingSeconds.also { pendingSeconds = 0 }

    // não abre a tela de bloqueio de novo enquanto ela tá aparecendo
    private fun block(app: String, nowMs: Long): Event.Block? {
        if (nowMs - lastBlockMs < reblockAfterMs) return null
        lastBlockMs = nowMs
        return Event.Block(app)
    }
}
