package com.pushlife.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

// registro de erros do próprio app (sem empresa de fora):
// se o app fechar sozinho, guarda o motivo num arquivo; na próxima abertura oferece mandar por e-mail
object CrashLog {
    const val EMAIL = "matheusasturofc@gmail.com"
    private const val FILE = "ultimo-erro.txt"

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file(app).writeText(header(app) + "Linha: ${thread.name}\n\n" + trace.take(30_000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    // erro guardado da última vez (null = nada)
    fun pending(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    // versão do app e do aparelho, pra eu saber onde aconteceu
    fun header(context: Context): String {
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("?")
        return "PushLife $version\nAparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Quando: ${LocalDateTime.now().withNano(0)}\n"
    }

    // abre o app de e-mail já com o relatório escrito
    fun emailIntent(subject: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
}
