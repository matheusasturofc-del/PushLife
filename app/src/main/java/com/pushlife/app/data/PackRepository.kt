package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import com.pushlife.app.core.packs.Feedback
import com.pushlife.app.core.packs.Finish
import com.pushlife.app.core.packs.PackCatalog
import com.pushlife.app.core.packs.PackCodec
import com.pushlife.app.core.packs.PackId
import com.pushlife.app.core.packs.PackPlanner
import com.pushlife.app.core.packs.PackProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate

// progresso de cada pack (nível, agenda, treinos feitos e testes)
class PackRepository(context: Context, private val bank: BankRepository, private val profile: ProfileRepository) {
    private val prefs = context.getSharedPreferences("packs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Map<PackId, PackProgress>> = _state.asStateFlow()

    // já viu a tela "Como o corpo muda"?
    var learnSeen: Boolean
        get() = prefs.getBoolean(KEY_LEARN, false)
        set(value) = prefs.edit { putBoolean(KEY_LEARN, value) }

    // contar com a câmera nos packs (liga sozinha nos movimentos que ela confere)
    var useCamera: Boolean
        get() = prefs.getBoolean(KEY_CAMERA, true)
        set(value) = prefs.edit { putBoolean(KEY_CAMERA, value) }

    fun progress(id: PackId): PackProgress = _state.value[id] ?: PackProgress()

    @Synchronized
    fun start(id: PackId, today: LocalDate = LocalDate.now()) {
        val pack = PackCatalog.get(id)
        save(id, PackPlanner.start(pack, profile.state.value.exerciseFrequency, today, previous = _state.value[id]))
    }

    @Synchronized
    fun setDays(id: PackId, days: Set<DayOfWeek>) = save(id, progress(id).copy(days = days))

    // parar o programa: some da agenda, mas guarda nível e testes pra quando voltar
    @Synchronized
    fun stop(id: PackId) = save(id, progress(id).copy(startedOn = null))

    // terminou o treino: ajusta o nível e já põe os bônus no saldo
    // bônus de treino: um por dia, somando todos os packs (senão dava pra fazer 10 packs e ganhar 10 vezes)
    @Synchronized
    fun finish(id: PackId, feedback: Feedback, completion: Float, today: LocalDate = LocalDate.now()): Finish {
        val bonusAvailable = _state.value.values.none { it.doneOn(today) }
        val result = PackPlanner.finish(PackCatalog.get(id), progress(id), feedback, today, completion, bonusAvailable)
        if (!result.counted) return result
        save(id, result.progress)
        val bonus = result.workoutBonus + result.weekBonus
        if (bonus > 0) bank.addBonus(bonus)
        ProgressCenter.afterExercise()
        return result
    }

    @Synchronized
    fun recordTest(id: PackId, result: Int, today: LocalDate = LocalDate.now()) =
        save(id, PackPlanner.recordTest(PackCatalog.get(id), progress(id), result, today))

    private fun load(): Map<PackId, PackProgress> =
        PackId.entries.mapNotNull { id -> prefs.getString(id.name, null)?.let { id to PackCodec.decode(it) } }.toMap()

    private fun save(id: PackId, progress: PackProgress) {
        prefs.edit { putString(id.name, PackCodec.encode(progress)) }
        _state.value = _state.value + (id to progress)
    }

    private companion object {
        const val KEY_LEARN = "learn_seen"
        const val KEY_CAMERA = "use_camera"
    }
}
