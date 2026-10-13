package com.pushlife.app

import android.app.Application
import com.pushlife.app.data.BankRepository

class PushLifeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.bank = BankRepository(this)
    }
}

/** Objetos compartilhados pelo app inteiro. */
object AppGraph {
    lateinit var bank: BankRepository
}
