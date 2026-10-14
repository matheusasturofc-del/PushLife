package com.pushlife.app.core

/**
 * O que o arquivo `update.json` (no GitHub) diz: qualquer versão com número menor que
 * [minVersionCode] fica travada até o usuário instalar a nova pela [url] (Uptodown).
 * [notes] são as novidades por idioma ("pt", "en", "es"), opcionais.
 */
data class UpdateInfo(
    val minVersionCode: Long,
    val url: String,
    val notes: Map<String, String> = emptyMap(),
)

object UpdatePolicy {
    /** true quando a versão instalada é velha demais e o app deve ficar travado. */
    fun mustUpdate(installedVersionCode: Long, info: UpdateInfo?): Boolean =
        info != null && info.url.isNotBlank() && info.minVersionCode > installedVersionCode

    /** Novidades no idioma do aparelho; cai para inglês e depois para qualquer uma. */
    fun notesFor(info: UpdateInfo, language: String): String? =
        info.notes[language]?.takeIf { it.isNotBlank() }
            ?: info.notes["en"]?.takeIf { it.isNotBlank() }
            ?: info.notes.values.firstOrNull { it.isNotBlank() }
}
