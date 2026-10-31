package com.pushlife.app.core.filter

// pesquisa segura forçada: o endereço do buscador vira o da versão "segura" dele
// (o próprio Google, Bing e DuckDuckGo oferecem esses endereços para escolas e famílias)
// YouTube fica de fora: o modo restrito bloqueia os comentários
object SafeSearch {
    private val google = Regex("^(www\\.)?google(\\.[a-z]{2,3}){1,2}$") // google.com, google.com.br, www.google.co.uk...

    fun target(host: String): String? {
        val h = host.trimEnd('.').lowercase()
        return when {
            google.matches(h) -> "forcesafesearch.google.com"
            h == "bing.com" || h == "www.bing.com" -> "strict.bing.com"
            h == "duckduckgo.com" || h == "www.duckduckgo.com" -> "safe.duckduckgo.com"
            else -> null
        }
    }
}
