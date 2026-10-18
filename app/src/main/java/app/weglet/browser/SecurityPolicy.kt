package app.weglet.browser

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.IDN
import java.util.Locale
import kotlin.math.abs

object SecurityPolicy {
    enum class RiskLevel { WARNING, BLOCK }

    data class NavigationRisk(
        val level: RiskLevel,
        val title: String,
        val reason: String,
        val normalizedHost: String? = null
    )

    private data class BrandRule(
        val displayName: String,
        val tokens: Set<String>,
        val officialRoots: Set<String>
    )

    private val blockedHosts = setOf(
        "adnxs.com",
        "ads.microsoft.com",
        "adsrvr.org",
        "amazon-adsystem.com",
        "2mdn.net",
        "adservice.google.com",
        "adservice.google.ru",
        "app-measurement.com",
        "bat.bing.com",
        "clarity.ms",
        "connect.facebook.net",
        "criteo.com",
        "criteo.net",
        "doubleclick.net",
        "google-analytics.com",
        "googlesyndication.com",
        "googletagmanager.com",
        "googletagservices.com",
        "hotjar.com",
        "mc.yandex.ru",
        "metrika.yandex.ru",
        "pixel.facebook.com",
        "quantserve.com",
        "scorecardresearch.com",
        "segment.io",
        "taboola.com",
        "track.adform.net",
        "yandexadexchange.net"
    )

    private val brandRules = listOf(
        BrandRule("Telegram", setOf("telegram"), setOf("telegram.org", "t.me")),
        BrandRule(
            "Google",
            setOf("google", "gmail", "youtube"),
            setOf(
                "google.com", "google.ru", "google.co.uk", "google.co.in", "google.co.jp",
                "google.com.au", "google.com.br", "google.com.tr", "google.com.ua", "google.ca",
                "google.de", "google.fr", "google.es", "google.it", "google.nl", "google.pl",
                "google.fi", "google.se", "google.no", "google.dk", "google.ch", "google.at",
                "google.be", "google.ie", "google.pt", "google.cz", "google.gr", "google.ro",
                "google.hu", "google.bg", "google.kz", "google.by",
                "googleusercontent.com", "googleapis.com", "gstatic.com", "gmail.com",
                "youtube.com", "youtu.be"
            )
        ),
        BrandRule(
            "Microsoft",
            setOf("microsoft", "outlook", "office365", "onedrive"),
            setOf(
                "microsoft.com", "live.com", "microsoftonline.com", "office.com", "office365.com",
                "outlook.com", "onedrive.com", "sharepoint.com"
            )
        ),
        BrandRule("Apple/iCloud", setOf("icloud", "appleid"), setOf("apple.com", "icloud.com")),
        BrandRule("Facebook", setOf("facebook"), setOf("facebook.com", "fb.com", "fb.me")),
        BrandRule("Instagram", setOf("instagram"), setOf("instagram.com")),
        BrandRule("WhatsApp", setOf("whatsapp"), setOf("whatsapp.com", "wa.me")),
        BrandRule("Discord", setOf("discord"), setOf("discord.com", "discord.gg", "discordapp.com")),
        BrandRule("GitHub", setOf("github"), setOf("github.com", "github.io", "github.blog", "githubusercontent.com")),
        BrandRule("Steam", setOf("steam", "steamcommunity"), setOf("steampowered.com", "steamcommunity.com")),
        BrandRule("Roblox", setOf("roblox"), setOf("roblox.com")),
        BrandRule("Binance", setOf("binance"), setOf("binance.com")),
        BrandRule("PayPal", setOf("paypal"), setOf("paypal.com", "paypal.me")),
        BrandRule("Сбер", setOf("sber", "sberbank"), setOf("sber.ru", "sberbank.ru", "online.sberbank.ru")),
        BrandRule("Т-Банк", setOf("tbank", "tinkoff"), setOf("tbank.ru", "tinkoff.ru")),
        BrandRule("ВКонтакте", setOf("vkontakte"), setOf("vk.com", "vk.ru"))
    )

