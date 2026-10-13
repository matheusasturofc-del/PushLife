package com.pushlife.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectionTest {
    @Test
    fun cincoHorasPorDiaDao76DiasPorAno() {
        assertEquals(76, Projection.daysPerYear(5f)) // 5 × 365 ÷ 24 = 76,04
    }

    @Test
    fun recuperaADiferencaEntreHojeEAMeta() {
        assertEquals(30, Projection.daysRecovered(5f, 3f)) // 2 × 365 ÷ 24 = 30,4
        assertEquals(0, Projection.daysRecovered(3f, 5f), "meta maior que o uso atual não recupera nada")
    }

    @Test
    fun metaSugeridaNuncaFicaAbaixoDeMeiaHora() {
        assertEquals(3f, Projection.suggestedTarget(5f), 0f)
        assertEquals(0.5f, Projection.suggestedTarget(1f), 0f)
    }

    @Test
    fun metaDiariaCresceComAFrequencia() {
        val metas = ExerciseFrequency.entries.map { it.dailyGoal }
        assertEquals(metas.sorted(), metas)
    }

    private fun assertEquals(expected: Int, actual: Int, message: String) = org.junit.Assert.assertEquals(message, expected, actual)
}
