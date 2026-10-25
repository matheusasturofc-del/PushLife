package com.pushlife.app.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

// idioma: "" segue o celular, "pt"/"en"/"es" fixa
// Android 13+ o sistema cuida; antes disso a gente guarda e aplica na mão via wrap()
object AppLanguage {
    val TAGS = listOf("", "pt", "en", "es")

    fun get(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val list = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (list.isEmpty) "" else list[0].language
        }
        return prefs(context).getString(KEY, "").orEmpty()
    }

    fun set(activity: Activity, tag: String) {
        prefs(activity).edit { putString(KEY, tag) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // O sistema reabre as telas sozinho no novo idioma.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            // atualiza o contexto do app também (notificações), sem reiniciar
            val res = activity.applicationContext.resources
            val config = Configuration(res.configuration).apply { setLocale(localeFor(tag)) }
            Locale.setDefault(localeFor(tag))
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            activity.recreate()
        }
    }

    // chamar no attachBaseContext (Application, Activities e Services)
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val locale = localeFor(prefs(base).getString(KEY, "").orEmpty())
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private fun localeFor(tag: String): Locale =
        if (tag.isEmpty()) Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag)

    private fun prefs(context: Context) = context.getSharedPreferences("language", Context.MODE_PRIVATE)

    private const val KEY = "tag"
}
