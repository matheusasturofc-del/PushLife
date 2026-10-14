package com.pushlife.app

import android.app.Application
import android.content.Context
import com.pushlife.app.data.Accounts
import com.pushlife.app.data.AppLanguage
import com.pushlife.app.data.BankRepository
import com.pushlife.app.data.BlockSettingsRepository
import com.pushlife.app.data.ProfileRepository
import com.pushlife.app.data.ProgressRepository
import java.util.Locale

class PushLifeApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Na versão das capturas de tela, datas e dias da semana também ficam em inglês.
        if (resources.getBoolean(R.bool.english_only)) Locale.setDefault(Locale.US)
        AppGraph.load(this)
        Accounts.init(this)
    }
}

/** Objetos compartilhados pelo app inteiro. */
object AppGraph {
    lateinit var bank: BankRepository
    lateinit var profile: ProfileRepository
    lateinit var blocking: BlockSettingsRepository
    lateinit var progress: ProgressRepository

    /** (Re)lê tudo do armazenamento; usado ao abrir o app e depois de restaurar um backup. */
    fun load(context: Context) {
        val app = context.applicationContext
        bank = BankRepository(app)
        profile = ProfileRepository(app)
        blocking = BlockSettingsRepository(app)
        progress = ProgressRepository(app)
    }
}
