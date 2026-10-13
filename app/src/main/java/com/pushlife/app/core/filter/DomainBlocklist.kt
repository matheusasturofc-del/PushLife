package com.pushlife.app.core.filter

/**
 * Lista de domínios bloqueados pelo filtro de sites adultos. Um domínio bloqueia também todos
 * os seus subdomínios (bloquear "site.com" bloqueia "www.site.com"). Palavras-chave pegam
 * espelhos e domínios novos que não estão na lista.
 */
class DomainBlocklist(
    domains: Collection<String> = ADULT_DOMAINS,
    private val keywords: List<String> = ADULT_KEYWORDS,
) {
    private val set = domains.map(::normalize).toHashSet()

    fun isBlocked(host: String): Boolean {
        val name = normalize(host)
        if (name.isEmpty()) return false
        var candidate = name
        while (true) {
            if (candidate in set) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) break
            candidate = candidate.substring(dot + 1)
        }
        return keywords.any { name.contains(it) }
    }

    private fun normalize(host: String) = host.trim().trimEnd('.').lowercase()

    companion object {
        /** Partes de nome que quase só aparecem em sites adultos ("sex" fica de fora: "essex", "sussex"…). */
        val ADULT_KEYWORDS = listOf("porn", "xxx", "hentai", "xvideo", "xnxx", "xhamster", "redtube", "rule34")

        /** Os sites adultos e de hentai mais acessados. O filtro família da Cloudflare cobre o resto. */
        val ADULT_DOMAINS = listOf(
            "pornhub.com", "pornhub.org", "xvideos.com", "xvideos2.com", "xnxx.com", "xhamster.com",
            "xhamster.desi", "redtube.com", "youporn.com", "tube8.com", "spankbang.com", "eporner.com",
            "txxx.com", "hclips.com", "porntrex.com", "beeg.com", "tnaflix.com", "empflix.com",
            "drtuber.com", "sunporno.com", "youjizz.com", "motherless.com", "ixxx.com", "4tube.com",
            "porn.com", "pornone.com", "chaturbate.com", "stripchat.com", "bongacams.com", "cam4.com",
            "camsoda.com", "livejasmin.com", "myfreecams.com", "onlyfans.com", "fansly.com",
            "manyvids.com", "clips4sale.com", "brazzers.com", "realitykings.com", "naughtyamerica.com",
            "bangbros.com", "mofos.com", "digitalplayground.com", "erome.com", "redgifs.com",
            "imagefap.com", "rule34.xxx", "nhentai.net", "e-hentai.org", "exhentai.org", "hanime.tv",
            "hentaihaven.xxx", "hentai2read.com", "hitomi.la", "tsumino.com", "luscious.net", "f95zone.to",
        )
    }
}
