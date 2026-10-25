package com.pushlife.app.core.filter

// sites adultos bloqueados; o domínio já pega os subdomínios
// palavras-chave pegam espelhos e domínios novos
class DomainBlocklist(
    domains: Collection<String> = ADULT_DOMAINS + GORE_DOMAINS,
    private val keywords: List<String> = ADULT_KEYWORDS,
    private val big: HashedDomains? = null,
) {
    private val set = domains.map(::normalize).toHashSet()

    fun isBlocked(host: String): Boolean {
        val name = normalize(host)
        if (name.isEmpty()) return false
        var candidate = name
        while (true) {
            if (candidate in set || (big != null && candidate in big)) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) break
            candidate = candidate.substring(dot + 1)
        }
        return keywords.any { name.contains(it) } || isGore(name)
    }

    // "gore" em qualquer parte do nome (gore.fun, seegore.com, goresite.net...),
    // menos os legítimos: gore-tex, gorenje, a empresa W. L. Gore (gore.com), al gore...
    private fun isGore(name: String): Boolean {
        if (name == "gore.com" || name.endsWith(".gore.com")) return false
        val labels = name.split('.').dropLast(1) // sem a terminação (.com, .fun...)
        return labels.any { label -> "gore" in label && GORE_SAFE.none { it in label } }
    }

    private fun normalize(host: String) = host.trim().trimEnd('.').lowercase()

    companion object {
        // pedaços de nome quase só de site adulto ("sex" ficou de fora por causa de "essex")
        val ADULT_KEYWORDS = listOf("porn", "xxx", "hentai", "xvideo", "xnxx", "xhamster", "redtube", "rule34")

        // os mais acessados, sempre bloqueados (o resto vem de assets/filter)
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

        // gore / violência explícita: lista nossa (não achei uma pública com licença livre)
        val GORE_DOMAINS = listOf(
            "bestgore.com", "bestgore.fun", "bestgore.org", "theync.com", "kaotic.com", "documentingreality.com",
            "crazyshit.com", "goregrish.com", "seegore.com", "gorecenter.com", "goresee.com", "deathaddict.com",
            "watchpeopledie.tv", "runthegauntlet.org", "ogrish.com", "rotten.com", "heavy-r.com", "efukt.com",
        )

        // palavras com "gore" que não são gore
        val GORE_SAFE = listOf("goretex", "gore-tex", "gorenje", "goregaon", "gorey", "gorebridge", "algore", "gorean", "goreville")
    }
}
