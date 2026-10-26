package com.pushlife.app.data

import android.content.Context
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat

// aceite dos termos e se o app já foi "preparado" nessa versão
object Legal {
    // sobe quando os termos mudarem: todo mundo aceita de novo
    const val TERMS_VERSION = 1
    const val TERMS_URL = "https://matheusasturofc-del.github.io/PushLife/termos.html"
    const val PRIVACY_URL = "https://matheusasturofc-del.github.io/PushLife/privacidade.html"

    private fun prefs(context: Context) = context.getSharedPreferences("legal", Context.MODE_PRIVATE)

    fun termsAccepted(context: Context): Boolean = prefs(context).getInt(KEY_TERMS, 0) >= TERMS_VERSION

    fun acceptTerms(context: Context) = prefs(context).edit {
        putInt(KEY_TERMS, TERMS_VERSION)
        putLong(KEY_TERMS_AT, System.currentTimeMillis())
    }

    // primeira vez e depois de cada atualização, igual app de banco
    fun needsPrepare(context: Context): Boolean = prefs(context).getLong(KEY_PREPARED, -1L) != versionCode(context)

    fun markPrepared(context: Context) = prefs(context).edit { putLong(KEY_PREPARED, versionCode(context)) }

    private fun versionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private const val KEY_TERMS = "terms_version"
    private const val KEY_TERMS_AT = "terms_accepted_at"
    private const val KEY_PREPARED = "prepared_version"
}
