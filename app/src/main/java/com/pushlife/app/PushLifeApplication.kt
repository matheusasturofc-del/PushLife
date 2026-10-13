package com.pushlife.app

import android.app.Application
import com.pushlife.app.data.BankRepository
import com.pushlife.app.data.BlockSettingsRepository
import com.pushlife.app.data.ProfileRepository
import com.pushlife.app.data.ProgressRepository

class PushLifeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.bank = BankRepository(this)
        AppGraph.profile = ProfileRepository(this)
        AppGraph.blocking = BlockSettingsRepository(this)
        AppGraph.progress = ProgressRepository(this)
    }
}

/** Objetos compartilhados pelo app inteiro. */
object AppGraph {
    lateinit var bank: BankRepository
    lateinit var profile: ProfileRepository
    lateinit var blocking: BlockSettingsRepository
    lateinit var progress: ProgressRepository
}
