package com.pushlife.app.core

// update.json do GitHub: versão abaixo de minVersionCode trava até instalar a nova pela url (Uptodown)
// notes = novidades por idioma (pt/en/es), opcional
data class UpdateInfo(
    val minVersionCode: Long,
    val url: String,
    val notes: Map<String, String> = emptyMap(),
)

object UpdatePolicy {
    // true = versão velha demais, trava o app
    fun mustUpdate(installedVersionCode: Long, info: UpdateInfo?): Boolean =
        info != null && info.url.isNotBlank() && info.minVersionCode > installedVersionCode

    // novidades no idioma do aparelho; senão inglês; senão qualquer uma
    fun notesFor(info: UpdateInfo, language: String): String? =
        info.notes[language]?.takeIf { it.isNotBlank() }
            ?: info.notes["en"]?.takeIf { it.isNotBlank() }
            ?: info.notes.values.firstOrNull { it.isNotBlank() }
}