    private val sensitiveWords = setOf(
        "account", "appeal", "auth", "bank", "billing", "confirm", "credential",
        "login", "password", "payment", "recovery", "reset", "secure", "seed",
        "signin", "support", "unlock", "verification", "verify", "wallet", "2fa", "otp",
        "аккаунт", "банк", "восстанов", "вход", "пароль", "подтверд", "провер", "кошелек"
    )

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.su", "cutt.ly", "clck.ru", "is.gd",
        "rb.gy", "rebrand.ly", "shorturl.at", "tiny.cc", "u.to", "to.lk"
    )

    private val highRiskTlds = setOf(
        "bond", "click", "cyou", "fit", "gq", "icu", "kim", "live", "ml", "mom",
        "rest", "shop", "support", "top", "work", "xyz", "zip"
    )

    fun shouldBlockResource(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        if (uri.scheme.equals("http", ignoreCase = true)) return true
        return matchedTrackerHost(uri.host) != null
    }

    fun matchedTrackerHost(host: String?): String? {
        var candidate = normalizedHost(host) ?: return null
        while (true) {
            if (candidate in blockedHosts) return candidate
            val dot = candidate.indexOf('.')
            if (dot < 0) return null
            candidate = candidate.substring(dot + 1)
        }
    }

    fun matchesUserBlocklist(url: String, blocked: Set<String>): Boolean {
        if (blocked.isEmpty()) return false
        var candidate = runCatching { normalizedHost(Uri.parse(url).host) }.getOrNull() ?: return false
        while (true) {
            if (candidate in blocked) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    fun blockedResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        204,
        "Blocked by Weglet",
        mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
        ByteArrayInputStream(ByteArray(0))
    )

    fun suspiciousReason(url: String): String? = assessNavigation(url)?.reason

    fun assessNavigation(rawUrl: String): NavigationRisk? {
        if (rawUrl.length > 4096) {
            return block("Слишком длинный адрес", "Адрес сайта необычно длинный и может скрывать настоящий домен.")
        }
        if (rawUrl.any { it.code < 0x20 || it.code == 0x7F } || rawUrl.any(::isBidiControl)) {
            return block("Скрытые символы в адресе", "Ссылка содержит управляющие символы, способные замаскировать настоящий адрес.")
        }
        if ('\\' in rawUrl || ENCODED_CONTROL.containsMatchIn(rawUrl)) {
            return block("Небезопасный формат ссылки", "Ссылка содержит обратную косую черту или закодированные управляющие символы.")
        }

        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull()
            ?: return block("Неправильный адрес", "Не удалось безопасно разобрать адрес сайта.")
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme !in setOf("http", "https")) {
            return block("Неподдерживаемая ссылка", "Сайт пытается открыть неподдерживаемый тип ссылки.")
        }
        if (!uri.userInfo.isNullOrBlank()) {
            return block(
                "Домен спрятан в ссылке",
                "Перед доменом указаны имя пользователя или пароль. Такой приём часто используют в фишинговых ссылках."
            )
        }
        val rawHost = uri.host?.trim()
            ?: return block("Домен не определён", "Weglet не смог определить настоящий домен сайта.")
        val hadTrailingDot = rawHost.endsWith('.')
        val host = normalizedHost(rawHost)
            ?: return block("Недопустимый домен", "Имя домена не соответствует безопасному формату DNS.")
        if (host.length > 253 || host.split('.').any { it.isEmpty() || it.length > 63 }) {
            return block("Недопустимый домен", "Имя домена превышает безопасную длину или содержит пустую часть.", host)
        }
        if (host == "weglet.local") return null

        val unicodeHost = runCatching { IDN.toUnicode(host) }.getOrDefault(host)
        val mixedScripts = unicodeHost.split('.').any(::containsMixedScripts)
        val usesPunycode = host.split('.').any { it.startsWith("xn--") }
        val context = buildString {
            append(unicodeHost.lowercase(Locale.US))
            append(' ')
            append(uri.path.orEmpty().lowercase(Locale.US))
            append(' ')
            append(uri.query.orEmpty().lowercase(Locale.US))
        }
        val sensitive = sensitiveWords.any(context::contains)

        findBrandImpersonation(host, unicodeHost, sensitive, mixedScripts, usesPunycode)?.let { return it }

        if (mixedScripts) {
            return NavigationRisk(
                if (sensitive) RiskLevel.BLOCK else RiskLevel.WARNING,
                "Похожие буквы в домене",
                "Домен смешивает буквы разных алфавитов. Так настоящий сайт часто маскируют символами, похожими внешне.",
                host
            )
        }
        if (usesPunycode && sensitive) {
            return warn(
                "Закодированный домен",
                "Страница входа или оплаты использует международное закодированное имя домена. Проверьте его особенно внимательно.",
                host
            )
        }
        if (hadTrailingDot) {
            return warn("Необычная запись домена", "Адрес заканчивается скрывающей точкой. Проверьте домен перед продолжением.", host)
        }

        val isIp = isIpv4(host) || host.contains(':')
        if (isIp) {
            val local = isPrivateAddress(host)
            val detail = if (local) {
                "Сайт открывается по адресу локальной сети, а не по обычному доменному имени."
            } else {
                "Сайт открывается по цифровому IP-адресу вместо обычного доменного имени."
            }
            return warn("Сайт без доменного имени", "$detail Не вводите пароль, пока не уверены в адресе.", host)
        }

        if (shorteners.any { host == it || host.endsWith(".$it") }) {
            return warn(
                "Сокращённая ссылка",
                "Короткая ссылка скрывает конечный сайт. Weglet повторно проверит адрес после перенаправления.",
                host
            )
        }

        val tld = host.substringAfterLast('.', "")
        if (sensitive && tld in highRiskTlds) {
            return warn(
                "Рискованный адрес формы входа",
                "Страница запрашивает чувствительные данные и использует доменную зону, часто встречающуюся в одноразовых фишинговых сайтах.",
                host
            )
        }
        if (sensitive && host.count { it == '.' } >= 5) {
            return warn(
                "Слишком много поддоменов",
                "Страница входа использует длинную цепочку поддоменов, которая может отвлекать от настоящего владельца сайта.",
                host
            )
        }
        if (uri.port > 0 && uri.port !in setOf(80, 443)) {
            return warn(
                "Необычный сетевой порт",
                "Сайт использует порт ${uri.port}. Убедитесь, что этот адрес вам знаком.",
                host
            )
        }
        return null
    }

    private fun findBrandImpersonation(
        host: String,
        unicodeHost: String,
        sensitive: Boolean,
        mixedScripts: Boolean,
        usesPunycode: Boolean
    ): NavigationRisk? {
        val labels = unicodeHost.split('.').filter { it.isNotBlank() }
        for (rule in brandRules) {
            if (rule.officialRoots.any { host == it || host.endsWith(".$it") }) continue

            val normalizedLabels = labels.map(::normalizeLookalike)
            val exactLookalike = normalizedLabels.any { label ->
                rule.tokens.any { token -> label == token || editDistanceAtMostOne(label, token) }
            }
            val containsBrand = normalizedLabels.any { label ->
                rule.tokens.any { token ->
                    label.contains(token) && label.length <= token.length + 18
                }
            }
            if (!exactLookalike && !containsBrand) continue

            val deceptiveOfficialSuffix = rule.officialRoots.any { official ->
                host.contains("$official.") || host.contains("$official-") || host.contains("${official}_")
            }
            val hardBlock = exactLookalike || sensitive || mixedScripts || usesPunycode || deceptiveOfficialSuffix
            val level = if (hardBlock) RiskLevel.BLOCK else RiskLevel.WARNING
            val action = if (hardBlock) "Weglet заблокировал переход." else "Внимательно проверьте адрес перед переходом."
            return NavigationRisk(
                level,
                "Возможная подделка ${rule.displayName}",
                "Домен похож на ${rule.displayName}, но не входит в список официальных доменов. $action",
                host
            )
        }
        return null
    }

    private fun normalizedHost(value: String?): String? {
        val raw = value?.trim()?.trimEnd('.')?.lowercase(Locale.US) ?: return null
        if (raw.isBlank()) return null
        if (raw.contains(':')) return raw.removePrefix("[").removeSuffix("]")
        return runCatching { IDN.toASCII(raw, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.US) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun containsMixedScripts(label: String): Boolean {
        val scripts = label.asSequence()
            .filter(Char::isLetter)
            .map { Character.UnicodeScript.of(it.code) }
            .filter { it !in setOf(Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED) }
            .toSet()
        if (scripts.size < 2) return false
        return Character.UnicodeScript.LATIN in scripts &&
            scripts.any { it == Character.UnicodeScript.CYRILLIC || it == Character.UnicodeScript.GREEK }
    }

    private fun normalizeLookalike(value: String): String = buildString {
        value.lowercase(Locale.US).forEach { character ->
            append(
                when (character) {
                    'а', 'α' -> 'a'
                    'в', 'β' -> 'b'
                    'с', 'ϲ' -> 'c'
                    'е', 'ё', 'ε' -> 'e'
                    'і', 'ι' -> 'i'
                    'к', 'κ' -> 'k'
                    'м', 'μ' -> 'm'
                    'н', 'η' -> 'h'
                    'о', 'ο', '0' -> 'o'
                    'р', 'ρ' -> 'p'
                    'ѕ' -> 's'
                    'т', 'τ' -> 't'
                    'у', 'γ' -> 'y'
                    'х', 'χ' -> 'x'
                    '1', 'l' -> 'l'
                    else -> character
                }
            )
        }
    }.filter { it in 'a'..'z' || it in '0'..'9' }

    private fun editDistanceAtMostOne(first: String, second: String): Boolean {
        if (first == second) return true
        if (abs(first.length - second.length) > 1) return false
        var left = 0
        var right = 0
        var changes = 0
        while (left < first.length && right < second.length) {
            if (first[left] == second[right]) {
                left++
                right++
            } else {
                if (++changes > 1) return false
                if (first.length > second.length) left++
                else if (second.length > first.length) right++
                else {
                    left++
                    right++
                }
            }
        }
        if (left < first.length || right < second.length) changes++
        return changes <= 1
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) && part.toIntOrNull() in 0..255
        }
    }

    private fun isPrivateAddress(host: String): Boolean {
        if (host == "::1" || host.startsWith("fc", true) || host.startsWith("fd", true) || host.startsWith("fe80", true)) {
            return true
        }
        if (!isIpv4(host)) return false
        val octets = host.split('.').map(String::toInt)
        return octets[0] == 10 || octets[0] == 127 ||
            (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    private fun isBidiControl(character: Char): Boolean = character.code in setOf(
        0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
        0x2066, 0x2067, 0x2068, 0x2069
    )

    private fun block(title: String, reason: String, host: String? = null) =
        NavigationRisk(RiskLevel.BLOCK, title, reason, host)

    private fun warn(title: String, reason: String, host: String? = null) =
        NavigationRisk(RiskLevel.WARNING, title, reason, host)

    private val ENCODED_CONTROL = Regex("%(?:00|0a|0d|1b|7f)", RegexOption.IGNORE_CASE)
}
