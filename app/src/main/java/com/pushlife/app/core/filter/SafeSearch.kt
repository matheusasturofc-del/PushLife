package com.pushlife.app.core.filter

// pesquisa segura forçada: o endereço do buscador vira o da versão "segura" dele
// (o próprio Google, Bing, DuckDuckGo e YouTube oferecem esses endereços para escolas e famílias)
object SafeSearch {
    private val google = Regex("^(www\\.)?google(\\.[a-z]{2,3}){1,2}$") // google.com, google.com.br, www.google.co.uk...
    private val youtube = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtubei.googleapis.com",
        "youtube.googleapis.com", "www.youtube-nocookie.com",
    )

    fun target(host: String): String? {
        val h = host.trimEnd('.').lowercase()
        return when {
            google.matches(h) -> "forcesafesearch.google.com"
            h == "bing.com" || h == "www.bing.com" -> "strict.bing.com"
            h == "duckduckgo.com" || h == "www.duckduckgo.com" -> "safe.duckduckgo.com"
            h in youtube -> "restrictmoderate.youtube.com" // modo restrito moderado
            else -> null
        }
    }
}
