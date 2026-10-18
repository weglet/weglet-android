package app.weglet.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.text.DateFormat
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.io.File
import java.util.Locale
import org.json.JSONObject

class MainActivity : Activity() {
    private data class BrowserTab(
        val id: Long,
        var title: String,
        var url: String,
        var searchQuery: String? = null,
        val webView: WebView,
        val engineView: WegletEngineView,
        var engineActive: Boolean = false
    )

    private data class SiteEntry(
        val title: String,
        val url: String
    )

    private data class BrandIcon(
        val path: String,
        val color: String,
        val background: String
    )

    private data class DownloadEntry(
        val id: Long,
        val name: String,
        val mimeType: String,
        val status: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val modifiedAt: Long
    )

    private data class MusicTrack(
        val id: String,
        val title: String,
        val artist: String,
        val durationSeconds: Int,
        val permalink: String
    )

    private enum class SearchEngine(
        val id: String,
        val title: String,
        val shortTitle: String
    ) {
        DUCKDUCKGO("duckduckgo", "DuckDuckGo", "DDG"),
        BING("bing", "Bing", "Bing"),
        GOOGLE("google", "Google", "Google"),
        CUSTOM("custom", "Свой поиск", "Свой")
    }

    private enum class BrowserEngine(val id: String, val title: String) {
        GECKO("gecko", "Firefox Gecko"),
        WEBVIEW("webview", "Android WebView")
    }

    private enum class AccentTheme(
        val id: String,
        val title: String,
        val accentHex: String,
        val backgroundHex: String,
        val surfaceHex: String,
        val stormHex: String
    ) {
        VIOLET_GRAPHITE("violet_graphite", "Фиолетовый графит", "#9C8CFF", "#15151B", "#22222B", "#9C8CFF"),
        SIGNAL_YELLOW("signal_yellow", "Сигнальный жёлтый", "#F5C518", "#101012", "#1D1D21", "#F5C518"),
        COLD_BLUE("cold_blue", "Холодный синий", "#72B7FF", "#10141A", "#1B222C", "#72B7FF"),
        MONO("mono", "Монохром", "#D8D5E3", "#111114", "#202025", "#B8B4C5"),
        CUSTOM("custom", "Своя палитра", "#9C8CFF", "#15151B", "#22222B", "#9C8CFF")
    }

    private enum class BarShape(val id: String, val title: String, val radius: Int) {
        SQUARE("square", "Без скругления", 0),
        SOFT("soft", "Лёгкое скругление", 6),
        ROUNDED("rounded", "Скруглённая", 12),
        LARGE("large", "Крупные углы", 18),
        PILL("pill", "Капсула", 28)
    }

    private enum class BarOutline(val id: String, val title: String, val width: Int) {
        NONE("none", "Без контура", 0),
        THIN("thin", "Тонкий", 1),
        MEDIUM("medium", "Средний", 2),
        BOLD("bold", "Толстый", 3)
    }

    private enum class BarHeight(val id: String, val title: String, val height: Int) {
        COMPACT("compact", "Компактная", 46),
        STANDARD("standard", "Обычная", 52),
        LARGE("large", "Высокая", 60)
    }

    private enum class BarWidth(val id: String, val title: String, val cssWidth: String) {
        NARROW("narrow", "Узкая", "300px"),
        STANDARD("standard", "Обычная", "360px"),
        FULL("full", "На всю ширину", "100%")
    }

    private enum class ToolbarPosition(val id: String, val title: String) {
        TOP("top", "Сверху"),
        BOTTOM("bottom", "Снизу")
    }

    private enum class HomePosition(val id: String, val title: String, val cssAlign: String) {
        TOP("top", "Сверху", "flex-start"),
        CENTER("center", "По центру", "center"),
        BOTTOM("bottom", "Снизу", "flex-end")
    }

    private enum class EntranceMotion(val id: String, val title: String) {
        BOTTOM_UP("bottom_up", "Снизу вверх"),
        TOP_DOWN("top_down", "Сверху вниз"),
        LEFT_IN("left_in", "Слева направо"),
        RIGHT_IN("right_in", "Справа налево"),
        SCALE("scale", "Мягкое увеличение"),
        FADE("fade", "Только проявление"),
        NONE("none", "Без анимации")
    }

    private enum class AnimationSpeed(val id: String, val title: String, val factor: Float) {
        FAST("fast", "Быстрая", 0.72f),
        BALANCED("balanced", "Плавная", 1.0f),
        RELAXED("relaxed", "Медленная", 1.35f)
    }

    private enum class WallpaperFit(val id: String, val title: String) {
        COVER("cover", "Заполнить"),
        CONTAIN("contain", "Вместить"),
        STRETCH("stretch", "Растянуть"),
        TILE("tile", "Плитка")
    }

    private enum class WallpaperShade(val id: String, val title: String, val alpha: Float) {
        LIGHT("light", "Светлое", 0.34f),
        BALANCED("balanced", "Сбалансированное", 0.58f),
        DARK("dark", "Тёмное", 0.76f)
    }

    private enum class ClockSize(val id: String, val title: String, val pixels: Int) {
        COMPACT("compact", "Компактные", 54),
        STANDARD("standard", "Обычные", 66),
        LARGE("large", "Крупные", 78)
    }

    private enum class ClockFont(val id: String, val title: String, val cssFamily: String) {
        ONEST("onest", "Onest", "Onest"),
        MONO("mono", "JetBrains Mono", "JetBrainsMono")
    }

    private enum class StormIntensity(val id: String, val title: String, val minDelay: Int, val delayRange: Int, val width: Double) {
        CALM("calm", "Редкая", 10500, 7000, 2.2),
        NORMAL("normal", "Обычная", 5600, 6200, 3.1),
        HEAVY("heavy", "Сильная", 2600, 3600, 4.4)
    }

    private enum class UiLanguage(val id: String, val title: String) {
        RUSSIAN("ru", "Русский"),
        ENGLISH("en", "English")
    }

    private enum class ShortcutPreset(
        val title: String,
        val url: String
    ) {
        YOUTUBE("YouTube", "https://www.youtube.com/"),
        TIKTOK("TikTok", "https://www.tiktok.com/"),
        TELEGRAM("Telegram", "https://web.telegram.org/"),
        WIKIPEDIA("Wikipedia", "https://ru.wikipedia.org/"),
        GITHUB("GitHub", "https://github.com/"),
        INSTAGRAM("Instagram", "https://www.instagram.com/"),
        WHATSAPP("WhatsApp", "https://web.whatsapp.com/")
    }

    private val tabs = mutableListOf<BrowserTab>()
    private val approvedHttp = mutableSetOf<String>()
    private val approvedSuspicious = mutableSetOf<String>()
    private val engineFallbackPrompted = mutableSetOf<String>()
    private lateinit var pageHost: FrameLayout
    private lateinit var address: EditText
    private lateinit var addressRow: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var downloadStatus: TextView
    private lateinit var securityChip: TextView
    private lateinit var engineChip: TextView
    private lateinit var musicChip: TextView
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    private lateinit var homeButton: TextView
    private lateinit var tabsButton: TextView
    private lateinit var goButton: Button
    private lateinit var topChrome: View
    private lateinit var bottomChrome: View
    private var activeTabId: Long = -1
    private var nextTabId = 1L
    private var restoredTabUrls: ArrayList<String>? = null
    private var restoredActiveIndex = 0
    private var pendingPermissionRequest: PermissionRequest? = null
    private var blockedSitesCache: Set<String>? = null
    private val downloadHandler = Handler(Looper.getMainLooper())
    private val playerHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("weglet", MODE_PRIVATE) }
    private val threatFeed by lazy { ThreatFeed(prefs) }
    private val onestRegular by lazy { fontTypeface("fonts/Onest.ttf", 400) }
    private val onestMedium by lazy { fontTypeface("fonts/Onest.ttf", 600) }
    private val onestBold by lazy { fontTypeface("fonts/Onest.ttf", 750) }
    private val tablerIcons by lazy {
        runCatching { Typeface.createFromAsset(assets, "fonts/TablerIcons.ttf") }
            .getOrElse { Typeface.DEFAULT }
    }

    private val background: Int get() = themeColor("custom_background", selectedAccentTheme().backgroundHex)
    private val accent: Int get() = themeColor("custom_accent", selectedAccentTheme().accentHex)
    private val stormColor: Int get() = themeColor("custom_storm", selectedAccentTheme().stormHex)
    private val automaticInk: Int get() = if (Color.luminance(background) > 0.48f) Color.parseColor("#111116") else Color.parseColor("#F4F1FF")
    private val ink: Int get() = themeColor("custom_ink", colorHex(automaticInk))
    private val muted: Int get() = blendColor(background, ink, 0.64f)
    private val surface: Int get() = if (selectedAccentTheme() == AccentTheme.CUSTOM) {
        blendColor(background, ink, 0.09f)
    } else Color.parseColor(selectedAccentTheme().surfaceHex)
    private val chrome: Int get() = blendColor(background, ink, 0.045f)
    private val panelLine: Int get() = blendColor(background, ink, 0.27f)
    private val searchBarColor: Int get() = themeColor("custom_search_bar", colorHex(surface))
    private val searchTextColor: Int get() = themeColor("custom_search_text", colorHex(ink))
    private val searchPlaceholderColor: Int get() = themeColor("custom_search_placeholder", colorHex(muted))
    private val searchOutlineColor: Int get() = themeColor("custom_search_outline", colorHex(accent))
    private val clockColor: Int get() = themeColor("custom_clock", colorHex(ink))
    private val shortcutSurfaceColor: Int get() = themeColor("custom_shortcut_surface", colorHex(surface))
    private val greenDark: Int get() = accent
    private val warning: Int get() = accent
    private var mediaPlayer: MediaPlayer? = null
    private var playerLoading = false
    private var playerTitle = ""
    private var playerArtist = ""
    private var playerPageUrl: String? = null
    private var playerDialog: Dialog? = null
    private var playerTitleView: TextView? = null
    private var playerArtistView: TextView? = null
    private var playerTimeView: TextView? = null
    private var playerSeekBar: SeekBar? = null
    private var playerPlayButton: TextView? = null
    private var playerSourceView: TextView? = null
    private var safeBrowsingReady = false
    private var securityBootstrapStarted = false
    private var safeBrowsingGateOpened = false
    @Volatile private var blockedTrackerRequests = 0
    @Volatile private var lastBlockedTrackerHost: String? = null
    private var analyzedHtmlPages = 0
    private var warnedHtmlPages = 0
    private var blockedHtmlPhishingPages = 0
    private var lastPageAnalysisCode = 0
    private var lastWarnedHtmlUrl: String? = null
    private val playerProgress = object : Runnable {
        override fun run() {
            updatePlayerProgress()
            if (playerDialog?.isShowing == true) playerHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredTabUrls = savedInstanceState?.getStringArrayList("tab_urls")
        restoredActiveIndex = savedInstanceState?.getInt("active_tab_index", 0) ?: 0
        if (!prefs.contains("blocked_sites")) {
            prefs.edit().putStringSet("blocked_sites", setOf("vk.ru")).apply()
        }
        migratePrivateData()
        ensureDefaultQuickLaunch()
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = 0
        buildInterface()
        WebView.setWebContentsDebuggingEnabled(false)
        if (hasAcceptedTerms()) startProtectedBrowserSession() else showFirstRunAgreement()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (hasAcceptedTerms()) startProtectedBrowserSession()
    }

    private fun startProtectedBrowserSession() {
        refreshThreatFeed()
        if (safeBrowsingGateOpened) {
            startBrowserSession()
            return
        }
        if (securityBootstrapStarted) return
        securityBootstrapStarted = true
        downloadHandler.postDelayed({ finishSafeBrowsingBootstrap(false) }, 1_600L)
        runCatching {
            WebView.startSafeBrowsing(this) { success ->
                runOnUiThread { finishSafeBrowsingBootstrap(success) }
            }
        }.onFailure {
            finishSafeBrowsingBootstrap(false)
        }
    }

    private fun finishSafeBrowsingBootstrap(success: Boolean) {
        if (success) safeBrowsingReady = true
        if (safeBrowsingGateOpened || isFinishing || isDestroyed) return
        safeBrowsingGateOpened = true
        startBrowserSession()
    }

    private fun refreshThreatFeed(force: Boolean = false, showResult: Boolean = false) {
        threatFeed.refreshIfNeeded(force) { success ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val current = currentTab()
                if (success && current != null && current.url != HOME_URL && threatFeed.isKnownPhishing(current.url)) {
                    showReputationBlocked(current.webView, current.url)
                }
                if (::securityChip.isInitialized) updateSecurityChip(current?.url ?: HOME_URL)
                if (showResult) {
                    val message = if (success) {
                        tr("База фишинга обновлена", "Phishing database updated")
                    } else {
                        tr(
                            "Не удалось обновить базу; сохранённая защита продолжает работать",
                            "Database update failed; saved protection remains active"
                        )
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startBrowserSession() {
        val target = intent?.dataString
        intent?.data = null
        if (tabs.isEmpty()) {
            val urls = restoredTabUrls
            if (!urls.isNullOrEmpty()) {
                urls.take(MAX_RESTORED_TABS).forEach { url ->
                    createTab(url.takeUnless { it == HOME_URL }, activate = false)
                }
                switchTab(tabs[restoredActiveIndex.coerceIn(0, tabs.lastIndex)].id)
                restoredTabUrls = null
            } else {
                createTab()
            }
        }
        target?.let(::openInput)
    }

    private fun runHomeChromeIntro() {
        if (!::topChrome.isInitialized || !::bottomChrome.isInitialized) return

        val motion = selectedEntranceMotion()
        if (motion == EntranceMotion.NONE) {
            listOf(topChrome, bottomChrome).forEach {
                it.animate().cancel()
                it.alpha = 1f
                it.translationX = 0f
                it.translationY = 0f
                it.scaleX = 1f
                it.scaleY = 1f
            }
            return
        }
        val offsetX = when (motion) {
            EntranceMotion.LEFT_IN -> -dp(20).toFloat()
            EntranceMotion.RIGHT_IN -> dp(20).toFloat()
            else -> 0f
        }
        val offsetY = when (motion) {
            EntranceMotion.TOP_DOWN -> -dp(14).toFloat()
            EntranceMotion.BOTTOM_UP -> dp(14).toFloat()
            else -> 0f
        }
        val startScale = if (motion == EntranceMotion.SCALE) 0.96f else 1f

        topChrome.animate().cancel()
        bottomChrome.animate().cancel()
        listOf(topChrome, bottomChrome).forEach {
            it.alpha = 0f
            it.translationX = offsetX
            it.translationY = offsetY
            it.scaleX = startScale
            it.scaleY = startScale
        }
        topChrome.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(motionDuration(50L))
            .setDuration(motionDuration(360L))
            .withLayer()
            .start()
        bottomChrome.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(motionDuration(390L))
            .setDuration(motionDuration(380L))
            .withLayer()
            .start()
    }

    private fun hasAcceptedTerms(): Boolean =
        prefs.getInt("terms_accepted_version", 0) >= TERMS_VERSION

    private fun agreementText(): String = runCatching {
        val file = if (selectedUiLanguage() == UiLanguage.ENGLISH) {
            "legal/terms_privacy_en.txt"
        } else {
            "legal/terms_privacy_ru.txt"
        }
        assets.open(file).bufferedReader().use { it.readText() }
    }.getOrElse {
        tr("Не удалось открыть текст условий. Перезапустите приложение.", "Could not open the terms. Restart the app.")
    }

    private fun showFirstRunAgreement() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(tr("Условия и конфиденциальность", "Terms and privacy"))
            .setMessage(agreementText())
            .setNegativeButton(tr("Выйти", "Exit")) { _, _ -> finishAndRemoveTask() }
            .setPositiveButton(tr("Принять", "Accept")) { _, _ ->
                val saved = prefs.edit()
                    .putInt("terms_accepted_version", TERMS_VERSION)
                    .putLong("terms_accepted_at", System.currentTimeMillis())
                    .commit()
                if (saved) {
                    startProtectedBrowserSession()
                } else {
                    Toast.makeText(this, tr("Не удалось сохранить согласие", "Could not save acceptance"), Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun showAgreementInfo() {
        AlertDialog.Builder(this)
            .setTitle(tr("Условия и конфиденциальность", "Terms and privacy"))
            .setMessage(agreementText())
            .setPositiveButton(tr("Закрыть", "Close"), null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarInsets(view: View, insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            view.setPadding(
                insets.systemWindowInsetLeft,
                insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
        }
        return insets
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
            setOnApplyWindowInsetsListener { view, insets ->
                applySystemBarInsets(view, insets)
            }
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(9), dp(12), dp(5))
        }
        topChrome = brandRow
        brandRow.addView(WegletMarkView(this), LinearLayout.LayoutParams(dp(34), dp(34)))
        brandRow.addView(TextView(this).apply {
            text = "Weglet"
            textSize = 20f
            setTextColor(ink)
            typeface = onestBold
            setPadding(dp(9), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        musicChip = TextView(this).apply {
            text = ICON_MUSIC
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(muted)
            typeface = tablerTypeface()
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = rounded(surface, 10f, withAlpha(muted, 56))
            tooltipText = tr("Музыкальный плеер", "Music player")
            setOnClickListener { showMusicPlayer() }
        }
        brandRow.addView(musicChip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(6) })
        engineChip = TextView(this).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            typeface = onestMedium
            setPadding(dp(9), dp(6), dp(9), dp(6))
            setOnClickListener { showSearchEnginePicker() }
            tooltipText = tr("Выбрать поисковик", "Choose search engine")
        }
        brandRow.addView(engineChip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(6) })
        securityChip = TextView(this).apply {
            text = tr("Защита", "Protection")
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(withAlpha(accent, 205))
            typeface = onestMedium
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = rounded(surface, 10f, withAlpha(muted, 56))
            setOnClickListener { showProtectionInfo() }
        }
        brandRow.addView(securityChip)
        root.addView(brandRow)

        addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(10))
        }
        address = EditText(this).apply {
            hint = tr("Поиск или адрес", "Search or enter address")
            textSize = 16f
            setTextColor(searchTextColor)
            setHintTextColor(searchPlaceholderColor)
            typeface = onestRegular
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(16), 0, dp(12), 0)
            val outline = selectedBarOutline()
            background = rounded(
                searchBarColor,
                selectedBarShape().radius.toFloat(),
                searchOutlineColor.takeIf { outline.width > 0 },
                outline.width
            )
            setOnEditorActionListener { _, actionId, event ->
                val submit = actionId == EditorInfo.IME_ACTION_GO ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                if (submit) openInput(text.toString())
                submit
            }
            setOnFocusChangeListener { _, focused -> if (focused) selectAll() }
        }
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(selectedBarHeight().height), 1f))
        goButton = Button(this).apply {
            text = ICON_ARROW_RIGHT
            textSize = 22f
            isAllCaps = false
            setTextColor(searchTextColor)
            typeface = tablerTypeface()
            val outline = selectedBarOutline()
            background = rounded(
                searchBarColor,
                selectedBarShape().radius.toFloat(),
                searchOutlineColor.takeIf { outline.width > 0 },
                outline.width
            )
            tooltipText = tr("Открыть", "Open")
            setOnClickListener { openInput(address.text.toString()) }
        }
        addressRow.addView(goButton, LinearLayout.LayoutParams(dp(54), dp(selectedBarHeight().height)).apply { marginStart = dp(8) })
        root.addView(addressRow)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(accent)
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)))

        downloadStatus = TextView(this).apply {
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ink)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(surface, 12f, withAlpha(muted, 70))
            visibility = View.GONE
            setOnClickListener { openDownloads() }
        }
        root.addView(downloadStatus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)).apply {
            marginStart = dp(14)
            marginEnd = dp(14)
            bottomMargin = dp(4)
        })

        pageHost = FrameLayout(this).apply { setBackgroundColor(this@MainActivity.background) }
        root.addView(pageHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val controls = FrameLayout(this).apply {
            setPadding(dp(14), dp(7), dp(14), dp(11))
            setBackgroundColor(this@MainActivity.background)
        }
        bottomChrome = controls
        val navShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(surface, 18f, withAlpha(muted, 70))
            elevation = 0f
        }
        backButton = navButton(ICON_ARROW_LEFT, tr("Назад", "Back")) { goBack() }
        forwardButton = navButton(ICON_ARROW_RIGHT, tr("Вперёд", "Forward")) { goForward() }
        homeButton = navButton(ICON_HOME, tr("Домой", "Home"), primary = true) { showHome() }
        tabsButton = navButton(tabCountIcon(1), tr("Вкладки", "Tabs")) { showTabs() }
        navShell.addView(backButton)
        navShell.addView(forwardButton)
        navShell.addView(homeButton)
        navShell.addView(tabsButton)
        navShell.addView(navButton(ICON_SETTINGS, tr("Настройки", "Settings")) { showSettings() })
        controls.addView(navShell, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54),
            Gravity.CENTER
        ))
        root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(74)))
        if (selectedToolbarPosition() == ToolbarPosition.BOTTOM) {
            root.removeView(addressRow)
            root.removeView(progress)
            root.removeView(downloadStatus)
            val controlsIndex = root.indexOfChild(controls)
            root.addView(downloadStatus, controlsIndex, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
                bottomMargin = dp(4)
            })
            root.addView(progress, controlsIndex + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)))
            root.addView(addressRow, controlsIndex + 2)
        }
        setContentView(root)
        refreshEngineUi()
    }

    private fun navButton(
        label: String,
        tooltip: String,
        primary: Boolean = false,
        action: (View) -> Unit
    ): TextView {
        return BeaconNavButton(this).apply {
            text = label
            textSize = if (label.length > 1) 17f else 21f
            gravity = Gravity.CENTER
            railActive = primary
            setTextColor(if (primary) withAlpha(accent, 220) else muted)
            typeface = tablerTypeface()
            background = rounded(Color.TRANSPARENT, 4f)
            tooltipText = tooltip
            setOnClickListener(action)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                false
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        }
    }

    private fun updateNavigationState() {
        if (!::backButton.isInitialized || !::forwardButton.isInitialized) return
        val tab = currentTab()
        val awayFromHome = tab?.url != HOME_URL
        val canGoBack = if (tab?.engineActive == true) tab.engineView.canGoBack() else tab?.webView?.canGoBack() == true
        val canGoForward = if (tab?.engineActive == true) tab.engineView.canGoForward() else tab?.webView?.canGoForward() == true
        setNavEnabled(backButton, canGoBack || awayFromHome)
        setNavEnabled(forwardButton, canGoForward)
        if (::homeButton.isInitialized) homeButton.alpha = if (awayFromHome) 1f else 0.88f
    }

    private fun setNavEnabled(button: TextView, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.32f
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun createTab(initialUrl: String? = null, activate: Boolean = true): BrowserTab {
        val webView = WebView(this)
        webView.setBackgroundColor(surface)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(false)
            saveFormData = false
            cacheMode = WebSettings.LOAD_DEFAULT
            offscreenPreRaster = false
            userAgentString = "$userAgentString Weglet/1.3.6"
        }
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        val engineView = WegletEngineView(this).apply {
            visibility = View.GONE
            setBackgroundColor(this@MainActivity.background)
            isRequestAllowed = { candidate ->
                (!candidate.startsWith("http://", true) || candidate in approvedHttp) &&
                    !isUserBlocked(candidate) &&
                    !threatFeed.isKnownPhishing(candidate) &&
                    SecurityPolicy.assessNavigation(candidate)?.level != SecurityPolicy.RiskLevel.BLOCK
            }
        }
        val tab = BrowserTab(nextTabId++, tr("Новая вкладка", "New tab"), HOME_URL, null, webView, engineView)
        engineView.onNavigationRequested = { requested ->
            when {
                requested.startsWith("https://", true) && isLikelyDownloadUrl(requested) -> {
                    if (!guardNavigation(webView, requested)) openWithWebView(tab, requested)
                }
                requested.startsWith("https://", true) -> navigate(webView, requested)
                requested.startsWith("http://", true) -> navigate(webView, requested)
                else -> runCatching { offerExternalApp(Uri.parse(requested)) }
            }
        }
        engineView.onStateChanged = { state ->
            if (tab.engineActive) {
                tab.url = state.url
                if (state.title.isNotBlank()) tab.title = state.title.take(80)
                if (tab.id == activeTabId) {
                    progress.progress = state.progress
                    progress.visibility = if (state.loading) View.VISIBLE else View.GONE
                    address.setText(tab.searchQuery ?: state.url)
                    updateChromeForUrl(state.url)
                    updateSecurityChip(state.url)
                    updateNavigationState()
                    state.error?.let {
                        if (engineFallbackPrompted.add(state.url) && !isFinishing && !isDestroyed) {
                            val securityFailure = it.startsWith("TLS/") || it.startsWith("Unsafe page")
                            val builder = AlertDialog.Builder(this@MainActivity)
                                .setTitle(
                                    if (securityFailure) tr("Опасная страница заблокирована", "Unsafe page blocked")
                                    else tr("Firefox Gecko не открыл страницу", "Firefox Gecko could not open the page")
                                )
                                .setMessage(
                                    if (securityFailure) it
                                    else "$it\n\n${tr("Открыть её через Android WebView?", "Open it with Android WebView?")}"
                                )
                            if (securityFailure) {
                                builder.setPositiveButton(tr("Понятно", "OK"), null)
                            } else {
                                builder
                                    .setNegativeButton(tr("Остаться", "Stay"), null)
                                    .setPositiveButton(tr("Открыть", "Open")) { _, _ -> openCurrentWithWebView() }
                            }
                            builder.show()
                        }
                    }
                }
            }
        }
        engineView.onNewTabRequested = { requested ->
            runOnUiThread { createTab(requested, activate = true) }
        }
        engineView.onBlockedNavigation = { requested ->
            runOnUiThread { guardNavigation(webView, requested) }
        }
        engineView.onDownloadRequested = { request ->
            runOnUiThread {
                WegletDownloadListener().onDownloadStart(
                    request.url,
                    request.userAgent,
                    request.contentDisposition,
                    request.mimeType,
                    request.contentLength
                )
            }
        }
        webView.webViewClient = WegletClient(tab)
        webView.webChromeClient = WegletChrome(tab)
        webView.setDownloadListener(WegletDownloadListener())
        tabs += tab
        pageHost.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        pageHost.addView(engineView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        if (activate) switchTab(tab.id)
        when {
            initialUrl == null -> loadHome(webView)
            activate -> navigate(webView, initialUrl)
            else -> tab.url = initialUrl
        }
        updateTabCount()
        return tab
    }

    private fun switchTab(id: Long) {
        if (activeTabId == id) return
        val previous = currentTab()
        val nextTab = tabs.firstOrNull { it.id == id } ?: return
        val direction = if (id > activeTabId) 1f else -1f
        activeTabId = id
        previous?.let { old ->
            old.engineView.setPaused(true)
            runCatching { old.webView.onPause() }
            listOf<View>(old.webView, old.engineView).forEach { surface ->
                surface.animate().cancel()
                surface.visibility = View.GONE
                surface.alpha = 1f
                surface.translationX = 0f
            }
        }
        if (!nextTab.engineActive && nextTab.webView.url == null && nextTab.url != HOME_URL) {
            navigate(nextTab.webView, nextTab.url)
        }
        val next = if (nextTab.engineActive) nextTab.engineView else nextTab.webView
        nextTab.engineView.setPaused(!nextTab.engineActive)
        if (nextTab.engineActive) runCatching { nextTab.webView.onPause() }
        else runCatching { nextTab.webView.onResume() }
        val hiddenNext = if (nextTab.engineActive) nextTab.webView else nextTab.engineView
        hiddenNext.visibility = View.GONE
        next.apply {
            animate().cancel()
            visibility = View.VISIBLE
            alpha = 0f
            translationX = dp(18) * direction
            animate().alpha(1f).translationX(0f).setDuration(210).start()
        }
        tabs.filter { it.id != id }.forEach {
            it.webView.visibility = View.GONE
            it.engineView.visibility = View.GONE
        }
        val tab = currentTab() ?: return
        address.setText(when {
            tab.url == HOME_URL -> ""
            tab.searchQuery != null -> tab.searchQuery
            else -> tab.url
        })
        updateChromeForUrl(tab.url)
        updateSecurityChip(tab.url)
        updateNavigationState()
    }

    private fun updateChromeForUrl(url: String) {
        if (!::addressRow.isInitialized) return
        if (url == HOME_URL) {
            addressRow.animate().cancel()
            addressRow.visibility = View.GONE
            addressRow.alpha = 1f
            addressRow.translationY = 0f
        } else if (addressRow.visibility != View.VISIBLE) {
            addressRow.visibility = View.VISIBLE
            addressRow.alpha = 0f
            addressRow.translationY = if (selectedToolbarPosition() == ToolbarPosition.BOTTOM) {
                dp(8).toFloat()
            } else {
                -dp(8).toFloat()
            }
            addressRow.animate().alpha(1f).translationY(0f).setDuration(220).start()
        }
    }

    private fun closeTab(id: Long) {
        val tab = tabs.firstOrNull { it.id == id } ?: return
        tabs.remove(tab)
        pageHost.removeView(tab.webView)
        pageHost.removeView(tab.engineView)
        tab.engineView.destroyEngine()
        tab.webView.destroy()
        if (tabs.isEmpty()) createTab() else if (activeTabId == id) switchTab(tabs.last().id)
        updateTabCount()
    }

    private fun currentTab() = tabs.firstOrNull { it.id == activeTabId }
    private fun currentWebView() = currentTab()?.webView

    private fun setCurrentWebContentPaused(paused: Boolean) {
        val tab = currentTab() ?: return
        tab.engineView.setPaused(paused || !tab.engineActive)
        val view = tab.webView
        if (paused || tab.engineActive) {
            runCatching { view.evaluateJavascript("document.documentElement.classList.add('weglet-paused')", null) }
            runCatching { view.onPause() }
        } else {
            runCatching { view.onResume() }
            runCatching { view.evaluateJavascript("document.documentElement.classList.remove('weglet-paused')", null) }
        }
    }

    private fun openInput(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        address.clearFocus()
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            navigate(currentWebView() ?: createTab().webView, value)
            return
        }
        if (value.contains('.') && !value.contains(' ')) {
            navigate(currentWebView() ?: createTab().webView, "https://$value")
            return
        }
        startSearch(value)
    }

    private fun navigate(webView: WebView, target: String, searchQuery: String? = null) {
        val tab = tabs.firstOrNull { it.webView == webView } ?: return
        tab.searchQuery = searchQuery
        if (guardNavigation(webView, target)) return
        if (shouldUseWegletEngine(target)) {
            loadWithWegletEngine(tab, target)
            return
        }
        showWebSurface(tab)
        prepareWebViewForUrl(webView, target)
        webView.loadUrl(target)
    }

    private fun shouldUseWegletEngine(url: String): Boolean =
        selectedBrowserEngine() == BrowserEngine.GECKO &&
            (url.startsWith("https://", true) || url.startsWith("http://", true))

    private fun loadWithWegletEngine(tab: BrowserTab, target: String) {
        tab.engineActive = true
        tab.url = target
        tab.title = Uri.parse(target).host?.removePrefix("www.") ?: "Firefox Gecko"
        tab.webView.stopLoading()
        runCatching { tab.webView.onPause() }
        tab.webView.visibility = View.GONE
        tab.engineView.visibility = if (tab.id == activeTabId) View.VISIBLE else View.GONE
        tab.engineView.setPaused(tab.id != activeTabId)
        if (tab.id == activeTabId) {
            progress.visibility = View.VISIBLE
            progress.progress = 12
            address.setText(tab.searchQuery ?: target)
            updateChromeForUrl(target)
            updateSecurityChip(target)
            updateNavigationState()
        }
        tab.engineView.loadUrl(target)
    }

    private fun showWebSurface(tab: BrowserTab) {
        tab.engineActive = false
        tab.engineView.stop()
        tab.engineView.setPaused(true)
        tab.engineView.visibility = View.GONE
        tab.webView.visibility = if (tab.id == activeTabId) View.VISIBLE else View.GONE
        if (tab.id == activeTabId) runCatching { tab.webView.onResume() }
    }

    private fun openCurrentWithWebView() {
        val tab = currentTab() ?: return
        val target = tab.url
        if (target == HOME_URL) return
        openWithWebView(tab, target)
    }

    private fun openWithWebView(tab: BrowserTab, target: String) {
        showWebSurface(tab)
        prepareWebViewForUrl(tab.webView, target)
        tab.webView.loadUrl(target)
        updateNavigationState()
    }

    private fun isLikelyDownloadUrl(url: String): Boolean {
        val path = runCatching { Uri.parse(url).path.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        return listOf(
            ".apk", ".apks", ".xapk", ".zip", ".7z", ".rar", ".tar", ".gz", ".pdf", ".doc", ".docx",
            ".xls", ".xlsx", ".ppt", ".pptx", ".mp3", ".flac", ".wav", ".mp4", ".mkv", ".exe", ".sh"
        ).any(path::endsWith)
    }

    private fun guardNavigation(webView: WebView, target: String): Boolean {
        val tab = tabs.firstOrNull { it.webView == webView }
        if (isUserBlocked(target)) {
            tab?.let(::showWebSurface)
            showUserBlocked(webView, target)
            return true
        }
        if (threatFeed.isKnownPhishing(target)) {
            tab?.let(::showWebSurface)
            showReputationBlocked(webView, target)
            return true
        }
        val risk = SecurityPolicy.assessNavigation(target)
        if (risk?.level == SecurityPolicy.RiskLevel.BLOCK) {
            tab?.let(::showWebSurface)
            showHeuristicBlocked(webView, target, risk)
            return true
        }
        if (risk != null && target !in approvedSuspicious) {
            showSuspiciousWarning(webView, target, risk)
            return true
        }
        if (target.startsWith("http://", true) && target !in approvedHttp) {
            showHttpWarning(webView, target)
            return true
        }
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun prepareWebViewForUrl(webView: WebView, url: String) {
        val restrictedHttp = url.startsWith("http://", ignoreCase = true)
        webView.settings.javaScriptEnabled = !restrictedHttp
        webView.settings.domStorageEnabled = !restrictedHttp
    }

    private fun startSearch(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return
        val engine = selectedSearchEngine()
        val tab = currentTab() ?: createTab()
        tab.title = "${engine.title} Search"
        address.setText(cleanQuery)
        navigate(tab.webView, searchUrl(engine, cleanQuery), cleanQuery)
    }

    private fun migratePrivateData() {
        if (prefs.getInt("data_schema", 0) >= 5) return
        val editor = prefs.edit()
            .remove("history")
            .remove("history_enabled")
            .remove("recent_sites")
            .remove("search_endpoint")
            .remove("local_documents")
            .putInt("data_schema", 5)
        if (!prefs.contains("search_engine")) editor.putString("search_engine", SearchEngine.DUCKDUCKGO.id)
        editor.apply()
    }

    private fun ensureDefaultQuickLaunch() {
        if (prefs.contains("pinned_sites")) return
        val defaults = listOf(
            ShortcutPreset.GITHUB,
            ShortcutPreset.YOUTUBE,
            ShortcutPreset.TELEGRAM,
            ShortcutPreset.TIKTOK,
            ShortcutPreset.INSTAGRAM
        ).mapTo(mutableSetOf()) { encodeEntry(it.title, it.url) }
        prefs.edit().putStringSet("pinned_sites", defaults).apply()
    }

    private fun selectedSearchEngine(): SearchEngine {
        val id = prefs.getString("search_engine", SearchEngine.DUCKDUCKGO.id)
        val selected = SearchEngine.entries.firstOrNull { it.id == id } ?: SearchEngine.DUCKDUCKGO
        return if (selected == SearchEngine.CUSTOM && !isValidCustomTemplate(customSearchTemplate())) {
            SearchEngine.DUCKDUCKGO
        } else {
            selected
        }
    }

    private fun selectedBrowserEngine(): BrowserEngine {
        if (RuntimeEnvironment.isEmulator()) return BrowserEngine.WEBVIEW
        val id = prefs.getString("browser_engine", BrowserEngine.GECKO.id)
        return BrowserEngine.entries.firstOrNull { it.id == id } ?: BrowserEngine.GECKO
    }

    private fun showBrowserEnginePicker() {
        val engines = BrowserEngine.entries
        val labels = arrayOf(
            tr("Firefox Gecko — приватнее, независим от Chromium", "Firefox Gecko — private and independent from Chromium"),
            tr("Android WebView — системная совместимость", "Android WebView — system compatibility")
        )
        val selected = engines.indexOf(selectedBrowserEngine())
        AlertDialog.Builder(this)
            .setTitle(tr("Движок страниц", "Page engine"))
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val engine = engines[which]
                if (engine == BrowserEngine.GECKO && RuntimeEnvironment.isEmulator()) {
                    dialog.dismiss()
                    Toast.makeText(
                        this,
                        tr(
                            "На эмуляторе используется WebView для экономии памяти",
                            "WebView is used on emulators to reduce memory use"
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setSingleChoiceItems
                }
                prefs.edit().putString("browser_engine", engine.id).apply()
                dialog.dismiss()
                val tab = currentTab()
                if (tab != null && tab.url != HOME_URL) {
                    if (engine == BrowserEngine.GECKO) {
                        navigate(tab.webView, tab.url, tab.searchQuery)
                    } else {
                        openWithWebView(tab, tab.url)
                    }
                } else {
                    currentWebView()?.let(::loadHome)
                }
                Toast.makeText(this, tr("Движок: ${engine.title}", "Engine: ${engine.title}"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(tr("Закрыть", "Close"), null)
            .show()
    }

    private fun selectedAccentTheme(): AccentTheme {
        val id = prefs.getString("accent_theme", AccentTheme.VIOLET_GRAPHITE.id)
        return AccentTheme.entries.firstOrNull { it.id == id } ?: AccentTheme.VIOLET_GRAPHITE
    }

    private fun selectedBarShape(): BarShape {
        val id = prefs.getString("bar_shape", BarShape.PILL.id)
        return BarShape.entries.firstOrNull { it.id == id } ?: BarShape.PILL
    }

    private fun selectedBarOutline(): BarOutline {
        val id = prefs.getString("bar_outline", BarOutline.MEDIUM.id)
        return BarOutline.entries.firstOrNull { it.id == id } ?: BarOutline.MEDIUM
    }

    private fun selectedBarHeight(): BarHeight {
        val id = prefs.getString("bar_height", BarHeight.STANDARD.id)
        return BarHeight.entries.firstOrNull { it.id == id } ?: BarHeight.STANDARD
    }

    private fun selectedBarWidth(): BarWidth {
        val id = prefs.getString("bar_width", BarWidth.STANDARD.id)
        return BarWidth.entries.firstOrNull { it.id == id } ?: BarWidth.STANDARD
    }

    private fun selectedToolbarPosition(): ToolbarPosition {
        val id = prefs.getString("toolbar_position", ToolbarPosition.TOP.id)
        return ToolbarPosition.entries.firstOrNull { it.id == id } ?: ToolbarPosition.TOP
    }

    private fun selectedHomePosition(): HomePosition {
        val id = prefs.getString("home_position", HomePosition.CENTER.id)
        return HomePosition.entries.firstOrNull { it.id == id } ?: HomePosition.CENTER
    }

    private fun selectedEntranceMotion(): EntranceMotion {
        val id = prefs.getString("entrance_motion", EntranceMotion.BOTTOM_UP.id)
        return EntranceMotion.entries.firstOrNull { it.id == id } ?: EntranceMotion.BOTTOM_UP
    }

    private fun selectedAnimationSpeed(): AnimationSpeed {
        val id = prefs.getString("animation_speed", AnimationSpeed.BALANCED.id)
        return AnimationSpeed.entries.firstOrNull { it.id == id } ?: AnimationSpeed.BALANCED
    }

    private fun selectedWallpaperFit(): WallpaperFit {
        val id = prefs.getString("wallpaper_fit", WallpaperFit.COVER.id)
        return WallpaperFit.entries.firstOrNull { it.id == id } ?: WallpaperFit.COVER
    }

    private fun selectedWallpaperShade(): WallpaperShade {
        val id = prefs.getString("wallpaper_shade", WallpaperShade.BALANCED.id)
        return WallpaperShade.entries.firstOrNull { it.id == id } ?: WallpaperShade.BALANCED
    }

    private fun selectedClockSize(): ClockSize {
        val id = prefs.getString("clock_size", ClockSize.STANDARD.id)
        return ClockSize.entries.firstOrNull { it.id == id } ?: ClockSize.STANDARD
    }

    private fun selectedClockFont(): ClockFont {
        val id = prefs.getString("clock_font", ClockFont.ONEST.id)
        return ClockFont.entries.firstOrNull { it.id == id } ?: ClockFont.ONEST
    }

    private fun motionDuration(base: Long): Long =
        (base * selectedAnimationSpeed().factor).toLong().coerceAtLeast(90L)

    private fun selectedStormIntensity(): StormIntensity {
        val id = prefs.getString("storm_intensity", StormIntensity.NORMAL.id)
        return StormIntensity.entries.firstOrNull { it.id == id } ?: StormIntensity.NORMAL
    }

    private fun themeColor(key: String, fallback: String): Int {
        val value = if (selectedAccentTheme() == AccentTheme.CUSTOM) prefs.getString(key, fallback) else fallback
        return runCatching { Color.parseColor(value) }.getOrElse { Color.parseColor(fallback) }
    }

    private fun colorHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

    private fun blendColor(from: Int, to: Int, amount: Float): Int {
        val ratio = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * ratio).toInt(),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * ratio).toInt(),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio).toInt()
        )
    }

    private fun selectedUiLanguage(): UiLanguage {
        val fallback = if (Locale.getDefault().language.equals("ru", true)) {
            UiLanguage.RUSSIAN
        } else {
            UiLanguage.ENGLISH
        }
        val id = prefs.getString("ui_language", fallback.id)
        return UiLanguage.entries.firstOrNull { it.id == id } ?: fallback
    }

    private fun tr(russian: String, english: String): String =
        if (selectedUiLanguage() == UiLanguage.ENGLISH) english else russian

    private fun searchEngineTitle(engine: SearchEngine): String = when (engine) {
        SearchEngine.CUSTOM -> tr("Свой поиск", "Custom search")
        else -> engine.title
    }

    private fun searchEngineShortTitle(engine: SearchEngine): String = when (engine) {
        SearchEngine.CUSTOM -> tr("Свой", "Custom")
        else -> engine.shortTitle
    }

    private fun accentThemeTitle(theme: AccentTheme): String = when (theme) {
        AccentTheme.VIOLET_GRAPHITE -> tr("Фиолетовый графит", "Violet graphite")
        AccentTheme.SIGNAL_YELLOW -> tr("Сигнальный жёлтый", "Signal yellow")
        AccentTheme.COLD_BLUE -> tr("Холодный синий", "Cold blue")
        AccentTheme.MONO -> tr("Монохром", "Monochrome")
        AccentTheme.CUSTOM -> tr("Своя палитра", "Custom palette")
    }

    private fun barShapeTitle(shape: BarShape): String = when (shape) {
        BarShape.SQUARE -> tr("Без скругления", "Square")
        BarShape.SOFT -> tr("Лёгкое скругление", "Slight rounding")
        BarShape.ROUNDED -> tr("Скруглённая", "Rounded")
        BarShape.LARGE -> tr("Крупные углы", "Large corners")
        BarShape.PILL -> tr("Капсула", "Pill")
    }

    private fun barOutlineTitle(outline: BarOutline): String = when (outline) {
        BarOutline.NONE -> tr("Без контура", "No outline")
        BarOutline.THIN -> tr("Тонкий", "Thin")
        BarOutline.MEDIUM -> tr("Средний", "Medium")
        BarOutline.BOLD -> tr("Толстый", "Bold")
    }

    private fun barHeightTitle(height: BarHeight): String = when (height) {
        BarHeight.COMPACT -> tr("Компактная", "Compact")
        BarHeight.STANDARD -> tr("Обычная", "Standard")
        BarHeight.LARGE -> tr("Высокая", "Tall")
    }

    private fun barWidthTitle(width: BarWidth): String = when (width) {
        BarWidth.NARROW -> tr("Узкая", "Narrow")
        BarWidth.STANDARD -> tr("Обычная", "Standard")
        BarWidth.FULL -> tr("На всю ширину", "Full width")
    }

    private fun toolbarPositionTitle(position: ToolbarPosition): String = when (position) {
        ToolbarPosition.TOP -> tr("Сверху", "Top")
        ToolbarPosition.BOTTOM -> tr("Снизу", "Bottom")
    }

    private fun homePositionTitle(position: HomePosition): String = when (position) {
        HomePosition.TOP -> tr("Сверху", "Top")
        HomePosition.CENTER -> tr("По центру", "Center")
        HomePosition.BOTTOM -> tr("Снизу", "Bottom")
    }

    private fun entranceMotionTitle(motion: EntranceMotion): String = when (motion) {
        EntranceMotion.BOTTOM_UP -> tr("Снизу вверх", "Bottom to top")
        EntranceMotion.TOP_DOWN -> tr("Сверху вниз", "Top to bottom")
        EntranceMotion.LEFT_IN -> tr("Слева направо", "Left to right")
        EntranceMotion.RIGHT_IN -> tr("Справа налево", "Right to left")
        EntranceMotion.SCALE -> tr("Мягкое увеличение", "Soft scale")
        EntranceMotion.FADE -> tr("Только проявление", "Fade only")
        EntranceMotion.NONE -> tr("Без анимации", "No animation")
    }

    private fun animationSpeedTitle(speed: AnimationSpeed): String = when (speed) {
        AnimationSpeed.FAST -> tr("Быстрая", "Fast")
        AnimationSpeed.BALANCED -> tr("Плавная", "Balanced")
        AnimationSpeed.RELAXED -> tr("Медленная", "Relaxed")
    }

    private fun wallpaperFitTitle(fit: WallpaperFit): String = when (fit) {
        WallpaperFit.COVER -> tr("Заполнить", "Cover")
        WallpaperFit.CONTAIN -> tr("Вместить", "Contain")
        WallpaperFit.STRETCH -> tr("Растянуть", "Stretch")
        WallpaperFit.TILE -> tr("Плитка", "Tile")
    }

    private fun wallpaperShadeTitle(shade: WallpaperShade): String = when (shade) {
        WallpaperShade.LIGHT -> tr("Светлое", "Light")
        WallpaperShade.BALANCED -> tr("Сбалансированное", "Balanced")
        WallpaperShade.DARK -> tr("Тёмное", "Dark")
    }

    private fun clockSizeTitle(size: ClockSize): String = when (size) {
        ClockSize.COMPACT -> tr("Компактные", "Compact")
        ClockSize.STANDARD -> tr("Обычные", "Standard")
        ClockSize.LARGE -> tr("Крупные", "Large")
    }

    private fun stormIntensityTitle(intensity: StormIntensity): String = when (intensity) {
        StormIntensity.CALM -> tr("Редкая", "Calm")
        StormIntensity.NORMAL -> tr("Обычная", "Normal")
        StormIntensity.HEAVY -> tr("Сильная", "Heavy")
    }

    private fun showAccentPicker() {
        val themes = AccentTheme.entries
        val labels = themes.map(::accentThemeTitle).toTypedArray()
        val checked = themes.indexOf(selectedAccentTheme())
        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(this)
            .setTitle(tr("Тема Weglet", "Weglet theme"))
            .setSingleChoiceItems(labels, checked) { _, index ->
                val theme = themes[index]
                dialog.dismiss()
                if (theme == AccentTheme.CUSTOM) {
                    switchToCustomPaletteKeepingCurrent()
                    showCustomPaletteEditor()
                } else {
                    prefs.edit().putString("accent_theme", theme.id).apply()
                    applyAccentTheme()
                }
            }
            .setNegativeButton(tr("Закрыть", "Close"), null)
            .create()
        dialog.show()
    }

    private fun applyAccentTheme() {
        Toast.makeText(this, tr("Цвет Weglet обновлён", "Weglet color updated"), Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun switchToCustomPaletteKeepingCurrent() {
        val wasCustom = selectedAccentTheme() == AccentTheme.CUSTOM
        val snapshot = mapOf(
            "custom_accent" to colorHex(accent),
            "custom_background" to colorHex(background),
            "custom_ink" to colorHex(ink),
            "custom_clock" to colorHex(clockColor),
            "custom_search_bar" to colorHex(searchBarColor),
            "custom_search_text" to colorHex(searchTextColor),
            "custom_search_placeholder" to colorHex(searchPlaceholderColor),
            "custom_search_outline" to colorHex(searchOutlineColor),
            "custom_shortcut_surface" to colorHex(shortcutSurfaceColor),
            "custom_storm" to colorHex(stormColor)
        )
        val editor = prefs.edit().putString("accent_theme", AccentTheme.CUSTOM.id)
        snapshot.forEach { (key, value) ->
            if (!wasCustom || !prefs.contains(key)) editor.putString(key, value)
        }
        editor.apply()
    }

    private fun showCustomPaletteEditor() {
        val labels = arrayOf(
            tr("Основной акцент", "Main accent"),
            tr("Фон", "Background"),
            tr("Текст интерфейса", "Interface text"),
            tr("Цвет часов", "Clock color"),
            tr("Заливка строки", "Bar fill"),
            tr("Текст в строке", "Bar text"),
            tr("Подсказка в строке", "Bar placeholder"),
            tr("Контур строки", "Bar outline"),
            tr("Плашки быстрых сайтов", "Shortcut tiles"),
            tr("Молнии", "Lightning"),
            tr("Сбросить свою палитру", "Reset custom palette")
        )
        AlertDialog.Builder(this)
            .setTitle(tr("Своя палитра", "Custom palette"))
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> showHexColorEditor("custom_accent", labels[0], colorHex(accent))
                    1 -> showHexColorEditor("custom_background", labels[1], colorHex(background))
                    2 -> showHexColorEditor("custom_ink", labels[2], colorHex(ink))
                    3 -> showHexColorEditor("custom_clock", labels[3], colorHex(clockColor))
                    4 -> showHexColorEditor("custom_search_bar", labels[4], colorHex(searchBarColor))
                    5 -> showHexColorEditor("custom_search_text", labels[5], colorHex(searchTextColor))
                    6 -> showHexColorEditor("custom_search_placeholder", labels[6], colorHex(searchPlaceholderColor))
                    7 -> showHexColorEditor("custom_search_outline", labels[7], colorHex(searchOutlineColor))
                    8 -> showHexColorEditor("custom_shortcut_surface", labels[8], colorHex(shortcutSurfaceColor))
                    9 -> showHexColorEditor("custom_storm", labels[9], colorHex(stormColor))
                    else -> {
                        prefs.edit()
                            .remove("custom_accent")
                            .remove("custom_background")
                            .remove("custom_ink")
                            .remove("custom_clock")
                            .remove("custom_search_bar")
                            .remove("custom_search_text")
                            .remove("custom_search_placeholder")
                            .remove("custom_search_outline")
                            .remove("custom_shortcut_surface")
                            .remove("custom_storm")
                            .apply()
                        applyAccentTheme()
                    }
                }
            }
            .setNegativeButton(tr("Закрыть", "Close"), null)
            .show()
    }

    private fun showHexColorEditor(key: String, title: String, current: String) {
        val input = EditText(this).apply {
            setText(current)
            setSelection(text.length)
            hint = "#RRGGBB"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Применить", "Apply")) { _, _ ->
                val value = input.text.toString().trim().uppercase(Locale.US)
                if (!Regex("^#[0-9A-F]{6}$").matches(value)) {
                    Toast.makeText(this, tr("Нужен цвет вида #AABBCC", "Use a color like #AABBCC"), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                switchToCustomPaletteKeepingCurrent()
                prefs.edit().putString(key, value).apply()
                applyAccentTheme()
            }
            .show()
    }

    private fun showBarShapePicker() = showAppearanceChoice(
        tr("Форма строки", "Bar shape"),
        BarShape.entries.map(::barShapeTitle),
        BarShape.entries.indexOf(selectedBarShape())
    ) { prefs.edit().putString("bar_shape", BarShape.entries[it].id).apply(); recreate() }

    private fun showBarOutlinePicker() = showAppearanceChoice(
        tr("Толщина контура", "Outline width"),
        BarOutline.entries.map(::barOutlineTitle),
        BarOutline.entries.indexOf(selectedBarOutline())
    ) { prefs.edit().putString("bar_outline", BarOutline.entries[it].id).apply(); recreate() }

    private fun showBarHeightPicker() = showAppearanceChoice(
        tr("Высота строки", "Bar height"),
        BarHeight.entries.map(::barHeightTitle),
        BarHeight.entries.indexOf(selectedBarHeight())
    ) { prefs.edit().putString("bar_height", BarHeight.entries[it].id).apply(); recreate() }

    private fun showBarWidthPicker() = showAppearanceChoice(
        tr("Ширина строки на домашней", "Home bar width"),
        BarWidth.entries.map(::barWidthTitle),
        BarWidth.entries.indexOf(selectedBarWidth())
    ) { prefs.edit().putString("bar_width", BarWidth.entries[it].id).apply(); currentWebView()?.let(::loadHome) }

    private fun showSearchBarPresetPicker() {
        val labels = listOf(
            tr("Белая строка", "White bar"),
            tr("Тёмная строка", "Dark bar"),
            tr("Фиолетовая строка", "Violet bar"),
            tr("Фон + цветной контур", "Background + accent outline")
        )
        AlertDialog.Builder(this)
            .setTitle(tr("Готовый стиль строки", "Bar preset"))
            .setItems(labels.toTypedArray()) { _, which ->
                switchToCustomPaletteKeepingCurrent()
                val currentAccent = colorHex(accent)
                val editor = prefs.edit().putString("bar_outline", BarOutline.MEDIUM.id)
                when (which) {
                    0 -> editor
                        .putString("custom_search_bar", "#FFFFFF")
                        .putString("custom_search_text", "#111116")
                        .putString("custom_search_placeholder", "#73737D")
                        .putString("custom_search_outline", currentAccent)
                    1 -> editor
                        .putString("custom_search_bar", "#18181E")
                        .putString("custom_search_text", "#F7F5FF")
                        .putString("custom_search_placeholder", "#8E8A99")
                        .putString("custom_search_outline", currentAccent)
                    2 -> editor
                        .putString("custom_search_bar", "#7041E6")
                        .putString("custom_search_text", "#FFFFFF")
                        .putString("custom_search_placeholder", "#D8CCFF")
                        .putString("custom_search_outline", "#9C7AFF")
                    else -> editor
                        .putString("custom_search_bar", colorHex(background))
                        .putString("custom_search_text", colorHex(ink))
                        .putString("custom_search_placeholder", colorHex(muted))
                        .putString("custom_search_outline", currentAccent)
                }
                editor.apply()
                applyAccentTheme()
            }
            .setNegativeButton(tr("Закрыть", "Close"), null)
            .show()
    }

    private fun showToolbarPositionPicker() = showAppearanceChoice(
        tr("Строка на сайтах", "Bar on websites"),
        listOf(tr("Сверху", "Top"), tr("Снизу", "Bottom")),
        ToolbarPosition.entries.indexOf(selectedToolbarPosition())
    ) { prefs.edit().putString("toolbar_position", ToolbarPosition.entries[it].id).apply(); recreate() }

    private fun showHomePositionPicker() = showAppearanceChoice(
        tr("Блок на домашней", "Home block position"),
        listOf(tr("Сверху", "Top"), tr("По центру", "Center"), tr("Снизу", "Bottom")),
        HomePosition.entries.indexOf(selectedHomePosition())
    ) { prefs.edit().putString("home_position", HomePosition.entries[it].id).apply(); currentWebView()?.let(::loadHome) }

    private fun showEntranceMotionPicker() = showAppearanceChoice(
        tr("Появление интерфейса", "Interface entrance"),
        EntranceMotion.entries.map(::entranceMotionTitle),
        EntranceMotion.entries.indexOf(selectedEntranceMotion())
    ) { prefs.edit().putString("entrance_motion", EntranceMotion.entries[it].id).apply(); currentWebView()?.let(::loadHome) }

    private fun showAnimationSpeedPicker() = showAppearanceChoice(
        tr("Скорость анимации", "Animation speed"),
        AnimationSpeed.entries.map(::animationSpeedTitle),
        AnimationSpeed.entries.indexOf(selectedAnimationSpeed())
    ) { prefs.edit().putString("animation_speed", AnimationSpeed.entries[it].id).apply(); currentWebView()?.let(::loadHome) }

    private fun showWallpaperFitPicker() = showAppearanceChoice(
        tr("Размещение обоев", "Wallpaper fit"),
        WallpaperFit.entries.map(::wallpaperFitTitle),
        WallpaperFit.entries.indexOf(selectedWallpaperFit())
    ) { prefs.edit().putString("wallpaper_fit", WallpaperFit.entries[it].id).apply(); currentWebView()?.let(::loadHome) }

    private fun showWallpaperShadePicker() = showAppearanceChoice(
        tr("Затемнение обоев", "Wallpaper shade"),
        WallpaperShade.entries.map(::wallpaperShadeTitle),
        WallpaperShade.entries.indexOf(selectedWallpaperShade())
    ) { prefs.edit().putString("wallpaper_shade", WallpaperShade.entries[it].id).apply(); currentWebView()?.let(::loadHome) }

    private fun showClockSizePicker() = showAppearanceChoice(
        tr("Размер часов", "Clock size"),
        ClockSize.entries.map(::clockSizeTitle),
        ClockSize.entries.indexOf(selectedClockSize())
    ) { prefs.edit().putString("clock_size", ClockSize.entries[it].id).apply(); currentWebView()?.let(::loadHome) }

    private fun showClockFontPicker() = showAppearanceChoice(
        tr("Шрифт часов", "Clock font"),
        ClockFont.entries.map { it.title },
        ClockFont.entries.indexOf(selectedClockFont())
    ) { prefs.edit().putString("clock_font", ClockFont.entries[it].id).apply(); currentWebView()?.let(::loadHome) }

    private fun showStormIntensityPicker() = showAppearanceChoice(
        tr("Интенсивность грозы", "Storm intensity"),
        listOf(tr("Редкая", "Calm"), tr("Обычная", "Normal"), tr("Сильная", "Heavy")),
        StormIntensity.entries.indexOf(selectedStormIntensity())
    ) { prefs.edit().putString("storm_intensity", StormIntensity.entries[it].id).apply(); currentWebView()?.let(::loadHome) }

    private fun showAppearanceChoice(title: String, labels: List<String>, checked: Int, onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                onSelected(which)
            }
            .setNegativeButton(tr("Закрыть", "Close"), null)
            .show()
    }

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        runCatching { startActivityForResult(intent, PICK_WALLPAPER_REQUEST) }
            .onFailure { Toast.makeText(this, tr("Не удалось открыть галерею", "Could not open gallery"), Toast.LENGTH_LONG).show() }
    }

    private fun clearHomeWallpaper() {
        runCatching { File(filesDir, HOME_WALLPAPER_FILE).delete() }
        prefs.edit().putBoolean("wallpaper_enabled", false).apply()
        currentWebView()?.let(::loadHome)
    }

    private fun searchUrl(engine: SearchEngine, query: String): String {
        val encoded = Uri.encode(query)
        return when (engine) {
            SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/?q=$encoded"
            SearchEngine.BING -> "https://www.bing.com/search?q=$encoded"
            SearchEngine.GOOGLE -> "https://www.google.com/search?q=$encoded"
            SearchEngine.CUSTOM -> customSearchTemplate().replace("{query}", encoded)
        }
    }

    private fun customSearchTemplate() = prefs.getString("custom_search_template", "").orEmpty().trim()

    private fun isValidCustomTemplate(template: String): Boolean {
        if (!template.startsWith("https://", ignoreCase = true) || !template.contains("{query}")) return false
        return runCatching { !Uri.parse(template.replace("{query}", "test")).host.isNullOrBlank() }.getOrDefault(false)
    }

    private fun isSearchProviderUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.US).orEmpty().removePrefix("www.")
        val path = uri.path.orEmpty()
        val hasQuery = !uri.getQueryParameter("q").isNullOrBlank()
        val isGoogle = (host == "google.com" || host.startsWith("google.")) && path.startsWith("/search")
        val customHost = runCatching {
            Uri.parse(customSearchTemplate().replace("{query}", "test")).host?.lowercase(Locale.US)?.removePrefix("www.")
        }.getOrNull()
        return (host == "duckduckgo.com" && hasQuery) ||
            (host == "bing.com" && path.startsWith("/search") && hasQuery) ||
            (isGoogle && hasQuery) ||
            (!customHost.isNullOrBlank() && host == customHost)
    }

    private fun pinnedSites(): List<SiteEntry> {
        val order = ShortcutPreset.entries.mapIndexed { index, preset -> preset.url to index }.toMap()
        return prefs.getStringSet("pinned_sites", emptySet()).orEmpty()
            .map(::decodeEntry)
            .map { (title, url) -> SiteEntry(title, url) }
            .filter { it.url.startsWith("https://") && !isUserBlocked(it.url) }
            .sortedBy { order[it.url] ?: Int.MAX_VALUE }
    }

    private fun showShortcutPicker() {
        val presets = ShortcutPreset.entries
        val selectedUrls = pinnedSites().mapTo(mutableSetOf()) { it.url }
        val checked = BooleanArray(presets.size) { presets[it].url in selectedUrls }
        AlertDialog.Builder(this)
            .setTitle(tr("Быстрый запуск", "Shortcuts"))
            .setMultiChoiceItems(presets.map { it.title }.toTypedArray(), checked) { _, index, enabled ->
                if (enabled) selectedUrls += presets[index].url else selectedUrls -= presets[index].url
            }
            .setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Готово", "Done")) { _, _ ->
                val encoded = presets.filter { it.url in selectedUrls }
                    .mapTo(mutableSetOf()) { encodeEntry(it.title, it.url) }
                prefs.edit().putStringSet("pinned_sites", encoded).apply()
                if (currentTab()?.url == HOME_URL) currentWebView()?.let(::loadHome)
            }
            .show()
    }

    private fun canonicalQuickHost(url: String): String {
        val host = Uri.parse(url).host?.removePrefix("www.")?.lowercase(Locale.US).orEmpty()
        return when {
            host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") -> "youtube.com"
            else -> host
        }
    }

    // Simple Icons 16.28.0 paths (CC0-1.0). Brand trademarks remain with their owners.
    private fun brandIcon(host: String): BrandIcon? = when {
        host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com") -> BrandIcon(
            "M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z",
            "#FF0000",
            "#171717"
        )
        host == "tiktok.com" || host.endsWith(".tiktok.com") -> BrandIcon(
            "M12.525.02c1.31-.02 2.61-.01 3.91-.02.08 1.53.63 3.09 1.75 4.17 1.12 1.11 2.7 1.62 4.24 1.79v4.03c-1.44-.05-2.89-.35-4.2-.97-.57-.26-1.1-.59-1.62-.93-.01 2.92.01 5.84-.02 8.75-.08 1.4-.54 2.79-1.35 3.94-1.31 1.92-3.58 3.17-5.91 3.21-1.43.08-2.86-.31-4.08-1.03-2.02-1.19-3.44-3.37-3.65-5.71-.02-.5-.03-1-.01-1.49.18-1.9 1.12-3.72 2.58-4.96 1.66-1.44 3.98-2.13 6.15-1.72.02 1.48-.04 2.96-.04 4.44-.99-.32-2.15-.23-3.02.37-.63.41-1.11 1.04-1.36 1.75-.21.51-.15 1.07-.14 1.61.24 1.64 1.82 3.02 3.5 2.87 1.12-.01 2.19-.66 2.77-1.61.19-.33.4-.67.41-1.06.1-1.79.06-3.57.07-5.36.01-4.03-.01-8.05.02-12.07z",
            "#F0F0F0",
            "#171717"
        )
        host == "telegram.org" || host == "t.me" || host.endsWith(".telegram.org") -> BrandIcon(
            "M11.944 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12 12 12 0 0 0 12-12A12 12 0 0 0 12 0a12 12 0 0 0-.056 0zm4.962 7.224c.1-.002.321.023.465.14a.506.506 0 0 1 .171.325c.016.093.036.306.02.472-.18 1.898-.962 6.502-1.36 8.627-.168.9-.499 1.201-.82 1.23-.696.065-1.225-.46-1.9-.902-1.056-.693-1.653-1.124-2.678-1.8-1.185-.78-.417-1.21.258-1.91.177-.184 3.247-2.977 3.307-3.23.007-.032.014-.15-.056-.212s-.174-.041-.249-.024c-.106.024-1.793 1.14-5.061 3.345-.48.33-.913.49-1.302.48-.428-.008-1.252-.241-1.865-.44-.752-.245-1.349-.374-1.297-.789.027-.216.325-.437.893-.663 3.498-1.524 5.83-2.529 6.998-3.014 3.332-1.386 4.025-1.627 4.476-1.635z",
            "#F0F0F0",
            "#171717"
        )
        host == "github.com" || host.endsWith(".github.com") -> BrandIcon(
            "M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12z",
            "#F0F0F0",
            "#171717"
        )
        host == "wikipedia.org" || host.endsWith(".wikipedia.org") -> BrandIcon(
            "M12.09 13.119c-.936 1.932-2.217 4.548-2.853 5.728-.616 1.074-1.127.931-1.532.029-1.406-3.321-4.293-9.144-5.651-12.409-.251-.601-.441-.987-.619-1.139-.181-.15-.554-.24-1.122-.271C.103 5.033 0 4.982 0 4.898v-.455l.052-.045c.924-.005 5.401 0 5.401 0l.051.045v.434c0 .119-.075.176-.225.176l-.564.031c-.485.029-.727.164-.727.436 0 .135.053.33.166.601 1.082 2.646 4.818 10.521 4.818 10.521l.136.046 2.411-4.81-.482-1.067-1.658-3.264s-.318-.654-.428-.872c-.728-1.443-.712-1.518-1.447-1.617-.207-.023-.313-.05-.313-.149v-.468l.06-.045h4.292l.113.037v.451c0 .105-.076.15-.227.15l-.308.047c-.792.061-.661.381-.136 1.422l1.582 3.252 1.758-3.504c.293-.64.233-.801.111-.947-.07-.084-.305-.22-.812-.24l-.201-.021c-.052 0-.098-.015-.145-.051-.045-.031-.067-.076-.067-.129v-.427l.061-.045c1.247-.008 4.043 0 4.043 0l.059.045v.436c0 .121-.059.178-.193.178-.646.03-.782.095-1.023.439-.12.186-.375.589-.646 1.039l-2.301 4.273-.065.135 2.792 5.712.17.048 4.396-10.438c.154-.422.129-.722-.064-.895-.197-.172-.346-.273-.857-.295l-.42-.016c-.061 0-.105-.014-.152-.045-.043-.029-.072-.075-.072-.119v-.436l.059-.045h4.961l.041.045v.437c0 .119-.074.18-.209.18-.648.03-1.127.18-1.443.421-.314.255-.557.616-.736 1.067 0 0-4.043 9.258-5.426 12.339-.525 1.007-1.053.917-1.503-.031-.571-1.171-1.773-3.786-2.646-5.71l.053-.036z",
            "#F0F0F0",
            "#171717"
        )
        host == "instagram.com" || host.endsWith(".instagram.com") -> BrandIcon(
            "M7.0301.084c-1.2768.0602-2.1487.264-2.911.5634-.7888.3075-1.4575.72-2.1228 1.3877-.6652.6677-1.075 1.3368-1.3802 2.127-.2954.7638-.4956 1.6365-.552 2.914-.0564 1.2775-.0689 1.6882-.0626 4.947.0062 3.2586.0206 3.6671.0825 4.9473.061 1.2765.264 2.1482.5635 2.9107.308.7889.72 1.4573 1.388 2.1228.6679.6655 1.3365 1.0743 2.1285 1.38.7632.295 1.6361.4961 2.9134.552 1.2773.056 1.6884.069 4.9462.0627 3.2578-.0062 3.668-.0207 4.9478-.0814 1.28-.0607 2.147-.2652 2.9098-.5633.7889-.3086 1.4578-.72 2.1228-1.3881.665-.6682 1.0745-1.3378 1.3795-2.1284.2957-.7632.4966-1.636.552-2.9124.056-1.2809.0692-1.6898.063-4.948-.0063-3.2583-.021-3.6668-.0817-4.9465-.0607-1.2797-.264-2.1487-.5633-2.9117-.3084-.7889-.72-1.4568-1.3876-2.1228C21.2982 1.33 20.628.9208 19.8378.6165 19.074.321 18.2017.1197 16.9244.0645 15.6471.0093 15.236-.005 11.977.0014 8.718.0076 8.31.0215 7.0301.0839m.1402 21.6932c-1.17-.0509-1.8053-.2453-2.2287-.408-.5606-.216-.96-.4771-1.3819-.895-.422-.4178-.6811-.8186-.9-1.378-.1644-.4234-.3624-1.058-.4171-2.228-.0595-1.2645-.072-1.6442-.079-4.848-.007-3.2037.0053-3.583.0607-4.848.05-1.169.2456-1.805.408-2.2282.216-.5613.4762-.96.895-1.3816.4188-.4217.8184-.6814 1.3783-.9003.423-.1651 1.0575-.3614 2.227-.4171 1.2655-.06 1.6447-.072 4.848-.079 3.2033-.007 3.5835.005 4.8495.0608 1.169.0508 1.8053.2445 2.228.408.5608.216.96.4754 1.3816.895.4217.4194.6816.8176.9005 1.3787.1653.4217.3617 1.056.4169 2.2263.0602 1.2655.0739 1.645.0796 4.848.0058 3.203-.0055 3.5834-.061 4.848-.051 1.17-.245 1.8055-.408 2.2294-.216.5604-.4763.96-.8954 1.3814-.419.4215-.8181.6811-1.3783.9-.4224.1649-1.0577.3617-2.2262.4174-1.2656.0595-1.6448.072-4.8493.079-3.2045.007-3.5825-.006-4.848-.0608M16.953 5.5864A1.44 1.44 0 1 0 18.39 4.144a1.44 1.44 0 0 0-1.437 1.4424M5.8385 12.012c.0067 3.4032 2.7706 6.1557 6.173 6.1493 3.4026-.0065 6.157-2.7701 6.1506-6.1733-.0065-3.4032-2.771-6.1565-6.174-6.1498-3.403.0067-6.156 2.771-6.1496 6.1738M8 12.0077a4 4 0 1 1 4.008 3.9921A3.9996 3.9996 0 0 1 8 12.0077",
            "#F0F0F0",
            "#171717"
        )
        host == "whatsapp.com" || host.endsWith(".whatsapp.com") -> BrandIcon(
            "M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z",
            "#F0F0F0",
            "#171717"
        )
        else -> null
    }

    private fun siteIconMarkup(host: String, initial: String): String {
        val icon = brandIcon(host)
        return if (icon == null) {
            "<i class=\"letter\">${html(initial)}</i>"
        } else {
            "<i class=\"brand\"><svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"${icon.path}\"/></svg></i>"
        }
    }

    private fun refreshEngineUi() {
        if (!::engineChip.isInitialized || !::address.isInitialized) return
        val engine = selectedSearchEngine()
        val (fill, textColor) = when (engine) {
            SearchEngine.DUCKDUCKGO -> surface to withAlpha(accent, 205)
            SearchEngine.BING -> surface to ink
            SearchEngine.GOOGLE -> surface to muted
            SearchEngine.CUSTOM -> chrome to ink
        }
        val shortTitle = searchEngineShortTitle(engine)
        engineChip.text = shortTitle
        engineChip.setTextColor(textColor)
        engineChip.background = rounded(fill, 10f, withAlpha(muted, 44))
        address.hint = tr(
            "Ищи в $shortTitle или введи адрес",
            "Search with $shortTitle or enter address"
        )
    }

    private fun selectSearchEngine(engine: SearchEngine) {
        prefs.edit().putString("search_engine", engine.id).apply()
        refreshEngineUi()
        if (currentTab()?.url == HOME_URL) currentWebView()?.let(::loadHome)
    }

    private fun showSearchEnginePicker() {
        val engines = SearchEngine.entries
        val selected = selectedSearchEngine()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val host = FrameLayout(this).apply {
            setBackgroundColor(withAlpha(this@MainActivity.background, 244))
            setOnClickListener { dialog.dismiss() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(surface, 20f, panelLine)
            clipToOutline = true
            setOnClickListener { }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = tr("Поисковик", "Search engine")
                textSize = 21f
                setTextColor(ink)
                typeface = onestBold
            })
            addView(TextView(this@MainActivity).apply {
                text = tr("Выберите поиск по умолчанию", "Choose the default search engine")
                textSize = 11f
                setTextColor(muted)
                typeface = onestRegular
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = tr("Готово", "Done")
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ink)
            typeface = onestBold
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(accent, 18f)
            setOnClickListener { dialog.dismiss() }
        })
        panel.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        })

        engines.forEach { engine ->
            val description = when (engine) {
                SearchEngine.DUCKDUCKGO -> tr("Приватный поиск без персонального профиля", "Private search without a personal profile")
                SearchEngine.BING -> tr("Поиск Microsoft", "Microsoft search")
                SearchEngine.GOOGLE -> tr("Поиск Google", "Google search")
                SearchEngine.CUSTOM -> tr("Ваш HTTPS-адрес поиска", "Your HTTPS search address")
            }
            val selectedRow = engine == selected
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(10), dp(8))
                background = rounded(chrome, 16f, if (selectedRow) accent else panelLine)
                setOnClickListener {
                    dialog.dismiss()
                    if (engine == SearchEngine.CUSTOM) showCustomSearchSetup() else selectSearchEngine(engine)
                }
            }
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = searchEngineTitle(engine)
                    textSize = 15f
                    setTextColor(ink)
                    typeface = onestMedium
                })
                addView(TextView(this@MainActivity).apply {
                    text = description
                    textSize = 10f
                    setTextColor(muted)
                    typeface = onestRegular
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this@MainActivity).apply {
                text = if (selectedRow) tr("Выбран", "Selected") else ""
                textSize = 11f
                setTextColor(if (selectedRow) accent else muted)
                typeface = onestMedium
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(54), ViewGroup.LayoutParams.MATCH_PARENT))
            panel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
                bottomMargin = if (engine == engines.last()) 0 else dp(6)
            })
        }
        host.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { setMargins(dp(20), 0, dp(20), 0) })
        dialog.setContentView(host)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun showLanguagePicker() {
        val languages = UiLanguage.entries
        val selected = selectedUiLanguage()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val host = FrameLayout(this).apply {
            setBackgroundColor(withAlpha(this@MainActivity.background, 244))
            setOnClickListener { dialog.dismiss() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(surface, 20f, panelLine)
            clipToOutline = true
            setOnClickListener { }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = tr("Язык", "Language")
                textSize = 21f
                setTextColor(ink)
                typeface = onestBold
            })
            addView(TextView(this@MainActivity).apply {
                text = tr("Выберите язык интерфейса", "Choose the interface language")
                textSize = 11f
                setTextColor(muted)
                typeface = onestRegular
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = tr("Готово", "Done")
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ink)
            typeface = onestBold
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(accent, 18f)
            setOnClickListener { dialog.dismiss() }
        })
        panel.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        })
        languages.forEach { language ->
            val selectedRow = language == selected
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(12), dp(8))
                background = rounded(chrome, 16f, if (selectedRow) accent else panelLine)
                setOnClickListener {
                    prefs.edit().putString("ui_language", language.id).apply()
                    dialog.dismiss()
                    recreate()
                }
            }
            row.addView(TextView(this).apply {
                text = language.title
                textSize = 16f
                setTextColor(ink)
                typeface = onestMedium
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = if (selectedRow) tr("Выбран", "Selected") else ""
                textSize = 11f
                setTextColor(accent)
                typeface = onestMedium
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.MATCH_PARENT))
            panel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply {
                bottomMargin = if (language == languages.last()) 0 else dp(8)
            })
        }
        host.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { setMargins(dp(20), 0, dp(20), 0) })
        dialog.setContentView(host)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun showCustomSearchSetup() {
        val input = EditText(this).apply {
            hint = "https://example.com/search?q={query}"
            setSingleLine(true)
            setText(customSearchTemplate())
            setSelectAllOnFocus(true)
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        AlertDialog.Builder(this)
            .setTitle(tr("Свой поисковик", "Custom search"))
            .setMessage(tr(
                "Укажите HTTPS-адрес и поставьте {query} на место поискового запроса.",
                "Enter an HTTPS address and put {query} where the search query should go."
            ))
            .setView(input)
            .setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Сохранить", "Save")) { _, _ ->
                val template = input.text.toString().trim()
                if (isValidCustomTemplate(template)) {
                    prefs.edit().putString("custom_search_template", template).apply()
                    selectSearchEngine(SearchEngine.CUSTOM)
                    Toast.makeText(this, tr("Свой поисковик включён", "Custom search enabled"), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, tr("Нужен HTTPS-адрес с {query}", "Enter an HTTPS address containing {query}"), Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun showHome() = currentWebView()?.let(::loadHome)

    private fun loadHome(webView: WebView) {
        tabs.firstOrNull { it.webView == webView }?.apply {
            searchQuery = null
            title = "Weglet"
            url = HOME_URL
            showWebSurface(this)
        }
        updateChromeForUrl(HOME_URL)
        updateNavigationState()
        runHomeChromeIntro()
        webView.loadDataWithBaseURL(HOME_URL, homePage(), "text/html", "UTF-8", null)
    }

    private fun homePage(): String {
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val stormEnabled = prefs.getBoolean("storm_enabled", true)
        val storm = selectedStormIntensity()
        val motion = selectedEntranceMotion()
        val motionX = when (motion) {
            EntranceMotion.LEFT_IN -> -18
            EntranceMotion.RIGHT_IN -> 18
            else -> 0
        }
        val motionY = when (motion) {
            EntranceMotion.BOTTOM_UP -> 14
            EntranceMotion.TOP_DOWN -> -14
            else -> 0
        }
        val motionScale = if (motion == EntranceMotion.SCALE) ".94" else "1"
        val introDuration = motionDuration(410L)
        val tileDuration = motionDuration(320L)
        val clockDelay = motionDuration(35L)
        val formDelay = motionDuration(150L)
        val noteDelay = motionDuration(225L)
        val tilesDelay = motionDuration(285L)
        val statusDelay = motionDuration(570L)
        val tileStep = motionDuration(42L)
        val siteDelayCss = (1..8).joinToString("\n") { index ->
            ".entering .dock .site:nth-child($index) { animation-delay: ${tilesDelay + (index - 1) * tileStep}ms; }"
        }
        val bodyClass = if (motion == EntranceMotion.NONE) "" else "entering"
        val homePosition = selectedHomePosition()
        val stagePadding = when (homePosition) {
            HomePosition.TOP -> "42px 20px 24px"
            HomePosition.CENTER -> "0 20px 24px"
            HomePosition.BOTTOM -> "24px 20px 54px"
        }
        val wallpaperEnabled = prefs.getBoolean("wallpaper_enabled", false) && File(filesDir, HOME_WALLPAPER_FILE).exists()
        val wallpaperFit = selectedWallpaperFit()
        val wallpaperSize = when (wallpaperFit) {
            WallpaperFit.COVER -> "cover"
            WallpaperFit.CONTAIN -> "contain"
            WallpaperFit.STRETCH -> "100% 100%"
            WallpaperFit.TILE -> "220px auto"
        }
        val wallpaperRepeat = if (wallpaperFit == WallpaperFit.TILE) "repeat" else "no-repeat"
        val wallpaperShade = String.format(Locale.US, "%.2f", selectedWallpaperShade().alpha)
        val wallpaperCss = if (wallpaperEnabled) {
            "body{background-image:url('https://weglet.local/assets/wallpaper');background-size:$wallpaperSize;background-position:center;background-repeat:$wallpaperRepeat;background-attachment:fixed}" +
                "body::before{content:'';position:fixed;z-index:0;inset:0;background:rgba(${Color.red(background)},${Color.green(background)},${Color.blue(background)},$wallpaperShade);pointer-events:none}"
        } else ""
        val stormRed = Color.red(stormColor)
        val stormGreen = Color.green(stormColor)
        val stormBlue = Color.blue(stormColor)
        val searchEngine = selectedSearchEngine()
        val htmlLanguage = selectedUiLanguage().id
        val addSiteLabel = tr("Добавить сайт", "Add site")
        val searchPlaceholder = tr("Поиск или адрес", "Search or enter address")
        val engineLabel = tr("Поиск через", "Searching with")
        val protectionLabel = tr("Защита включена", "Protection enabled")
        val quickLaunchLabel = tr("Быстрый запуск", "Shortcuts")
        val showClock = prefs.getBoolean("home_clock_enabled", true)
        val showEngineNote = prefs.getBoolean("home_engine_note_enabled", true)
        val showShortcuts = prefs.getBoolean("home_shortcuts_enabled", true)
        val showStatus = prefs.getBoolean("home_status_enabled", true)
        val clockMarkup = if (showClock) "<div id=\"clock\" class=\"clock intro-item\">$currentTime</div>" else ""
        val engineMarkup = if (showEngineNote) {
            "<div class=\"engine-note intro-item\">${html(selectedBrowserEngine().title)} · ${html(engineLabel)} ${html(searchEngineTitle(searchEngine))}</div>"
        } else ""
        val statusMarkup = if (showStatus) {
            "<div class=\"status intro-item\"><i></i><span>${html(protectionLabel)}</span></div>"
        } else ""
        val bookmarked = prefs.getStringSet("bookmarks", emptySet()).orEmpty()
            .map(::decodeEntry)
            .filterNot { (_, url) -> isSearchProviderUrl(url) }
            .map { (title, url) -> SiteEntry(title, url) }
        val quickSites = (pinnedSites() + bookmarked)
            .filter { it.url.startsWith("http") && !isUserBlocked(it.url) }
            .distinctBy { canonicalQuickHost(it.url) }
            .take(7)
        val quickMarkup = buildString {
            quickSites.forEach { site ->
                val host = Uri.parse(site.url).host?.removePrefix("www.")?.lowercase(Locale.US).orEmpty()
                val initial = host.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
                val siteClass = if (canonicalQuickHost(site.url) == "youtube.com") "site youtube" else "site"
                append(
                    """
                        <a class="$siteClass" aria-label="${html(site.title.ifBlank { host })}" href="weglet://open?url=${Uri.encode(site.url)}">
                            ${siteIconMarkup(host, initial)}
                        </a>
                    """.trimIndent()
                )
            }
            append(
                """
                    <a class="site add" aria-label="${html(addSiteLabel)}" href="weglet://shortcuts"><i>+</i></a>
                """.trimIndent()
            )
        }
        val shortcutsMarkup = if (showShortcuts) {
            "<nav class=\"dock\" aria-label=\"${html(quickLaunchLabel)}\">$quickMarkup</nav>"
        } else ""
        val barOutline = selectedBarOutline()
        val barBorderCss = if (barOutline.width == 0) "0" else "${barOutline.width}px solid var(--search-outline)"
        return """
            <!doctype html>
            <html lang="$htmlLanguage">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; font-src 'self'; img-src 'self'; form-action weglet:">
                <title>Weglet</title>
                <style>
                    @font-face {
                        font-family: Onest;
                        src: url('https://weglet.local/assets/Onest.ttf') format('truetype');
                        font-style: normal;
                        font-weight: 100 900;
                        font-display: swap;
                    }

                    @font-face {
                        font-family: JetBrainsMono;
                        src: url('https://weglet.local/assets/JetBrainsMono.ttf') format('truetype');
                        font-style: normal;
                        font-weight: 100 800;
                        font-display: swap;
                    }

                    :root {
                        --bg: ${colorHex(background)};
                        --surface: ${colorHex(surface)};
                        --search: ${colorHex(searchBarColor)};
                        --search-text: ${colorHex(searchTextColor)};
                        --search-placeholder: ${colorHex(searchPlaceholderColor)};
                        --search-outline: ${colorHex(searchOutlineColor)};
                        --clock: ${colorHex(clockColor)};
                        --shortcut: ${colorHex(shortcutSurfaceColor)};
                        --line: ${colorHex(panelLine)};
                        --text: ${colorHex(ink)};
                        --muted: ${colorHex(muted)};
                        --accent: ${colorHex(accent)};
                        --youtube: #FF0000;
                    }

                    * { box-sizing: border-box; }

                    html,
                    body {
                        height: 100%;
                        background: var(--bg);
                    }

                    body {
                        margin: 0;
                        color: var(--text);
                        font-family: Onest, sans-serif;
                        overflow-x: hidden;
                        -webkit-font-smoothing: antialiased;
                    }

                    $wallpaperCss

                    .stage {
                        position: relative;
                        z-index: 2;
                        min-height: 100vh;
                        display: flex;
                        align-items: ${homePosition.cssAlign};
                        justify-content: center;
                        padding: $stagePadding;
                    }

                    #storm {
                        position: fixed;
                        z-index: 1;
                        inset: 0;
                        width: 100%;
                        height: 100%;
                        pointer-events: none;
                        opacity: 0;
                        transition: opacity 65ms linear;
                    }

                    .frame {
                        width: 100%;
                        max-width: 390px;
                        text-align: center;
                    }

                    .clock {
                        margin: 0 0 34px;
                        color: var(--clock);
                        font: 750 ${selectedClockSize().pixels}px/1 ${selectedClockFont().cssFamily}, monospace;
                        font-variant-numeric: tabular-nums;
                        letter-spacing: -.045em;
                    }

                    form {
                        display: flex;
                        width: min(100%, ${selectedBarWidth().cssWidth});
                        height: ${selectedBarHeight().height}px;
                        margin: 0 auto;
                        border: $barBorderCss;
                        border-radius: ${selectedBarShape().radius}px;
                        background: var(--search);
                        touch-action: manipulation;
                        transition:
                            outline-color .14s ease,
                            transform .14s cubic-bezier(.18,.86,.22,1);
                    }

                    form:focus-within,
                    form:active {
                        outline: 2px solid var(--accent);
                        outline-offset: 2px;
                    }

                    input {
                        min-width: 0;
                        flex: 1;
                        padding: 0 20px;
                        border: 0;
                        outline: 0;
                        background: transparent;
                        color: var(--search-text);
                        font: 500 15px Onest, sans-serif;
                        cursor: text;
                    }

                    input::placeholder { color: var(--search-placeholder); }

                    .engine-note {
                        margin-top: 8px;
                        color: var(--muted);
                        font: 500 10px/1.2 Onest, sans-serif;
                    }

                    .dock {
                        display: flex;
                        flex-wrap: wrap;
                        justify-content: center;
                        gap: 12px;
                        margin-top: 20px;
                    }

                    .site {
                        display: flex;
                        width: 52px;
                        height: 52px;
                        align-items: center;
                        justify-content: center;
                        color: var(--muted);
                        text-decoration: none;
                        cursor: pointer;
                        touch-action: manipulation;
                        transform-origin: center;
                        -webkit-tap-highlight-color: transparent;
                        transition:
                            transform .16s cubic-bezier(.18,.86,.22,1),
                            opacity .12s ease;
                    }

                    .site:active {
                        transform: translateY(-3px) scale(1.14);
                        opacity: .92;
                    }

                    .site i {
                        display: grid;
                        width: 52px;
                        height: 52px;
                        place-items: center;
                        border: 1px solid var(--line);
                        border-radius: 16px;
                        background: var(--shortcut);
                        color: var(--text);
                        font-style: normal;
                        font-size: 17px;
                        font-weight: 700;
                    }

                    .site svg {
                        display: block;
                        width: 22px;
                        height: 22px;
                    }

                    .site svg path { fill: currentColor; }
                    .site.youtube i { color: var(--youtube); }

                    .status {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 8px;
                        margin-top: 20px;
                        color: var(--muted);
                        font: 500 10px Onest, sans-serif;
                    }

                    .status i {
                        display: block;
                        width: 4px;
                        height: 4px;
                        border-radius: 1px;
                        background: var(--accent);
                        opacity: .75;
                    }

                    .weglet-paused * { animation-play-state: paused !important; }

                    @media (max-height: 620px) {
                        .stage {
                            align-items: flex-start;
                            padding-top: 22px;
                        }

                        .clock {
                            margin: 0 0 24px;
                            font-size: ${minOf(selectedClockSize().pixels, 54)}px;
                        }

                        .dock { margin-top: 14px; }
                        .status { margin-top: 14px; }
                    }

                    @media (prefers-reduced-motion: reduce) {
                        *,
                        *::before,
                        *::after {
                            animation: none !important;
                            transition: none !important;
                            opacity: 1 !important;
                            transform: none !important;
                        }
                    }

                    .entering .intro-item {
                        opacity: 0;
                        will-change: transform, opacity;
                        animation: intro-enter ${introDuration}ms cubic-bezier(.18,.86,.22,1) both;
                    }

                    .entering .clock { animation-delay: ${clockDelay}ms; }

                    .entering form { animation-delay: ${formDelay}ms; }
                    .entering .engine-note { animation-delay: ${noteDelay}ms; }
                    .entering .status { animation-delay: ${statusDelay}ms; }

                    .entering .dock .site {
                        opacity: 0;
                        will-change: transform, opacity;
                        animation: intro-tile ${tileDuration}ms cubic-bezier(.18,.86,.22,1) both;
                    }

                    $siteDelayCss

                    @keyframes intro-enter {
                        from { opacity: 0; transform: translate3d(${motionX}px, ${motionY}px, 0) scale($motionScale); }
                        to { opacity: 1; transform: translate3d(0, 0, 0) scale(1); }
                    }

                    @keyframes intro-tile {
                        from { opacity: 0; transform: translate3d(${motionX}px, ${motionY}px, 0) scale(.90); }
                        to { opacity: 1; transform: translate3d(0, 0, 0) scale(1); }
                    }
                </style>
            </head>
            <body class="$bodyClass">
                <canvas id="storm" aria-hidden="true"></canvas>
                <main class="stage">
                    <div class="frame">
                        $clockMarkup
                        <form class="intro-item" action="weglet://search" method="get">
                            <input name="q" required autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="${html(searchPlaceholder)}">
                        </form>
                        $engineMarkup
                        $shortcutsMarkup
                        $statusMarkup
                    </div>
                </main>
                <script>
                    const clock = document.getElementById('clock');
                    function updateClock() {
                        if (!clock) return;
                        clock.textContent = new Date().toLocaleTimeString('ru-RU', {
                            hour: '2-digit',
                            minute: '2-digit'
                        });
                    }
                    function scheduleClock() {
                        if (!clock) return;
                        const wait = 60000 - (Date.now() % 60000) + 12;
                        setTimeout(() => {
                            updateClock();
                            scheduleClock();
                        }, wait);
                    }
                    updateClock();
                    scheduleClock();

                    const stormEnabled = $stormEnabled;
                    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
                    if (stormEnabled && !reducedMotion) {
                        const canvas = document.getElementById('storm');
                        const context = canvas.getContext('2d', { alpha: true, desynchronized: true });
                        let timer = 0;

                        function sizeStorm() {
                            const ratio = Math.min(window.devicePixelRatio || 1, 1.25);
                            canvas.width = Math.round(innerWidth * ratio);
                            canvas.height = Math.round(innerHeight * ratio);
                            context.setTransform(ratio, 0, 0, ratio, 0, 0);
                        }

                        function bolt(points, width, color) {
                            context.beginPath();
                            points.forEach((point, index) => {
                                if (index === 0) context.moveTo(point.x, point.y);
                                else context.lineTo(point.x, point.y);
                            });
                            context.lineWidth = width;
                            context.lineJoin = 'miter';
                            context.strokeStyle = color;
                            context.stroke();
                        }

                        function strike() {
                            if (document.hidden || document.body.classList.contains('weglet-paused')) {
                                scheduleStrike();
                                return;
                            }
                            context.clearRect(0, 0, innerWidth, innerHeight);
                            const fromRight = Math.random() > .5;
                            let x = fromRight ? innerWidth * (.74 + Math.random() * .18) : innerWidth * (.08 + Math.random() * .18);
                            let y = -6;
                            const points = [{ x: x, y: y }];
                            const segments = 11 + Math.floor(Math.random() * 5);
                            for (let index = 0; index < segments; index += 1) {
                                x += (Math.random() - .5) * 48;
                                y += innerHeight * (.036 + Math.random() * .023);
                                points.push({ x: x, y: y });
                            }
                            context.fillStyle = 'rgba($stormRed,$stormGreen,$stormBlue,.035)';
                            context.fillRect(0, 0, innerWidth, innerHeight);
                            bolt(points, ${storm.width * 2.25}, 'rgba($stormRed,$stormGreen,$stormBlue,.18)');
                            bolt(points, ${storm.width}, 'rgba($stormRed,$stormGreen,$stormBlue,.82)');
                            bolt(points, 1.15, 'rgba(${Color.red(ink)},${Color.green(ink)},${Color.blue(ink)},.96)');
                            if (points.length > 7) {
                                const roots = [points[3], points[5], points[7 + Math.floor(Math.random() * 2)]];
                                roots.forEach((root, index) => {
                                    const side = (fromRight ? -1 : 1) * (index === 0 ? 1 : -1);
                                    const branch = [root];
                                    let branchX = root.x;
                                    let branchY = root.y;
                                    for (let step = 0; step < 3 + Math.floor(Math.random() * 3); step += 1) {
                                        branchX += side * (15 + Math.random() * 18) + (Math.random() - .5) * 11;
                                        branchY += 17 + Math.random() * 16;
                                        branch.push({ x: branchX, y: branchY });
                                    }
                                    bolt(branch, ${storm.width}, 'rgba($stormRed,$stormGreen,$stormBlue,.20)');
                                    bolt(branch, 1.05, 'rgba(${Color.red(ink)},${Color.green(ink)},${Color.blue(ink)},.72)');
                                });
                            }
                            canvas.style.opacity = '.94';
                            setTimeout(() => { canvas.style.opacity = '.12'; }, 78);
                            setTimeout(() => { canvas.style.opacity = '.76'; }, 136);
                            setTimeout(() => { canvas.style.opacity = '0'; }, 214);
                            setTimeout(() => context.clearRect(0, 0, innerWidth, innerHeight), 340);
                            scheduleStrike();
                        }

                        function scheduleStrike() {
                            clearTimeout(timer);
                            timer = setTimeout(strike, ${storm.minDelay} + Math.random() * ${storm.delayRange});
                        }

                        sizeStorm();
                        addEventListener('resize', sizeStorm, { passive: true });
                        addEventListener('pagehide', () => clearTimeout(timer), { once: true });
                        scheduleStrike();
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun goBack() {
        val tab = currentTab() ?: return
        if (tab.engineActive) {
            if (!tab.engineView.goBack()) showHome()
        } else if (tab.webView.canGoBack()) tab.webView.goBack() else showHome()
        updateNavigationState()
    }

    private fun goForward() {
        val tab = currentTab() ?: return
        if (tab.engineActive) tab.engineView.goForward() else if (tab.webView.canGoForward()) tab.webView.goForward()
        updateNavigationState()
    }

    private fun showHttpWarning(webView: WebView, url: String) {
        AlertDialog.Builder(this)
            .setTitle(tr("Незащищённое соединение", "Unsecured connection"))
            .setMessage(
                tr(
                    "Этот сайт использует HTTP. Соединение не зашифровано: введённые данные могут " +
                        "перехватить или изменить. Weglet не может подтвердить подлинность сайта. " +
                        "При продолжении JavaScript и локальное хранилище будут отключены.\n\n$url",
                    "This site uses HTTP. The connection is not encrypted, so entered data can be " +
                        "intercepted or changed. Weglet cannot verify the site identity. JavaScript " +
                        "and local storage will be disabled if you continue.\n\n$url"
                )
            )
            .setNegativeButton(tr("Назад", "Back"), null)
            .setPositiveButton(tr("Открыть один раз", "Open once")) { _, _ ->
                approvedHttp += url
                tabs.firstOrNull { it.webView == webView }?.let(::showWebSurface)
                prepareWebViewForUrl(webView, url)
                webView.loadUrl(url)
            }
            .show()
    }

    private fun showSuspiciousWarning(
        webView: WebView,
        url: String,
        risk: SecurityPolicy.NavigationRisk
    ) {
        val checkedHost = risk.normalizedHost?.let {
            tr("\n\nНастоящий домен: $it", "\n\nActual domain: $it")
        }.orEmpty()
        val title = if (selectedUiLanguage() == UiLanguage.ENGLISH) "Suspicious address" else risk.title
        val reason = if (selectedUiLanguage() == UiLanguage.ENGLISH) {
            "Weglet detected deceptive or high-risk characteristics in this address."
        } else risk.reason
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("$reason$checkedHost\n\n${tr("Полный адрес", "Full address")}:\n$url")
            .setNegativeButton(tr("Назад", "Back"), null)
            .setPositiveButton(tr("Всё равно открыть", "Open anyway")) { _, _ ->
                approvedSuspicious += url
                navigate(webView, url)
            }
            .show()
    }

    private fun showHeuristicBlocked(
        webView: WebView,
        url: String,
        risk: SecurityPolicy.NavigationRisk
    ) {
        val host = risk.normalizedHost?.let { tr(" Проверенный домен: $it.", " Checked domain: $it.") }.orEmpty()
        val title = if (selectedUiLanguage() == UiLanguage.ENGLISH) "Address blocked" else risk.title
        val reason = if (selectedUiLanguage() == UiLanguage.ENGLISH) {
            "Weglet detected deceptive or high-risk characteristics in this address."
        } else risk.reason
        webView.loadDataWithBaseURL(
            HOME_URL,
            blockedPage(title, "$reason$host", url, "Weglet 1.3.6", null),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showReputationBlocked(webView: WebView, url: String) {
        webView.stopLoading()
        webView.loadDataWithBaseURL(
            HOME_URL,
            blockedPage(
                tr("Фишинговая страница заблокирована", "Phishing page blocked"),
                tr(
                    "Адрес совпал с актуальным индикатором фишинга. Совпадение проверено локально; Weglet не отправлял открываемый URL поставщику списка.",
                    "The address matched a current phishing indicator. The match was checked locally; Weglet did not send the opened URL to the list provider."
                ),
                url,
                "OpenPhish Community Feed",
                "https://openphish.com/phishing_feeds.html"
            ),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showCertificateBlocked(webView: WebView, url: String, error: SslError?) {
        val detail = when (error?.primaryError) {
            SslError.SSL_EXPIRED -> tr("Сертификат сайта просрочен.", "The site certificate has expired.")
            SslError.SSL_IDMISMATCH -> tr("Сертификат выдан для другого домена.", "The certificate belongs to another domain.")
            SslError.SSL_NOTYETVALID -> tr("Сертификат сайта ещё не действует.", "The site certificate is not valid yet.")
            SslError.SSL_UNTRUSTED -> tr("Центр сертификации не доверенный.", "The certificate authority is not trusted.")
            else -> tr("Не удалось проверить сертификат сайта.", "The site certificate could not be verified.")
        }
        val certificate = error?.certificate
        val subject = certificate?.issuedTo?.cName?.takeIf { it.isNotBlank() }
        val issuer = certificate?.issuedBy?.cName?.takeIf { it.isNotBlank() }
        val identity = buildString {
            subject?.let { append(tr(" Сертификат выдан для: $it.", " Certificate subject: $it.")) }
            issuer?.let { append(tr(" Центр сертификации: $it.", " Certificate authority: $it.")) }
        }
        webView.stopLoading()
        webView.loadDataWithBaseURL(
            HOME_URL,
            blockedPage(
                tr("TLS-соединение заблокировано", "TLS connection blocked"),
                "$detail$identity ${tr("Weglet не позволяет обойти проверку сертификата.", "Weglet does not allow certificate checks to be bypassed.")}",
                url,
                tr("Системное хранилище сертификатов Android", "Android system certificate store"),
                null
            ),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showThreatBlocked(webView: WebView, url: String, threatType: Int) {
        val reason = when (threatType) {
            WebViewClient.SAFE_BROWSING_THREAT_MALWARE -> tr("Google Safe Browsing считает, что страница может распространять вредоносное ПО.", "Google Safe Browsing reports that this page may distribute malware.")
            WebViewClient.SAFE_BROWSING_THREAT_PHISHING -> tr("Google Safe Browsing считает, что страница может использовать фишинг или социальную инженерию.", "Google Safe Browsing reports possible phishing or social engineering.")
            WebViewClient.SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE -> tr("Google Safe Browsing считает, что сайт может распространять нежелательное ПО.", "Google Safe Browsing reports possible unwanted software.")
            WebViewClient.SAFE_BROWSING_THREAT_BILLING -> tr("Google Safe Browsing обнаружил возможную угрозу, связанную с оплатой.", "Google Safe Browsing reports a possible billing threat.")
            else -> tr("Google Safe Browsing пометил страницу как потенциально опасную.", "Google Safe Browsing marked the page as potentially dangerous.")
        }
        webView.stopLoading()
        webView.loadDataWithBaseURL(
            HOME_URL,
            blockedPage(
                tr("Потенциально опасный сайт заблокирован", "Potentially dangerous site blocked"),
                reason,
                url,
                tr("Рекомендация Google Safe Browsing", "Google Safe Browsing recommendation"),
                "https://developers.google.com/safe-browsing"
            ),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showUserBlocked(webView: WebView, url: String) {
        webView.loadDataWithBaseURL(
            HOME_URL,
            blockedPage(
                tr("Сайт заблокирован", "Site blocked"),
                tr("Домен находится в вашем чёрном списке. Изменить список можно в меню Weglet.", "The domain is in your blocklist. You can change the list in Weglet settings."),
                url
            ),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showProtectionInfo() {
        val snapshot = threatFeed.snapshot()
        val feedStatus = when {
            !snapshot.enabled -> tr("выключена пользователем", "disabled by user")
            snapshot.updating -> tr("обновляется", "updating")
            snapshot.indicatorCount > 0 -> {
                val updated = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(java.util.Date(snapshot.lastUpdatedAt))
                tr("${snapshot.sourceUrlCount} адресов, локальные хэши · $updated", "${snapshot.sourceUrlCount} addresses, local hashes · $updated")
            }
            snapshot.lastUpdateFailed -> tr("пока недоступна; повторим позже", "temporarily unavailable; will retry later")
            else -> tr("ожидает первого обновления", "waiting for the first update")
        }
        val safeBrowsingStatus = when {
            currentTab()?.engineActive == true -> tr("используется только в режиме WebView", "used only in WebView mode")
            safeBrowsingReady -> tr("готов", "ready")
            else -> tr("включён в Android WebView", "enabled in Android WebView")
        }
        val certificateStatus = currentCertificateSummary()
        val trackerStatus = tr(
            "$blockedTrackerRequests запросов заблокировано в этой сессии" +
                (lastBlockedTrackerHost?.let { " · последний домен: $it" } ?: ""),
            "$blockedTrackerRequests requests blocked in this session" +
                (lastBlockedTrackerHost?.let { " · last domain: $it" } ?: "")
        )
        val htmlStatus = when (lastPageAnalysisCode) {
            10 -> tr("опасных признаков не найдено", "no dangerous signals found")
            11 -> tr("обнаружена форма, требующая внимания", "a form requiring attention was found")
            12 -> tr("заблокирована небезопасная отправка пароля", "an insecure password submission was blocked")
            13 -> tr("заблокированы признаки поддельной страницы входа", "signs of an imitation login page were blocked")
            else -> tr("проверок ещё не было", "no checks yet")
        }
        AlertDialog.Builder(this)
            .setTitle(tr("Центр защиты Weglet 1.3.6", "Weglet 1.3.6 protection center"))
            .setMessage(
                tr(
                    "Трекеры: $trackerStatus. Счётчик хранится только в памяти до закрытия Weglet; " +
                        "встроенные блокировки Gecko в него не входят.\n\n" +
                        "Локальный анализ HTML: $analyzedHtmlPages страниц, $warnedHtmlPages предупреждений, " +
                        "$blockedHtmlPhishingPages блокировок. Последняя проверка: $htmlStatus. " +
                        "Проверяются только структура формы, заголовки и признаки бренда; введённые значения " +
                        "не читаются, HTML и URL для этой проверки не отправляются. Анализ доступен в режиме WebView.\n\n" +
                        "Google Safe Browsing: $safeBrowsingStatus.\n\n" +
                        "OpenPhish: $feedStatus. Открываемые URL не отправляются — сравнение идёт локально.\n\n" +
                        "Антифишинг Weglet проверяет подделки брендов, похожие буквы, скрытые домены, " +
                        "сокращённые ссылки, IP-адреса и подозрительные формы входа.\n\n" +
                        "TLS: ошибки сертификата блокируются без обхода. HTTP открывается один раз " +
                        "в ограниченном режиме без JavaScript.\n\n$certificateStatus\n\n" +
                        "Ни один браузер не обнаруживает 100% новых угроз.",
                    "Trackers: $trackerStatus. The counter exists only in memory until Weglet closes; " +
                        "Gecko's built-in blocking is not included.\n\n" +
                        "Local HTML analysis: $analyzedHtmlPages pages, $warnedHtmlPages warnings, " +
                        "$blockedHtmlPhishingPages blocks. Last check: $htmlStatus. Only form structure, " +
                        "headings, and brand signals are inspected; entered values are not read, and this " +
                        "check sends neither HTML nor URLs. Analysis is available in WebView mode.\n\n" +
                        "Google Safe Browsing: $safeBrowsingStatus.\n\n" +
                        "OpenPhish: $feedStatus. Opened URLs are not submitted; matching runs locally.\n\n" +
                        "Weglet anti-phishing checks brand impersonation, look-alike characters, hidden domains, " +
                        "shortened links, IP addresses, and suspicious login forms.\n\n" +
                        "TLS certificate errors are blocked without bypass. HTTP can be opened once in a " +
                        "restricted mode without JavaScript.\n\n$certificateStatus\n\n" +
                        "No browser can detect 100% of new threats."
                )
            )
            .setPositiveButton(tr("Понятно", "OK"), null)
            .setNeutralButton(tr("Обновить базу", "Update database")) { _, _ -> refreshThreatFeed(force = true, showResult = true) }
            .show()
    }

    @Synchronized
    private fun recordBlockedTracker(host: String?) {
        blockedTrackerRequests += 1
        if (!host.isNullOrBlank()) lastBlockedTrackerHost = host
    }

    private fun handlePageScanResult(tab: BrowserTab, view: WebView, url: String, rawResult: String?) {
        if (tab.engineActive || tab.webView !== view || view.url != url || url == HOME_URL) return
        val result = rawResult?.trim('"')?.toIntOrNull()?.takeIf { it in 10..13 } ?: return
        analyzedHtmlPages += 1
        lastPageAnalysisCode = result
        when (result) {
            11 -> {
                warnedHtmlPages += 1
                if (lastWarnedHtmlUrl != url) {
                    lastWarnedHtmlUrl = url
                    AlertDialog.Builder(this)
                        .setTitle(tr("Проверьте форму входа", "Check this login form"))
                        .setMessage(
                            tr(
                                "Страница содержит парольную форму, которая отправляет данные на другой домен " +
                                    "или использует автоматическое перенаправление. Это бывает законно, но перед " +
                                    "вводом пароля проверьте адрес сайта. Weglet не читал содержимое полей.",
                                "This page contains a password form that submits to another domain or uses an " +
                                    "automatic redirect. That can be legitimate, but verify the site address " +
                                    "before entering a password. Weglet did not read field contents."
                            )
                        )
                        .setNegativeButton(tr("Закрыть страницу", "Close page")) { _, _ ->
                            if (view.canGoBack()) view.goBack() else loadHome(view)
                        }
                        .setPositiveButton(tr("Я проверю адрес", "I will verify it"), null)
                        .show()
                }
            }
            12, 13 -> {
                blockedHtmlPhishingPages += 1
                showHtmlPhishingBlocked(view, url, result)
            }
        }
        if (tab.id == activeTabId && result == 11) {
            setChip(tr("Проверить форму", "Check form"), accent, surface)
        }
    }

    private fun showHtmlPhishingBlocked(webView: WebView, url: String, result: Int) {
        val reason = if (result == 12) {
            tr(
                "Парольная форма пыталась отправить данные по незашифрованному HTTP-соединению.",
                "A password form attempted to submit data over an unencrypted HTTP connection."
            )
        } else {
            tr(
                "Структура формы входа, заголовки и визуальные признаки похожи на страницу известного бренда, " +
                    "но открытый домен не принадлежит этому бренду.",
                "The login form structure, headings, and visual signals resemble a known brand, but the " +
                    "opened domain does not belong to that brand."
            )
        }
        webView.stopLoading()
        webView.loadDataWithBaseURL(
            HOME_URL,
            blockedPage(
                tr("Подозрительная форма заблокирована", "Suspicious form blocked"),
                "$reason ${tr("Анализ выполнен локально без чтения введённых значений.", "Analysis ran locally without reading entered values.")}",
                url,
                tr("Локальная защита Weglet", "Weglet local protection"),
                null
            ),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun currentCertificateSummary(): String {
        val tab = currentTab() ?: return tr("Текущая страница: домашний экран.", "Current page: home screen.")
        if (!tab.url.startsWith("https://", true) || tab.url == HOME_URL) {
            return if (tab.url.startsWith("http://", true)) {
                tr("Текущая страница: HTTP, сертификата нет.", "Current page: HTTP, no certificate.")
            } else {
                tr("Текущая страница: домашний экран.", "Current page: home screen.")
            }
        }
        if (tab.engineActive) {
            return tr(
                "Gecko и WebView проверяют TLS через доверенное системное хранилище; сертификаты с ошибками не принимаются.",
                "Gecko and WebView validate TLS against trusted system roots; invalid certificates are rejected."
            )
        }
        val certificate = tab.webView.certificate ?: return tr("Текущий TLS-сертификат ещё проверяется.", "The current TLS certificate is still being checked.")
        val subject = certificate.issuedTo?.cName?.takeIf { it.isNotBlank() } ?: tr("домен не указан", "domain not specified")
        val issuer = certificate.issuedBy?.cName?.takeIf { it.isNotBlank() } ?: tr("центр не указан", "authority not specified")
        return tr("Текущий сертификат: $subject · выдан $issuer.", "Current certificate: $subject · issued by $issuer.")
    }

    private fun updateSecurityChip(url: String) {
        when {
            url == HOME_URL -> setChip(tr("Защита", "Protection"), withAlpha(accent, 185), surface)
            url.startsWith("https://", true) -> setChip("TLS ✓", ink, surface)
            url.startsWith("http://", true) -> setChip(tr("HTTP опасен", "HTTP unsafe"), accent, surface)
            else -> setChip(tr("Проверка", "Checking"), muted, chrome)
        }
    }

    private fun setChip(text: String, color: Int, fill: Int) {
        securityChip.text = text
        securityChip.setTextColor(color)
        securityChip.background = rounded(fill, 4f, withAlpha(muted, 44))
    }

    private fun showTabs() {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        lateinit var dialog: AlertDialog
        tabs.forEach { tab ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(4), dp(6))
                background = rounded(
                    if (tab.id == activeTabId) withAlpha(accent, 24) else surface,
                    4f,
                    if (tab.id == activeTabId) accent else withAlpha(muted, 54)
                )
                setOnClickListener {
                    switchTab(tab.id)
                    dialog.dismiss()
                }
            }
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = tab.title.ifBlank { tr("Без названия", "Untitled") }
                    textSize = 15f
                    setTextColor(ink)
                    maxLines = 1
                })
                addView(TextView(this@MainActivity).apply {
                    text = if (tab.url == HOME_URL) tr("Домашняя страница", "Home page") else tab.url
                    textSize = 11f
                    setTextColor(muted)
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = ICON_X
                textSize = 20f
                typeface = tablerTypeface()
                tooltipText = tr("Закрыть вкладку", "Close tab")
                setTextColor(warning)
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                setOnClickListener {
                    closeTab(tab.id)
                    dialog.dismiss()
                }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)).apply {
                bottomMargin = dp(4)
            })
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(tr("Вкладки", "Tabs"))
            .setView(ScrollView(this).apply { addView(list) })
            .setNegativeButton(tr("Закрыть", "Close"), null)
            .setPositiveButton(tr("Новая вкладка", "New tab")) { _, _ -> createTab() }
            .create()
        dialog.show()
    }

    private fun showSettings() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        var replayHomeOnDismiss = true

        val host = FrameLayout(this).apply {
            setPadding(0, 0, 0, 0)
            setBackgroundColor(this@MainActivity.background)
            setOnClickListener { dialog.dismiss() }
        }
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
            setBackgroundColor(this@MainActivity.background)
            elevation = 0f
            setOnClickListener { }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(18), dp(12))
        }
        header.addView(TextView(this).apply {
            text = tr("Настройки", "Settings")
            textSize = 30f
            setTextColor(ink)
            typeface = onestBold
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = tr("Готово", "Done")
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ink)
            typeface = onestBold
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(accent, 18f)
            tooltipText = tr("Закрыть", "Close")
            setOnClickListener { dialog.dismiss() }
        })
        sheet.addView(header)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(28))
        }
        val entranceViews = mutableListOf<View>()
        val currentPageAvailable = currentTab()?.url?.let { it != HOME_URL } == true
        val threatSnapshot = threatFeed.snapshot()
        val threatStatus = when {
            threatSnapshot.updating -> tr("Сейчас обновляется", "Updating now")
            threatSnapshot.sourceUrlCount > 0 -> tr(
                "${threatSnapshot.sourceUrlCount} известных фишинговых адресов",
                "${threatSnapshot.sourceUrlCount} known phishing addresses"
            )
            threatSnapshot.lastUpdateFailed -> tr("Последнее обновление не удалось", "Last update failed")
            else -> tr("База ещё не загружена", "Database has not been downloaded yet")
        }
        var currentGroup: LinearLayout? = null
        fun addSection(title: String) {
            val section = settingsSection(title)
            content.addView(section)
            entranceViews += section
            val group = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(surface, 20f, panelLine)
                clipToOutline = true
            }
            currentGroup = group
            content.addView(group, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) })
            entranceViews += group
        }
        fun addToCurrentGroup(view: View, height: Int) {
            val group = currentGroup ?: return
            if (group.childCount > 0) {
                group.addView(View(this).apply { setBackgroundColor(withAlpha(panelLine, 170)) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        marginStart = dp(14)
                        marginEnd = dp(14)
                    })
            }
            group.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height)))
        }
        fun addItem(
            symbol: String,
            title: String,
            subtitle: String,
            enabled: Boolean = true,
            action: () -> Unit
        ) {
            addToCurrentGroup(settingsItem(symbol, title, subtitle, enabled) {
                replayHomeOnDismiss = false
                dialog.dismiss()
                action()
            }, 62)
        }
        fun addToggle(
            symbol: String,
            title: String,
            subtitle: String,
            key: String,
            defaultValue: Boolean,
            onChanged: ((Boolean) -> Unit)? = null
        ) {
            addToCurrentGroup(settingsToggleItem(symbol, title, subtitle, key, defaultValue, onChanged), 66)
        }
        fun addChoice(
            symbol: String,
            title: String,
            subtitle: String,
            value: String,
            action: () -> Unit
        ) {
            addToCurrentGroup(settingsChoiceItem(symbol, title, subtitle, value) {
                replayHomeOnDismiss = false
                dialog.dismiss()
                action()
            }, 66)
        }

        addSection(tr("Поиск", "Search"))
        addChoice(
            ICON_SEARCH,
            tr("Поисковик по умолчанию", "Default engine"),
            tr("Используется, когда введён не адрес", "Used when what you type is not an address"),
            searchEngineTitle(selectedSearchEngine()),
            ::showSearchEnginePicker
        )

        addSection(tr("Быстрый запуск", "Shortcuts"))
        addItem(
            ICON_PLUS,
            tr("Сайты на новой вкладке", "Tiles on the new-tab page"),
            tr("Выберите ярлыки для домашнего экрана", "Choose shortcuts for the home page"),
            action = ::showShortcutPicker
        )

        addSection(tr("Текущая страница", "Current page"))
        addItem(ICON_STAR, tr("Добавить в закладки", "Add bookmark"), tr("Сохранить текущую страницу", "Save the current page"), currentPageAvailable, ::addBookmark)
        addItem(ICON_SHARE, tr("Поделиться", "Share"), tr("Отправить ссылку в другое приложение", "Send the link to another app"), currentPageAvailable, ::shareCurrentPage)
        addItem(ICON_COPY, tr("Копировать адрес", "Copy address"), tr("Скопировать URL в буфер обмена", "Copy the URL to the clipboard"), currentPageAvailable, ::copyCurrentUrl)
        addItem(
            ICON_ARROWS_EXCHANGE,
            tr("Режим совместимости", "Compatibility mode"),
            tr("Открыть страницу через системный WebView", "Open this page with the system WebView"),
            currentPageAvailable && currentTab()?.engineActive == true,
            ::openCurrentWithWebView
        )

        addSection(tr("Браузер", "Browser"))
        addChoice(
            ICON_BROWSER,
            tr("Движок страниц", "Page engine"),
            tr("Firefox Gecko или системный Android WebView", "Firefox Gecko or Android System WebView"),
            selectedBrowserEngine().title,
            ::showBrowserEnginePicker
        )
        addItem(ICON_BOOKMARK, tr("Закладки", "Bookmarks"), tr("Сохранённые страницы", "Saved pages")) {
            showStored(tr("Закладки", "Bookmarks"), "bookmarks")
        }
        addItem(ICON_DOWNLOAD, tr("Загрузки", "Downloads"), tr("Файлы и состояние скачивания", "Files and download status"), action = ::openDownloads)
        addItem(ICON_MUSIC, tr("Музыка", "Music"), tr("Локальные файлы и поиск в Audius", "Local files and Audius search"), action = ::showMusicPlayer)

        addSection(tr("Оформление", "Appearance"))
        addChoice(
            ICON_PALETTE,
            tr("Тема и цвета", "Theme and colors"),
            tr("Готовые темы или собственная HEX-палитра", "Presets or a custom HEX palette"),
            accentThemeTitle(selectedAccentTheme()),
            ::showAccentPicker
        )
        addItem(
            ICON_COLOR_SWATCH,
            tr("Своя палитра", "Custom palette"),
            tr("Фон, текст, часы, строка, плашки и молнии", "Background, text, clock, bar, tiles, and lightning"),
            action = ::showCustomPaletteEditor
        )
        addItem(
            ICON_PHOTO,
            tr("Обои домашней страницы", "Home wallpaper"),
            tr("Выбрать изображение из галереи", "Choose an image from gallery"),
            action = ::chooseWallpaper
        )
        if (File(filesDir, HOME_WALLPAPER_FILE).exists()) {
            addChoice(
                ICON_ASPECT_RATIO,
                tr("Размещение обоев", "Wallpaper fit"),
                tr("Заполнить, вместить, растянуть или повторить", "Cover, contain, stretch, or tile"),
                wallpaperFitTitle(selectedWallpaperFit()),
                ::showWallpaperFitPicker
            )
            addChoice(
                ICON_CONTRAST,
                tr("Затемнение обоев", "Wallpaper shade"),
                tr("Сохраняет читаемость часов и строки", "Keeps the clock and bar readable"),
                wallpaperShadeTitle(selectedWallpaperShade()),
                ::showWallpaperShadePicker
            )
            addItem(
                ICON_PHOTO_OFF,
                tr("Убрать обои", "Remove wallpaper"),
                tr("Вернуть однотонный фон темы", "Restore the solid theme background"),
                action = ::clearHomeWallpaper
            )
        }

        addSection(tr("Строка поиска", "Search bar"))
        addItem(
            ICON_PAINT,
            tr("Готовый стиль", "Bar preset"),
            tr("Белая, тёмная, фиолетовая или контурная", "White, dark, violet, or outline"),
            action = ::showSearchBarPresetPicker
        )
        addChoice(
            ICON_RECTANGLE,
            tr("Форма строки", "Bar shape"),
            tr("От прямых углов до капсулы", "From square corners to a pill"),
            barShapeTitle(selectedBarShape()),
            ::showBarShapePicker
        )
        addChoice(
            ICON_BORDER_ALL,
            tr("Контур строки", "Bar outline"),
            tr("Без контура, тонкий, средний или толстый", "None, thin, medium, or bold"),
            barOutlineTitle(selectedBarOutline()),
            ::showBarOutlinePicker
        )
        addChoice(
            ICON_ARROWS_VERTICAL,
            tr("Высота строки", "Bar height"),
            tr("Компактная, обычная или высокая", "Compact, standard, or tall"),
            barHeightTitle(selectedBarHeight()),
            ::showBarHeightPicker
        )
        addChoice(
            ICON_ARROWS_HORIZONTAL,
            tr("Ширина на домашней", "Home bar width"),
            tr("Узкая, обычная или на всю ширину", "Narrow, standard, or full width"),
            barWidthTitle(selectedBarWidth()),
            ::showBarWidthPicker
        )
        addChoice(
            ICON_LAYOUT_NAVBAR,
            tr("Строка на сайтах", "Bar on websites"),
            tr("Переместить адресную строку вверх или вниз", "Move the address bar to top or bottom"),
            toolbarPositionTitle(selectedToolbarPosition()),
            ::showToolbarPositionPicker
        )

        addSection(tr("Домашний экран", "Home screen"))
        addChoice(
            ICON_LAYOUT_ALIGN_CENTER,
            tr("Домашний блок", "Home block"),
            tr("Часы и поиск сверху, в центре или снизу", "Clock and search at top, center, or bottom"),
            homePositionTitle(selectedHomePosition()),
            ::showHomePositionPicker
        )
        addChoice(
            ICON_CLOCK_HOUR_12,
            tr("Размер часов", "Clock size"),
            tr("Компактные, обычные или крупные", "Compact, standard, or large"),
            clockSizeTitle(selectedClockSize()),
            ::showClockSizePicker
        )
        addChoice(
            ICON_TYPOGRAPHY,
            tr("Шрифт часов", "Clock font"),
            tr("Onest или моноширинный JetBrains Mono", "Onest or monospaced JetBrains Mono"),
            selectedClockFont().title,
            ::showClockFontPicker
        )
        addToggle(ICON_EYE, tr("Показывать часы", "Show clock"), tr("Большое время на домашней странице", "Large time on the home page"), "home_clock_enabled", true) {
            if (currentTab()?.url == HOME_URL) currentWebView()?.let(::loadHome)
        }
        addToggle(ICON_BROWSER, tr("Подпись поисковика", "Engine caption"), tr("Движок страниц и выбранный поиск", "Page engine and selected search provider"), "home_engine_note_enabled", true) {
            if (currentTab()?.url == HOME_URL) currentWebView()?.let(::loadHome)
        }
        addToggle(ICON_LAYOUT_GRID, tr("Быстрый запуск", "Show shortcuts"), tr("Ярлыки сайтов на домашнем экране", "Site shortcuts on the home screen"), "home_shortcuts_enabled", true) {
            if (currentTab()?.url == HOME_URL) currentWebView()?.let(::loadHome)
        }
        addToggle(ICON_SHIELD_CHECK, tr("Статус защиты", "Protection status"), tr("Короткая подпись под ярлыками", "Short caption below shortcuts"), "home_status_enabled", true) {
            if (currentTab()?.url == HOME_URL) currentWebView()?.let(::loadHome)
        }

        addSection(tr("Движение и эффекты", "Motion and effects"))
        addChoice(
            ICON_TRANSITION_TOP,
            tr("Анимация появления", "Entrance animation"),
            tr("Направление или полное отключение", "Direction or completely disabled"),
            entranceMotionTitle(selectedEntranceMotion()),
            ::showEntranceMotionPicker
        )
        addChoice(
            ICON_SPEEDTEST,
            tr("Скорость анимации", "Animation speed"),
            tr("Быстрая, плавная или медленная", "Fast, balanced, or relaxed"),
            animationSpeedTitle(selectedAnimationSpeed()),
            ::showAnimationSpeedPicker
        )
        addToggle(ICON_BOLT, tr("Гроза на фоне", "Background storm"), tr("Короткие вспышки, можно отключить", "Short flashes that can be disabled"), "storm_enabled", true) {
            if (currentTab()?.url == HOME_URL) currentWebView()?.let(::loadHome)
        }
        addChoice(
            ICON_ADJUSTMENTS,
            tr("Интенсивность грозы", "Storm intensity"),
            tr("Частота, толщина и яркость молний", "Lightning frequency, width, and brightness"),
            stormIntensityTitle(selectedStormIntensity()),
            ::showStormIntensityPicker
        )

        addSection(tr("Приватность", "Privacy"))
        addToggle(
            ICON_SHIELD_SEARCH,
            tr("База фишинга", "Phishing database"),
            tr("OpenPhish; адреса проверяются локально", "OpenPhish; addresses are checked locally"),
            "openphish_enabled",
            true
        ) { enabled ->
            if (enabled) refreshThreatFeed(force = true, showResult = true)
            else Toast.makeText(this, tr("Локальная база фишинга выключена", "Local phishing database disabled"), Toast.LENGTH_SHORT).show()
        }
        addItem(ICON_REFRESH, tr("Обновить базу угроз", "Update threat database"), threatStatus, threatSnapshot.enabled) {
            refreshThreatFeed(force = true, showResult = true)
        }
        addToggle(ICON_MICROPHONE, tr("Запросы сайтов", "Site requests"), tr("Камера и микрофон после подтверждения", "Camera and microphone after confirmation"), "site_permissions_enabled", false)
        addItem(ICON_BAN, tr("Заблокировать этот сайт", "Block this site"), tr("Добавить домен в чёрный список", "Add the domain to the blocklist"), currentPageAvailable, ::blockCurrentSite)
        addItem(ICON_LIST, tr("Заблокированные сайты", "Blocked sites"), tr("Управление чёрным списком", "Manage the blocklist"), action = ::showBlockedSites)
        addItem(ICON_SHIELD_LOCK, tr("Центр защиты и трекеры", "Protection center & trackers"), tr("Счётчики, антифишинг, HTTP/TLS и базы угроз", "Counters, anti-phishing, HTTP/TLS, and threat feeds"), action = ::showProtectionInfo)
        addItem(ICON_TRASH, tr("Очистить данные", "Clear data"), tr("Cookie, кэш и данные сайтов", "Cookies, cache and site data"), action = ::confirmClearData)

        addSection(tr("Язык", "Language"))
        addChoice(
            ICON_LANGUAGE,
            tr("Язык интерфейса", "Interface language"),
            tr("Меняет весь интерфейс Weglet", "Changes the full Weglet interface"),
            selectedUiLanguage().title,
            ::showLanguagePicker
        )

        addSection(tr("Приложение", "App"))
        addItem(ICON_FILE_DESCRIPTION, tr("Условия и конфиденциальность", "Terms and privacy"), tr("Правила и обработка данных", "Rules and data handling"), action = ::showAgreementInfo)
        addItem(ICON_INFO_CIRCLE, tr("О Weglet", "About Weglet"), tr("Версия, возможности и ограничения", "Version, features and limitations"), action = ::showAbout)

        sheet.addView(ScrollView(this).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        host.addView(sheet, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))
        dialog.setContentView(host)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(this@MainActivity.background))
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }
        dialog.setOnDismissListener {
            setCurrentWebContentPaused(false)
            if (replayHomeOnDismiss && currentTab()?.url == HOME_URL) {
                currentWebView()?.let(::loadHome)
            }
        }
        dialog.show()
        setCurrentWebContentPaused(true)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        val settingsMotion = selectedEntranceMotion()
        if (settingsMotion == EntranceMotion.NONE) {
            header.alpha = 1f
            header.translationX = 0f
            header.translationY = 0f
            header.scaleX = 1f
            header.scaleY = 1f
            entranceViews.forEach {
                it.alpha = 1f
                it.translationX = 0f
                it.translationY = 0f
                it.scaleX = 1f
                it.scaleY = 1f
            }
        } else {
            val offsetX = when (settingsMotion) {
                EntranceMotion.LEFT_IN -> -dp(16).toFloat()
                EntranceMotion.RIGHT_IN -> dp(16).toFloat()
                else -> 0f
            }
            val offsetY = when (settingsMotion) {
                EntranceMotion.TOP_DOWN -> -dp(12).toFloat()
                EntranceMotion.BOTTOM_UP -> dp(12).toFloat()
                else -> 0f
            }
            val startScale = if (settingsMotion == EntranceMotion.SCALE) 0.97f else 1f
            header.alpha = 0f
            header.translationX = offsetX
            header.translationY = offsetY
            header.scaleX = startScale
            header.scaleY = startScale
            header.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(motionDuration(220L))
                .withLayer()
                .start()
            entranceViews.forEachIndexed { index, view ->
                view.alpha = 0f
                view.translationX = offsetX
                view.translationY = offsetY
                view.scaleX = startScale
                view.scaleY = startScale
                view.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(motionDuration(55L + index * 18L))
                    .setDuration(motionDuration(255L))
                    .withLayer()
                    .start()
            }
        }
    }

    private fun settingsSection(title: String): TextView {
        return TextView(this).apply {
            text = title.uppercase(Locale.getDefault())
            textSize = 12f
            setTextColor(ink)
            typeface = onestBold
            setPadding(dp(4), dp(16), dp(4), dp(9))
        }
    }

    private fun settingsItem(
        symbol: String,
        title: String,
        subtitle: String,
        enabled: Boolean,
        action: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(10), dp(4))
            background = ColorDrawable(Color.TRANSPARENT)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.38f

            addView(TextView(this@MainActivity).apply {
                text = symbol
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(accent)
                typeface = tablerTypeface()
            }, LinearLayout.LayoutParams(dp(28), dp(34)).apply { marginEnd = dp(8) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 15f
                    setTextColor(ink)
                    typeface = onestMedium
                    maxLines = 1
                })
                addView(TextView(this@MainActivity).apply {
                    text = subtitle
                    textSize = 10f
                    setTextColor(muted)
                    typeface = onestRegular
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = ICON_CHEVRON_RIGHT
                textSize = 22f
                setTextColor(muted)
                gravity = Gravity.CENTER
                typeface = tablerTypeface()
            }, LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.MATCH_PARENT))

            if (enabled) setOnClickListener {
                animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).withEndAction {
                    scaleX = 1f
                    scaleY = 1f
                    action()
                }.start()
            }
        }
    }

    private fun settingsChoiceItem(
        symbol: String,
        title: String,
        subtitle: String,
        value: String,
        action: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(10), dp(4))
            background = ColorDrawable(Color.TRANSPARENT)

            addView(TextView(this@MainActivity).apply {
                text = symbol
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(accent)
                typeface = tablerTypeface()
            }, LinearLayout.LayoutParams(dp(28), dp(34)).apply { marginEnd = dp(8) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 15f
                    setTextColor(ink)
                    typeface = onestMedium
                    maxLines = 1
                })
                addView(TextView(this@MainActivity).apply {
                    text = subtitle
                    textSize = 10f
                    setTextColor(muted)
                    typeface = onestRegular
                    maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(ink)
                typeface = onestMedium
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = rounded(chrome, 12f, withAlpha(accent, 160))
            })
            addView(TextView(this@MainActivity).apply {
                text = ICON_CHEVRON_RIGHT
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(muted)
                typeface = tablerTypeface()
            }, LinearLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.MATCH_PARENT))
            setOnClickListener {
                animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).withEndAction {
                    scaleX = 1f
                    scaleY = 1f
                    action()
                }.start()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun settingsToggleItem(
        symbol: String,
        title: String,
        subtitle: String,
        key: String,
        defaultValue: Boolean,
        onChanged: ((Boolean) -> Unit)? = null
    ): View {
        return LinearLayout(this).apply row@ {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(8), dp(4))
            background = ColorDrawable(Color.TRANSPARENT)
            addView(TextView(this@MainActivity).apply {
                text = symbol; textSize = 15f; gravity = Gravity.CENTER; setTextColor(accent)
                typeface = tablerTypeface()
            }, LinearLayout.LayoutParams(dp(28), dp(34)).apply { marginEnd = dp(8) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = title; textSize = 15f; setTextColor(ink)
                    typeface = onestMedium
                })
                addView(TextView(this@MainActivity).apply {
                    text = subtitle; textSize = 10f; setTextColor(muted); typeface = onestRegular; maxLines = 1
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(this@MainActivity).apply {
                isChecked = prefs.getBoolean(key, defaultValue)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                    onChanged?.invoke(checked)
                }
            }, LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Weglet 1.3.6")
            .setMessage(
                tr(
                    "Weglet — браузер для Android 10–17. История посещений не записывается, " +
                        "а поисковый запрос отправляется только после вашего нажатия.\n\n" +
                    "Для страниц можно выбрать Firefox Gecko или системный Android WebView. " +
                        "Gecko не зависит от Chromium, а WebView остаётся режимом совместимости.\n\n" +
                    "Включены безопасный просмотр движка, локальная база OpenPhish, строгая проверка " +
                        "сертификатов, блокировка трекеров и защита от поддельных доменов.\n\n" +
                        "Домашний экран настраивается: фон и обои, цвета текста и часов, заливка и " +
                        "контур строки, форма, размер, положение, видимость блоков и скорость анимации.\n\n" +
                        "Плеер воспроизводит локальные файлы и открытые треки Audius. " +
                        "Грозу на домашнем фоне можно отключить в настройках.\n\n" +
                        "Разработчик версии для ПК: @avrety\n" +
                        "Разработчик Android-версии: @Durov500\n\n" +
                        "Лицензия приложения: Apache License 2.0\n" +
                        "Шрифты: Onest и JetBrains Mono — SIL OFL 1.1\n" +
                        "Иконки: Tabler Icons — MIT License\n\n" +
                        "Защита снижает риск, но не гарантирует обнаружение каждой новой угрозы.",
                    "Weglet is a browser for Android 10–17. Browsing history is not recorded, " +
                        "and a search query is sent only after you submit it.\n\n" +
                    "Pages can use Firefox Gecko or Android System WebView. Gecko is independent of " +
                        "Chromium, while WebView remains available as a compatibility mode.\n\n" +
                    "Engine safe browsing, a local OpenPhish database, strict certificate checks, " +
                        "tracker blocking, and look-alike domain protection are enabled.\n\n" +
                        "The home screen supports wallpapers, clock and text colors, search fill and outline, " +
                        "shape, size, position, section visibility, and entrance animation speed.\n\n" +
                        "The player supports local files and public Audius tracks. " +
                        "The background storm can be disabled in Settings.\n\n" +
                        "Desktop version developer: @avrety\n" +
                        "Android version developer: @Durov500\n\n" +
                        "App license: Apache License 2.0\n" +
                        "Fonts: Onest and JetBrains Mono — SIL OFL 1.1\n" +
                        "Icons: Tabler Icons — MIT License\n\n" +
                        "Protection reduces risk but cannot guarantee detection of every new threat."
                )
            )
            .setPositiveButton("Android · @Durov500") { _, _ -> openTelegramProfile("Durov500") }
            .setNegativeButton(tr("ПК · @avrety", "Desktop · @avrety")) { _, _ -> openTelegramProfile("avrety") }
            .setNeutralButton(tr("Готово", "Done"), null)
            .show()
    }

    private fun openTelegramProfile(handle: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$handle")))
        }.onFailure {
            Toast.makeText(this, tr("Не удалось открыть ссылку Telegram", "Could not open the Telegram link"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMusicPlayer() {
        playerDialog?.dismiss()
        if (mediaPlayer == null && playerTitle.isBlank()) {
            playerTitle = tr("Ничего не играет", "Nothing is playing")
            playerArtist = tr("Выберите аудиофайл или найдите трек", "Choose an audio file or find a track")
        }
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(18))
            background = rounded(this@MainActivity.background, 20f, withAlpha(muted, 72))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = tr("Плеер", "Player")
            textSize = 22f
            setTextColor(ink)
            typeface = onestBold
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = ICON_X
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(ink)
            typeface = tablerTypeface()
            tooltipText = tr("Закрыть", "Close")
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        sheet.addView(header)

        playerTitleView = TextView(this).apply {
            textSize = 20f
            maxLines = 2
            setTextColor(ink)
            typeface = onestBold
            setPadding(0, dp(22), 0, dp(4))
        }
        sheet.addView(playerTitleView)
        playerArtistView = TextView(this).apply {
            textSize = 13f
            maxLines = 1
            setTextColor(muted)
            typeface = onestRegular
        }
        sheet.addView(playerArtistView)

        playerSeekBar = SeekBar(this).apply {
            max = 1
            progress = 0
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) runCatching { mediaPlayer?.seekTo(progress) }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        sheet.addView(playerSeekBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(42)
        ).apply { topMargin = dp(14) })
        playerTimeView = TextView(this).apply {
            textSize = 10f
            setTextColor(muted)
            typeface = onestMedium
            gravity = Gravity.END
        }
        sheet.addView(playerTimeView)

        val playback = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(15), 0, 0)
        }
        playback.addView(playerAction(ICON_FILE_MUSIC, tr("Выбрать аудиофайл", "Choose audio file")) { pickLocalAudio() })
        playback.addView(playerAction(ICON_PLAYER_PLAY, tr("Воспроизвести или поставить на паузу", "Play or pause")) { togglePlayer() }.also { playerPlayButton = it })
        playback.addView(playerAction(ICON_PLAYER_STOP, tr("Остановить", "Stop")) { stopPlayer() })
        playback.addView(playerAction(ICON_SEARCH, tr("Найти в Audius", "Search Audius")) { askAudiusQuery() })
        sheet.addView(playback)

        playerSourceView = TextView(this).apply {
            text = tr("Открыть страницу трека в Audius ↗", "Open track page in Audius ↗")
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(accent)
            typeface = onestMedium
            setPadding(0, dp(18), 0, dp(5))
            setOnClickListener {
                playerPageUrl?.let(::openInput)
            }
        }
        sheet.addView(playerSourceView)
        sheet.addView(TextView(this).apply {
            text = tr(
                "Локальные файлы не отправляются в сеть. Онлайн-поиск и поток — Audius.",
                "Local files are not uploaded. Online search and streaming use Audius."
            )
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(muted)
            typeface = onestRegular
            setPadding(dp(8), dp(5), dp(8), 0)
        })

        dialog.setContentView(sheet)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setOnDismissListener {
            playerHandler.removeCallbacks(playerProgress)
            playerDialog = null
            playerTitleView = null
            playerArtistView = null
            playerTimeView = null
            playerSeekBar = null
            playerPlayButton = null
            playerSourceView = null
        }
        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels - dp(28)).coerceAtLeast(dp(300)),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        playerDialog = dialog
        updatePlayerUi()
        playerHandler.removeCallbacks(playerProgress)
        playerHandler.post(playerProgress)
        sheet.alpha = 0f
        sheet.translationY = dp(18).toFloat()
        sheet.animate().alpha(1f).translationY(0f).setDuration(170).start()
    }

    private fun playerAction(label: String, tooltip: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 21f
            gravity = Gravity.CENTER
            setTextColor(ink)
            typeface = tablerTypeface()
            tooltipText = tooltip
            background = rounded(surface, 14f, withAlpha(muted, 66))
            setOnClickListener {
                animate().scaleX(.94f).scaleY(.94f).setDuration(70).withEndAction {
                    scaleX = 1f
                    scaleY = 1f
                    action()
                }.start()
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun pickLocalAudio() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(picker, PICK_AUDIO_REQUEST) }
            .onFailure { Toast.makeText(this, tr("Не удалось открыть выбор файлов", "Could not open the file picker"), Toast.LENGTH_LONG).show() }
    }

    private fun askAudiusQuery() {
        val input = EditText(this).apply {
            hint = tr("Исполнитель или трек", "Artist or track")
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(18), dp(6), dp(18), dp(6))
        }
        AlertDialog.Builder(this)
            .setTitle(tr("Найти музыку в Audius", "Find music in Audius"))
            .setMessage(tr("Показываются треки из открытого каталога Audius.", "Tracks are shown from the public Audius catalog."))
            .setView(input)
            .setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Найти", "Search")) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) searchAudius(query)
            }
            .show()
    }

    private fun searchAudius(query: String) {
        Toast.makeText(this, tr("Ищу в Audius…", "Searching Audius…"), Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                val endpoint = "https://api.audius.co/v1/tracks/search?query=$encoded&limit=12&app_name=Weglet"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Weglet/1.3.6 Android")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        error("Audius HTTP ${connection.responseCode}")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val data = JSONObject(body).optJSONArray("data")
                    buildList {
                        if (data != null) for (index in 0 until data.length()) {
                            val item = data.optJSONObject(index) ?: continue
                            val id = item.optString("id")
                            val title = item.optString("title")
                            if (id.isBlank() || title.isBlank()) continue
                            val artist = item.optJSONObject("user")?.optString("name")
                                ?.takeIf { it.isNotBlank() } ?: tr("Неизвестный исполнитель", "Unknown artist")
                            add(MusicTrack(
                                id = id,
                                title = title,
                                artist = artist,
                                durationSeconds = item.optInt("duration", 0),
                                permalink = item.optString("permalink")
                            ))
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
            runOnUiThread {
                result.onSuccess(::showAudiusResults).onFailure {
                    Toast.makeText(this, tr("Audius сейчас не ответил. Попробуйте ещё раз.", "Audius did not respond. Try again."), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showAudiusResults(tracks: List<MusicTrack>) {
        if (tracks.isEmpty()) {
            Toast.makeText(this, tr("Ничего не найдено", "Nothing found"), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = tracks.map {
            val duration = if (it.durationSeconds > 0) " · ${formatPlayerTime(it.durationSeconds * 1000)}" else ""
            "${it.title}\n${it.artist}$duration"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(tr("Audius · результаты", "Audius · results"))
            .setItems(labels) { _, index -> playAudiusTrack(tracks[index]) }
            .setNegativeButton(tr("Закрыть", "Close"), null)
            .show()
    }

    private fun playAudiusTrack(track: MusicTrack) {
        val page = when {
            track.permalink.startsWith("http") -> track.permalink
            track.permalink.startsWith("/") -> "https://audius.co${track.permalink}"
            else -> "https://audius.co"
        }
        val stream = "https://api.audius.co/v1/tracks/${Uri.encode(track.id)}/stream?app_name=Weglet"
        preparePlayer(track.title, "${track.artist} · Audius", page) { player ->
            player.setDataSource(stream)
        }
    }

    private fun playLocalAudio(uri: Uri, name: String) {
        preparePlayer(name, tr("Локальный файл", "Local file"), null) { player ->
            player.setDataSource(this, uri)
        }
    }

    private fun preparePlayer(
        title: String,
        artist: String,
        pageUrl: String?,
        setSource: (MediaPlayer) -> Unit
    ) {
        releasePlayer()
        playerTitle = title
        playerArtist = artist
        playerPageUrl = pageUrl
        playerLoading = true
        updatePlayerUi()
        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build())
        player.setOnPreparedListener {
            if (mediaPlayer !== it) return@setOnPreparedListener
            playerLoading = false
            it.start()
            updatePlayerUi()
        }
        player.setOnCompletionListener {
            if (mediaPlayer === it) updatePlayerUi()
        }
        player.setOnErrorListener { failed, _, _ ->
            if (mediaPlayer === failed) {
                playerLoading = false
                releasePlayer()
                Toast.makeText(this, tr("Не удалось воспроизвести этот трек", "Could not play this track"), Toast.LENGTH_LONG).show()
                updatePlayerUi()
            }
            true
        }
        runCatching {
            setSource(player)
            player.prepareAsync()
        }.onFailure {
            playerLoading = false
            releasePlayer()
            Toast.makeText(this, tr("Не удалось открыть аудио", "Could not open audio"), Toast.LENGTH_LONG).show()
            updatePlayerUi()
        }
    }

    private fun togglePlayer() {
        val player = mediaPlayer
        if (playerLoading) {
            Toast.makeText(this, tr("Трек загружается…", "Track is loading…"), Toast.LENGTH_SHORT).show()
            return
        }
        if (player == null) {
            pickLocalAudio()
            return
        }
        runCatching {
            if (player.isPlaying) player.pause() else player.start()
        }.onFailure {
            Toast.makeText(this, tr("Плеер ещё не готов", "Player is not ready yet"), Toast.LENGTH_SHORT).show()
        }
        updatePlayerUi()
    }

    private fun stopPlayer() {
        releasePlayer()
        playerTitle = tr("Ничего не играет", "Nothing is playing")
        playerArtist = tr("Выберите аудиофайл или найдите трек", "Choose an audio file or find a track")
        playerPageUrl = null
        updatePlayerUi()
    }

    private fun releasePlayer() {
        val old = mediaPlayer
        mediaPlayer = null
        runCatching { old?.stop() }
        runCatching { old?.reset() }
        runCatching { old?.release() }
        playerLoading = false
    }

    private fun updatePlayerUi() {
        val playing = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)
        playerTitleView?.text = playerTitle
        playerArtistView?.text = if (playerLoading) "$playerArtist · ${tr("загрузка", "loading")}" else playerArtist
        playerPlayButton?.text = if (playing) ICON_PLAYER_PAUSE else ICON_PLAYER_PLAY
        playerSourceView?.apply {
            visibility = if (playerPageUrl == null) View.GONE else View.VISIBLE
        }
        if (::musicChip.isInitialized) {
            musicChip.setTextColor(if (playing || playerLoading) accent else muted)
            musicChip.text = ICON_MUSIC
        }
        updatePlayerProgress()
    }

    private fun updatePlayerProgress() {
        val player = mediaPlayer
        val duration = runCatching { player?.duration ?: 0 }.getOrDefault(0).coerceAtLeast(0)
        val position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0).coerceAtLeast(0)
        playerSeekBar?.max = duration.coerceAtLeast(1)
        playerSeekBar?.progress = position.coerceAtMost(duration.coerceAtLeast(1))
        playerTimeView?.text = "${formatPlayerTime(position)} / ${formatPlayerTime(duration)}"
        val playing = runCatching { player?.isPlaying == true }.getOrDefault(false)
        playerPlayButton?.text = if (playing) ICON_PLAYER_PAUSE else ICON_PLAYER_PLAY
        if (::musicChip.isInitialized) {
            musicChip.setTextColor(if (playing || playerLoading) accent else muted)
            musicChip.text = ICON_MUSIC
        }
    }

    private fun formatPlayerTime(milliseconds: Int): String {
        val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
        return "%d:%02d".format(Locale.US, totalSeconds / 60, totalSeconds % 60)
    }

    private fun audioDisplayName(uri: Uri): String {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty().ifBlank { tr("Локальный трек", "Local track") }
    }

    private fun addBookmark() {
        val tab = currentTab() ?: return
        if (tab.url == HOME_URL) return
        val bookmarks = prefs.getStringSet("bookmarks", emptySet()).orEmpty().toMutableSet()
        bookmarks += encodeEntry(tab.title, tab.url)
        prefs.edit().putStringSet("bookmarks", bookmarks).apply()
        Toast.makeText(this, tr("Добавлено в закладки", "Added to bookmarks"), Toast.LENGTH_SHORT).show()
    }

    private fun openDownloads() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(14))
            background = rounded(this@MainActivity.background, 4f, withAlpha(muted, 54))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = tr("Загрузки", "Downloads")
                textSize = 22f
                setTextColor(ink)
                typeface = onestBold
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = ICON_REFRESH
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(accent)
                typeface = tablerTypeface()
                tooltipText = tr("Обновить", "Refresh")
                setOnClickListener { dialog.dismiss(); openDownloads() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(TextView(this@MainActivity).apply {
                text = ICON_X
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(ink)
                typeface = tablerTypeface()
                tooltipText = tr("Закрыть", "Close")
                setOnClickListener { dialog.dismiss() }
            }, LinearLayout.LayoutParams(dp(42), dp(44)))
        }
        sheet.addView(header)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, 0)
        }
        val entries = storedDownloads()
        if (entries.isEmpty()) {
            list.addView(TextView(this).apply {
                text = tr("Здесь появятся скачанные файлы", "Downloaded files will appear here")
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(dp(12), dp(34), dp(12), dp(34))
            })
        } else {
            entries.forEach { list.addView(downloadRow(it), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(7) }) }
        }
        sheet.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        dialog.setContentView(sheet)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, minOf(dp(560), (resources.displayMetrics.heightPixels * .72f).toInt()))
        sheet.alpha = 0f
        sheet.translationY = dp(28).toFloat()
        sheet.animate().alpha(1f).translationY(0f).setDuration(190).start()
    }

    private fun storedDownloads(): List<DownloadEntry> {
        val ids = prefs.getStringSet("download_ids", emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return emptyList()
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val entries = mutableListOf<DownloadEntry>()
        manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { cursor ->
            while (cursor.moveToNext()) {
                entries += DownloadEntry(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty().ifBlank { tr("Файл", "File") },
                    mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)).orEmpty(),
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    modifiedAt = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP))
                )
            }
        }
        prefs.edit().putStringSet("download_ids", entries.map { it.id.toString() }.toSet()).apply()
        return entries.sortedByDescending { it.modifiedAt }
    }

    private fun downloadRow(entry: DownloadEntry): View {
        val done = entry.status == DownloadManager.STATUS_SUCCESSFUL
        val status = when (entry.status) {
            DownloadManager.STATUS_SUCCESSFUL -> tr("Готово · нажми, чтобы открыть", "Done · tap to open")
            DownloadManager.STATUS_FAILED -> tr("Ошибка загрузки", "Download failed")
            DownloadManager.STATUS_PAUSED -> tr("Загрузка приостановлена", "Download paused")
            else -> {
                val percent = if (entry.totalBytes > 0) (entry.downloadedBytes * 100 / entry.totalBytes).toInt() else null
                if (percent == null) tr("Подготовка загрузки", "Preparing download")
                else tr("Скачивается · $percent%", "Downloading · $percent%")
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(9), dp(8), dp(9))
            background = rounded(surface, 4f, withAlpha(muted, 54))
            alpha = if (done) 1f else .84f
            addView(TextView(this@MainActivity).apply {
                text = if (entry.name.endsWith(".apk", true)) ICON_FILE_CODE else if (done) ICON_FILE_DOWNLOAD else ICON_LOADER
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(if (done) accent else muted)
                typeface = tablerTypeface()
                background = rounded(this@MainActivity.background, 4f)
            }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = entry.name
                    textSize = 14f
                    maxLines = 1
                    setTextColor(ink)
                    typeface = onestMedium
                })
                addView(TextView(this@MainActivity).apply {
                    text = "$status · ${formatBytes(entry.downloadedBytes)}${if (entry.totalBytes > 0) " / ${formatBytes(entry.totalBytes)}" else ""}"
                    textSize = 10f
                    maxLines = 1
                    setTextColor(if (done) greenDark else muted)
                })
                if (done) addView(TextView(this@MainActivity).apply {
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(entry.modifiedAt)
                    textSize = 9f
                    setTextColor(muted)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = if (done) ICON_CHEVRON_RIGHT else ""
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(accent)
                typeface = tablerTypeface()
            }, LinearLayout.LayoutParams(dp(24), dp(44)))
            if (done) setOnClickListener { openDownloadedFile(entry) }
        }
    }

    private fun openDownloadedFile(entry: DownloadEntry) {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(entry.id)
        if (uri == null) {
            Toast.makeText(this, tr("Файл недоступен: возможно, его удалили", "File unavailable; it may have been deleted"), Toast.LENGTH_SHORT).show()
            return
        }
        val mimeType = if (entry.name.endsWith(".apk", true)) {
            "application/vnd.android.package-archive"
        } else {
            manager.getMimeTypeForDownloadedFile(entry.id).orEmpty().ifBlank { entry.mimeType.ifBlank { "application/octet-stream" } }
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure {
            Toast.makeText(this, tr("Нет приложения для открытия этого файла", "No app can open this file"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatBytes(value: Long): String = when {
        value < 1024 -> "$value ${tr("Б", "B")}"
        value < 1024 * 1024 -> "${value / 1024} ${tr("КБ", "KB")}"
        else -> String.format(Locale.getDefault(), "%.1f ${tr("МБ", "MB")}", value / 1024f / 1024f)
    }

    private fun monitorDownload(id: Long, name: String) {
        downloadStatus.visibility = View.VISIBLE
        downloadStatus.alpha = 0f
        downloadStatus.translationY = -dp(8).toFloat()
        downloadStatus.animate().alpha(1f).translationY(0f).setDuration(180).start()
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val update = object : Runnable {
            override fun run() {
                manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                    if (!cursor.moveToFirst()) { downloadStatus.visibility = View.GONE; return }
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloadStatus.text = "✓ $name · ${tr("скачано", "downloaded")}"
                            downloadStatus.setTextColor(greenDark)
                            downloadStatus.animate().alpha(0f).setStartDelay(2600).setDuration(300)
                                .withEndAction { downloadStatus.visibility = View.GONE }.start()
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloadStatus.text = "! $name · ${tr("ошибка загрузки", "download failed")}"
                            downloadStatus.setTextColor(accent)
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            downloadStatus.text = "Ⅱ $name · ${tr("приостановлено", "paused")}"
                            downloadHandler.postDelayed(this, 1200)
                        }
                        else -> {
                            downloadStatus.text = if (percent >= 0) "↓ $name · $percent%"
                            else "↓ $name · ${tr("подготовка", "preparing")}"
                            downloadStatus.setTextColor(ink)
                            downloadHandler.postDelayed(this, 700)
                        }
                    }
                }
            }
        }
        downloadHandler.post(update)
    }

    private fun showStored(title: String, key: String) {
        val entries = prefs.getStringSet(key, emptySet()).orEmpty().map(::decodeEntry).reversed()
        if (entries.isEmpty()) {
            Toast.makeText(this, tr("$title пока пусты", "$title are empty"), Toast.LENGTH_SHORT).show()
            return
        }
        val labels = entries.map { (name, url) -> "$name\n$url" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, which -> navigate(currentWebView() ?: return@setItems, entries[which].second) }
            .setNegativeButton(tr("Закрыть", "Close"), null)
            .show()
    }

    private fun shareCurrentPage() {
        val tab = currentTab() ?: return
        if (tab.url == HOME_URL) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tab.url)
        }, tr("Поделиться страницей", "Share page")))
    }

    private fun copyCurrentUrl() {
        val tab = currentTab() ?: return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Weglet URL", tab.url))
        Toast.makeText(this, tr("Адрес скопирован", "Address copied"), Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this)
            .setTitle(tr("Очистить данные?", "Clear data?"))
            .setMessage(tr("Будут удалены cookie, кэш и данные сайтов. Закладки сохранятся.", "Cookies, cache, and site data will be deleted. Bookmarks will remain."))
            .setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Очистить", "Clear")) { _, _ ->
                prefs.edit()
                    .remove("history")
                    .remove("recent_sites")
                    .apply()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                tabs.forEach { it.webView.clearCache(true); it.webView.clearHistory() }
                clearGeckoBrowsingData(this) { error ->
                    runOnUiThread {
                        val message = if (error == null) {
                            tr("Данные обоих движков очищены", "Both engines' data was cleared")
                        } else {
                            tr("WebView очищен, Gecko вернул ошибку", "WebView was cleared; Gecko returned an error")
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun blockedSites(): Set<String> {
        blockedSitesCache?.let { return it }
        return prefs.getStringSet("blocked_sites", setOf("vk.ru")).orEmpty().also {
            blockedSitesCache = it
        }
    }

    private fun isUserBlocked(url: String) = SecurityPolicy.matchesUserBlocklist(url, blockedSites())

    private fun blockCurrentSite() {
        val tab = currentTab() ?: return
        val host = runCatching { Uri.parse(tab.url).host }.getOrNull()?.removePrefix("www.")
        if (host.isNullOrBlank() || tab.url == HOME_URL) {
            Toast.makeText(this, tr("На этой странице нет домена", "This page has no domain"), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(tr("Заблокировать $host?", "Block $host?"))
            .setMessage(tr("Weglet перестанет открывать этот домен и его поддомены.", "Weglet will stop opening this domain and its subdomains."))
            .setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Заблокировать", "Block")) { _, _ ->
                val sites = blockedSites().toMutableSet().apply { add(host.lowercase(Locale.US)) }
                blockedSitesCache = sites
                prefs.edit().putStringSet("blocked_sites", sites).apply()
                showUserBlocked(tab.webView, tab.url)
            }
            .show()
    }

    private fun showBlockedSites() {
        val sites = blockedSites().sorted()
        if (sites.isEmpty()) {
            Toast.makeText(this, tr("Чёрный список пуст", "The blocklist is empty"), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(tr("Нажмите, чтобы разрешить", "Tap to allow"))
            .setItems(sites.toTypedArray()) { _, which ->
                val updated = blockedSites().toMutableSet().apply { remove(sites[which]) }
                blockedSitesCache = updated
                prefs.edit().putStringSet("blocked_sites", updated).apply()
                Toast.makeText(this, tr("${sites[which]} разрешён", "${sites[which]} allowed"), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(tr("Закрыть", "Close"), null)
            .show()
    }

    private fun offerExternalApp(uri: Uri) {
        val scheme = uri.scheme.orEmpty().lowercase(Locale.US)
        val allowed = scheme in setOf("mailto", "tel", "sms", "geo", "market")
        if (!allowed) {
            Toast.makeText(this, tr("Weglet заблокировал внешний тип ссылки", "Weglet blocked this external link type"), Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(tr("Открыть другое приложение?", "Open another app?"))
            .setMessage(uri.toString())
            .setNegativeButton(tr("Отмена", "Cancel"), null)
            .setPositiveButton(tr("Открыть", "Open")) { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    .onFailure { Toast.makeText(this, tr("Подходящее приложение не найдено", "No suitable app found"), Toast.LENGTH_SHORT).show() }
            }
            .show()
    }

    private fun encodeEntry(title: String, url: String) = "${title.replace(ENTRY_SEPARATOR, " ")}$ENTRY_SEPARATOR$url"

    private fun decodeEntry(value: String): Pair<String, String> {
        val parts = value.split(ENTRY_SEPARATOR, limit = 2)
        return (parts.getOrNull(0).orEmpty().ifBlank { tr("Без названия", "Untitled") }) to parts.getOrNull(1).orEmpty()
    }

    private fun updateTabCount() {
        if (::tabsButton.isInitialized) {
            tabsButton.text = tabCountIcon(tabs.size)
            tabsButton.contentDescription = tr("Вкладки: ${tabs.size}", "Tabs: ${tabs.size}")
        }
    }

    private fun tabCountIcon(count: Int): String = when (count) {
        0 -> ICON_TAB_0
        1 -> ICON_TAB_1
        2 -> ICON_TAB_2
        3 -> ICON_TAB_3
        4 -> ICON_TAB_4
        5 -> ICON_TAB_5
        6 -> ICON_TAB_6
        7 -> ICON_TAB_7
        8 -> ICON_TAB_8
        9 -> ICON_TAB_9
        else -> ICON_TABS_MANY
    }

    private fun blockedPage(
        title: String,
        message: String,
        url: String,
        sourceLabel: String? = null,
        sourceUrl: String? = null
    ): String {
        val source = when {
            sourceLabel.isNullOrBlank() -> ""
            sourceUrl.isNullOrBlank() -> "<p class=source>${html(sourceLabel)}</p>"
            else -> "<p class=source><a href=\"${html(sourceUrl)}\">${html(sourceLabel)}</a></p>"
        }
        return """
            <!doctype html><html lang="ru"><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>@font-face{font-family:Onest;src:url('https://weglet.local/assets/Onest.ttf')}body{margin:0;padding:36px 22px;background:#15151B;color:#F4F1FF;font-family:Onest,sans-serif}
            main{max-width:620px;margin:10vh auto}b{display:inline-block;background:#22222B;color:#9C8CFF;padding:7px 10px;border-radius:4px}
            h1{font-size:28px;margin:20px 0 10px}p{font-size:16px;line-height:1.55;color:#AAA7B7}.url{word-break:break-all;font-size:13px}.source{font-size:12px;margin-top:22px}a{color:#B9AEFF;text-decoration:none}</style>
            </head><body><main><b>Weglet 1.3.6</b><h1>${html(title)}</h1><p>${html(message)}</p><p class="url">${html(url)}</p>$source</main></body></html>
        """.trimIndent()
    }

    private fun html(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null, strokeWidthDp: Int = 1): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius.toInt().coerceAtLeast(0)).toFloat()
            stroke?.let { setStroke(dp(strokeWidthDp.coerceAtLeast(1)), it) }
        }
    }

    private inner class BeaconNavButton(context: Context) : TextView(context) {
        var railActive = false
        private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!railActive) return
            railPaint.color = accent
            canvas.drawRect(
                dp(12).toFloat(),
                height - dp(3).toFloat(),
                width - dp(12).toFloat(),
                height.toFloat(),
                railPaint
            )
        }
    }

    private inner class WegletMarkView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            contentDescription = "Weglet"
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            paint.style = Paint.Style.FILL
            paint.color = surface
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), dp(10).toFloat(), dp(10).toFloat(), paint)
            paint.color = ink
            paint.typeface = onestBold
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = width * .57f
            val baseline = cy - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText("W", cx, baseline, paint)
            paint.color = accent
            canvas.drawRect(dp(5).toFloat(), height - dp(3).toFloat(), width - dp(5).toFloat(), height.toFloat(), paint)
        }
    }

    private fun fontTypeface(assetPath: String, weight: Int): Typeface {
        return runCatching {
            Typeface.Builder(assets, assetPath)
                .setWeight(weight)
                .build()
        }.getOrElse {
            Typeface.create("sans-serif", if (weight >= 700) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun tablerTypeface(): Typeface {
        return tablerIcons
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        val tab = currentTab()
        when {
            tab?.engineActive == true && tab.engineView.canGoBack() -> tab.engineView.goBack()
            tab?.engineActive == true -> showHome()
            tab?.webView?.canGoBack() == true -> tab.webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        downloadHandler.removeCallbacksAndMessages(null)
        playerHandler.removeCallbacksAndMessages(null)
        releasePlayer()
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        tabs.forEach {
            it.engineView.destroyEngine()
            it.webView.destroy()
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("tab_urls", ArrayList(tabs.map { it.url }))
        outState.putInt("active_tab_index", tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0))
    }

    @Deprecated("Legacy result API is kept to support the system document picker without extra libraries")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_WALLPAPER_REQUEST) {
            if (resultCode != RESULT_OK) return
            val uri = data?.data ?: return
            val target = File(filesDir, HOME_WALLPAPER_FILE)
            runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_WALLPAPER_BYTES) { "wallpaper is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("wallpaper could not be opened")
                prefs.edit()
                    .putBoolean("wallpaper_enabled", true)
                    .putString("wallpaper_mime", contentResolver.getType(uri) ?: "image/jpeg")
                    .apply()
                currentWebView()?.let(::loadHome)
            }.onFailure {
                target.delete()
                Toast.makeText(this, tr("Не удалось сохранить обои или файл больше 20 МБ", "Could not save wallpaper or file is over 20 MB"), Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode != PICK_AUDIO_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        playLocalAudio(uri, audioDisplayName(uri))
    }

    private inner class WegletClient(private val tab: BrowserTab) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (request.url.scheme == "weglet") {
                when (request.url.host) {
                    "search" -> startSearch(request.url.getQueryParameter("q").orEmpty())
                    "open" -> request.url.getQueryParameter("url")?.let { navigate(view, it) }
                    "shortcuts" -> showShortcutPicker()
                    "engine" -> {
                        val engineId = request.url.getQueryParameter("id")
                        val engine = SearchEngine.entries.firstOrNull { it.id == engineId }
                        if (engine == SearchEngine.CUSTOM && !isValidCustomTemplate(customSearchTemplate())) {
                            showCustomSearchSetup()
                        } else if (engine != null) {
                            selectSearchEngine(engine)
                        }
                    }
                    else -> Toast.makeText(this@MainActivity, tr("Неизвестная команда Weglet", "Unknown Weglet command"), Toast.LENGTH_SHORT).show()
                }
                return true
            }
            if (request.isForMainFrame && request.url.scheme in setOf("http", "https")) {
                if (guardNavigation(view, url)) return true
                prepareWebViewForUrl(view, url)
                return false
            }
            if (request.isForMainFrame) {
                offerExternalApp(request.url)
                return true
            }
            return false
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val localFont = if (
                request.url.scheme == "https" &&
                request.url.host.equals("weglet.local", ignoreCase = true)
            ) when (request.url.path) {
                "/assets/Onest.ttf" -> "fonts/Onest.ttf"
                "/assets/JetBrainsMono.ttf" -> "fonts/JetBrainsMono.ttf"
                else -> null
            } else null
            if (localFont != null) {
                return runCatching {
                    WebResourceResponse("font/ttf", null, assets.open(localFont))
                }.getOrNull()
            }
            if (
                request.url.scheme == "https" &&
                request.url.host.equals("weglet.local", ignoreCase = true) &&
                request.url.path == "/assets/wallpaper"
            ) {
                val wallpaper = File(filesDir, HOME_WALLPAPER_FILE)
                if (wallpaper.exists() && prefs.getBoolean("wallpaper_enabled", false)) {
                    return runCatching {
                        WebResourceResponse(
                            prefs.getString("wallpaper_mime", "image/jpeg") ?: "image/jpeg",
                            null,
                            wallpaper.inputStream()
                        )
                    }.getOrNull()
                }
            }
            val url = request.url.toString()
            val approvedMainFrame = request.isForMainFrame && url in approvedHttp
            // OpenPhish is a navigation feed, so hashing every image/script URL only wastes CPU.
            val knownThreat = request.isForMainFrame && threatFeed.isKnownPhishing(url)
            val trackerHost = if (!request.isForMainFrame) SecurityPolicy.matchedTrackerHost(request.url.host) else null
            if (trackerHost != null) recordBlockedTracker(trackerHost)
            val insecureResource = request.url.scheme.equals("http", ignoreCase = true)
            return if (
                isUserBlocked(url) || knownThreat ||
                (!approvedMainFrame && (insecureResource || trackerHost != null))
            ) {
                SecurityPolicy.blockedResponse()
            } else {
                super.shouldInterceptRequest(view, request)
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (tab.engineActive) return
            if (url != HOME_URL && threatFeed.isKnownPhishing(url)) {
                showReputationBlocked(view, url)
                return
            }
            val redirectRisk = if (url == HOME_URL) null else SecurityPolicy.assessNavigation(url)
            if (redirectRisk?.level == SecurityPolicy.RiskLevel.BLOCK) {
                view.stopLoading()
                showHeuristicBlocked(view, url, redirectRisk)
                return
            }
            view.animate().cancel()
            view.alpha = 1f
            view.translationY = 0f
            prepareWebViewForUrl(view, url)
            if (!isSearchProviderUrl(url) && url != HOME_URL) tab.searchQuery = null
            if (tab.id == activeTabId) {
                progress.visibility = View.VISIBLE
                updateChromeForUrl(url)
                address.setText(when {
                    url == HOME_URL -> ""
                    tab.searchQuery != null -> tab.searchQuery
                    else -> url
                })
                updateSecurityChip(url)
                updateNavigationState()
            }
            tab.url = url
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (tab.engineActive) return
            tab.url = url
            tab.title = view.title?.take(80).orEmpty().ifBlank { Uri.parse(url).host ?: tr("Новая вкладка", "New tab") }
            if (tab.id == activeTabId) {
                address.setText(when {
                    url == HOME_URL -> ""
                    tab.searchQuery != null -> tab.searchQuery
                    else -> url
                })
                updateChromeForUrl(url)
                updateSecurityChip(url)
                updateNavigationState()
            }
            if (url.startsWith("https://", true) && url != HOME_URL) {
                view.evaluateJavascript(PAGE_SCAN_SCRIPT) { result ->
                    handlePageScanResult(tab, view, url, result)
                }
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            val wasActive = tab.id == activeTabId
            tabs.remove(tab)
            pageHost.removeView(view)
            pageHost.removeView(tab.engineView)
            tab.engineView.destroyEngine()
            runCatching { view.destroy() }
            if (wasActive) activeTabId = -1
            if (wasActive && !isFinishing && !isDestroyed) {
                createTab()
                val message = if (detail.didCrash()) {
                    tr("Вкладка перезапущена после сбоя", "Tab restarted after a crash")
                } else {
                    tr("Вкладка восстановлена", "Tab restored")
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            } else {
                updateTabCount()
            }
            return true
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError?) {
            handler.cancel()
            showCertificateBlocked(view, error?.url ?: view.url.orEmpty(), error)
        }

        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: SafeBrowsingResponse
        ) {
            callback.backToSafety(false)
            runOnUiThread { showThreatBlocked(view, request.url.toString(), threatType) }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (tab.engineActive) return
            if (!request.isForMainFrame) return
            view.loadDataWithBaseURL(
                HOME_URL,
                blockedPage(tr("Страница не открылась", "Page could not be opened"), error.description.toString(), request.url.toString()),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    private inner class WegletChrome(private val tab: BrowserTab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (tab.id != activeTabId || tab.engineActive) return
            progress.progress = newProgress
            progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (tab.engineActive) return
            tab.title = title.orEmpty().take(80)
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                if (!prefs.getBoolean("site_permissions_enabled", false)) {
                    request.deny()
                    return@runOnUiThread
                }
                val requested = request.resources.toSet()
                val supported = setOf(
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE
                )
                if (requested.isEmpty() || requested.any { it !in supported }) {
                    request.deny()
                    return@runOnUiThread
                }
                val labels = buildList {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in requested) add(tr("камере", "camera"))
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in requested) add(tr("микрофону", "microphone"))
                }
                if (labels.isEmpty()) { request.deny(); return@runOnUiThread }
                val site = request.origin.host ?: request.origin.toString()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(tr("Разрешение для сайта", "Site permission"))
                    .setMessage(tr(
                        "$site запрашивает доступ к ${labels.joinToString(" и ")}. Разрешить?",
                        "$site requests access to ${labels.joinToString(" and ")}. Allow?"
                    ))
                    .setNegativeButton(tr("Запретить", "Deny")) { _, _ -> request.deny() }
                    .setOnCancelListener { request.deny() }
                    .setPositiveButton(tr("Продолжить", "Continue")) { _, _ ->
                        pendingPermissionRequest?.deny()
                        pendingPermissionRequest = request
                        val permissions = buildList {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in requested) add(Manifest.permission.CAMERA)
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in requested) add(Manifest.permission.RECORD_AUDIO)
                        }.toTypedArray()
                        requestPermissions(permissions, SITE_PERMISSION_REQUEST)
                    }.show()
            }
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
            return false
        }
    }

    private inner class WegletDownloadListener : DownloadListener {
        override fun onDownloadStart(
            url: String,
            userAgent: String,
            contentDisposition: String,
            mimetype: String,
            contentLength: Long
        ) {
            val sourceRisk = SecurityPolicy.assessNavigation(url)
            if (threatFeed.isKnownPhishing(url) || sourceRisk?.level == SecurityPolicy.RiskLevel.BLOCK) {
                val reason = if (selectedUiLanguage() == UiLanguage.ENGLISH) {
                    if (sourceRisk != null) "The file address failed Weglet's local security checks."
                    else "The file address matched a current phishing indicator."
                } else {
                    sourceRisk?.reason ?: "Адрес файла совпал с актуальным индикатором фишинга."
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(tr("Загрузка заблокирована", "Download blocked"))
                    .setMessage("$reason\n\n$url")
                    .setPositiveButton(tr("Понятно", "OK"), null)
                    .show()
                return
            }
            if (!url.startsWith("https://", true)) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(tr("Загрузка заблокирована", "Download blocked"))
                    .setMessage(tr("Weglet разрешает загрузки только по защищённому HTTPS-соединению.", "Weglet only allows downloads over a secure HTTPS connection."))
                    .setPositiveButton(tr("Понятно", "OK"), null)
                    .show()
                return
            }
            val guessedName = URLUtil.guessFileName(url, contentDisposition, mimetype)
            val name = guessedName
                .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
                .take(160)
                .ifBlank { "download" }
            val warningNotes = buildList {
                sourceRisk?.let { add(it.reason) }
                val lowerName = name.lowercase(Locale.US)
                if (DOUBLE_EXTENSION.containsMatchIn(lowerName)) {
                    add(tr("У файла двойное расширение — его тип могли попытаться замаскировать.", "The file has a double extension and its type may be disguised."))
                }
                val extension = lowerName.substringAfterLast('.', "")
                if (extension == "apk") {
                    add(tr("Это APK. Android покажет отдельное подтверждение установки; устанавливайте только из доверенного источника.", "This is an APK. Android will ask for separate installation confirmation; install only from a trusted source."))
                } else if (extension in ACTIVE_DOWNLOAD_EXTENSIONS) {
                    add(tr("Это исполняемый файл или скрипт. Weglet не запускает его и не заменяет полноценный антивирус.", "This is an executable file or script. Weglet does not run it and is not a replacement for antivirus software."))
                }
            }
            val warningText = if (warningNotes.isEmpty()) "" else
                "\n\n${tr("Проверка Weglet", "Weglet check")}:\n• ${warningNotes.joinToString("\n• ")}"
            AlertDialog.Builder(this@MainActivity)
                .setTitle(tr("Скачать файл?", "Download file?"))
                .setMessage("$name$warningText")
                .setNegativeButton(tr("Отмена", "Cancel"), null)
                .setPositiveButton(tr("Скачать", "Download")) { _, _ ->
                    runCatching {
                        val request = DownloadManager.Request(Uri.parse(url))
                            .setTitle(name)
                            .setMimeType(mimetype)
                            .addRequestHeader("User-Agent", userAgent)
                            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                        val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                        val knownDownloads = prefs.getStringSet("download_ids", emptySet()).orEmpty().toMutableSet()
                        knownDownloads += id.toString()
                        prefs.edit().putStringSet("download_ids", knownDownloads).apply()
                        monitorDownload(id, name)
                        Toast.makeText(this@MainActivity, tr("Загрузка началась", "Download started"), Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this@MainActivity, tr("Не удалось начать загрузку", "Could not start download"), Toast.LENGTH_LONG).show()
                    }
                }
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != SITE_PERMISSION_REQUEST) return
        val request = pendingPermissionRequest ?: return
        pendingPermissionRequest = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            request.grant(request.resources)
        } else request.deny()
    }

    companion object {
        private const val HOME_URL = "https://weglet.local/"
        private const val ENTRY_SEPARATOR = "\u001F"
        private const val SITE_PERMISSION_REQUEST = 420
        private const val PICK_AUDIO_REQUEST = 421
        private const val PICK_WALLPAPER_REQUEST = 422
        private const val HOME_WALLPAPER_FILE = "weglet-home-wallpaper"
        private const val MAX_WALLPAPER_BYTES = 20L * 1024L * 1024L
        private const val MAX_RESTORED_TABS = 8
        private const val TERMS_VERSION = 10

        // Tabler Icons 3.46.0 code points used by the interface.
        private const val ICON_ADJUSTMENTS = "\uEA03"
        private const val ICON_ARROW_LEFT = "\uEA19"
        private const val ICON_ARROW_RIGHT = "\uEA1F"
        private const val ICON_BAN = "\uEA2E"
        private const val ICON_BOLT = "\uEA38"
        private const val ICON_BOOKMARK = "\uEA3A"
        private const val ICON_BORDER_ALL = "\uEA3B"
        private const val ICON_COPY = "\uEA7A"
        private const val ICON_DOWNLOAD = "\uEA96"
        private const val ICON_EYE = "\uEA9A"
        private const val ICON_FILE_DOWNLOAD = "\uEA9D"
        private const val ICON_FILE_MUSIC = "\uEA9F"
        private const val ICON_HOME = "\uEAC1"
        private const val ICON_INFO_CIRCLE = "\uEAC5"
        private const val ICON_LAYOUT_ALIGN_CENTER = "\uEACE"
        private const val ICON_LAYOUT_NAVBAR = "\uEAD7"
        private const val ICON_MICROPHONE = "\uEAF0"
        private const val ICON_MUSIC = "\uEAFC"
        private const val ICON_PAINT = "\uEB00"
        private const val ICON_PALETTE = "\uEB01"
        private const val ICON_PHOTO = "\uEB0A"
        private const val ICON_PLUS = "\uEB0B"
        private const val ICON_REFRESH = "\uEB13"
        private const val ICON_SEARCH = "\uEB1C"
        private const val ICON_SETTINGS = "\uEB20"
        private const val ICON_SHARE = "\uEB21"
        private const val ICON_SHIELD_CHECK = "\uEB22"
        private const val ICON_STAR = "\uEB2E"
        private const val ICON_TRASH = "\uEB41"
        private const val ICON_X = "\uEB55"
        private const val ICON_ARROWS_HORIZONTAL = "\uEB59"
        private const val ICON_ARROWS_VERTICAL = "\uEB5B"
        private const val ICON_COLOR_SWATCH = "\uEB61"
        private const val ICON_LIST = "\uEB6B"
        private const val ICON_BROWSER = "\uEBB7"
        private const val ICON_LANGUAGE = "\uEBBE"
        private const val ICON_TYPOGRAPHY = "\uEBC5"
        private const val ICON_FILE_CODE = "\uEBD0"
        private const val ICON_CONTRAST = "\uEC4E"
        private const val ICON_PHOTO_OFF = "\uECF6"
        private const val ICON_CHEVRON_RIGHT = "\uEA61"
        private const val ICON_ASPECT_RATIO = "\uED30"
        private const val ICON_RECTANGLE = "\uED37"
        private const val ICON_PLAYER_PAUSE = "\uED45"
        private const val ICON_PLAYER_PLAY = "\uED46"
        private const val ICON_PLAYER_STOP = "\uED4A"
        private const val ICON_SHIELD_LOCK = "\uED58"
        private const val ICON_LAYOUT_GRID = "\uEDBA"
        private const val ICON_TAB_0 = "\uEEE5"
        private const val ICON_TAB_1 = "\uEEE6"
        private const val ICON_TAB_2 = "\uEEE7"
        private const val ICON_TAB_3 = "\uEEE8"
        private const val ICON_TAB_4 = "\uEEE9"
        private const val ICON_TAB_5 = "\uEEEA"
        private const val ICON_TAB_6 = "\uEEEB"
        private const val ICON_TAB_7 = "\uEEEC"
        private const val ICON_TAB_8 = "\uEEED"
        private const val ICON_TAB_9 = "\uEEEE"
        private const val ICON_TABS_MANY = "\uEEF6"
        private const val ICON_FILE_DESCRIPTION = "\uF028"
        private const val ICON_ARROWS_EXCHANGE = "\uF1F4"
        private const val ICON_LOADER = "\uF226"
        private const val ICON_TRANSITION_TOP = "\uF2B5"
        private const val ICON_CLOCK_HOUR_12 = "\uF316"
        private const val ICON_SHIELD_SEARCH = "\uF9CD"
        private const val ICON_SPEEDTEST = "\uFA77"
        private val PAGE_SCAN_SCRIPT = """
            (function(){
              try {
                if (location.protocol !== 'https:') return 10;
                var passwordFields = document.querySelectorAll('input[type="password"]');
                if (!passwordFields.length) return 10;
                var passwordField = passwordFields[0];
                var form = passwordField.form;
                var actionUrl = null;
                if (form) {
                  var action = form.getAttribute('action');
                  if (action) actionUrl = new URL(action, location.href);
                }
                if (actionUrl && actionUrl.protocol === 'http:') return 12;
                var collect = function(selector, limit) {
                  return Array.prototype.slice.call(document.querySelectorAll(selector), 0, limit)
                    .map(function(node) { return (node.textContent || node.getAttribute('aria-label') || '').slice(0, 180); })
                    .join(' ');
                };
                var headingText = ((document.title || '') + ' ' + collect('h1,h2,legend', 24)).toLowerCase();
                var controlText = collect('label,button,input[type="submit"]', 40).toLowerCase();
                var visualText = Array.prototype.slice.call(document.images, 0, 36)
                  .map(function(img) { return ((img.alt || '') + ' ' + (img.title || '') + ' ' + (img.src || '')).slice(0, 260); })
                  .join(' ').toLowerCase();
                var loginWords = /login|log in|sign in|password|account|verify|verification|вход|парол|аккаунт|подтверд/.test(headingText + ' ' + controlText);
                var hasSubmit = !!(form && form.querySelector('button[type="submit"],input[type="submit"],button:not([type])'));
                var loginStructure = loginWords || hasSubmit;
                var host = (location.hostname || '').toLowerCase().replace(/\.$/, '');
                var official = function(roots) {
                  return roots.some(function(root) { return host === root || host.endsWith('.' + root); });
                };
                var brands = [
                  [['google','gmail','youtube'],['google.com','google.ru','gmail.com','youtube.com']],
                  [['microsoft','outlook','office 365','onedrive'],['microsoft.com','live.com','microsoftonline.com','office.com','outlook.com','onedrive.com']],
                  [['apple','icloud','apple id'],['apple.com','icloud.com']],
                  [['telegram'],['telegram.org','t.me']],
                  [['facebook'],['facebook.com','fb.com']],
                  [['instagram'],['instagram.com']],
                  [['whatsapp'],['whatsapp.com','wa.me']],
                  [['github'],['github.com']],
                  [['steam'],['steampowered.com','steamcommunity.com']],
                  [['roblox'],['roblox.com']],
                  [['binance'],['binance.com']],
                  [['paypal'],['paypal.com','paypal.me']],
                  [['sber','сбер'],['sber.ru','sberbank.ru']],
                  [['t-bank','tinkoff','т-банк','тинькофф'],['tbank.ru','tinkoff.ru']],
                  [['vkontakte','вконтакте'],['vk.com','vk.ru']]
                ];
                for (var i = 0; i < brands.length; i++) {
                  var words = brands[i][0];
                  var namedAsBrand = words.some(function(word) { return headingText.indexOf(word) >= 0; });
                  var brandedVisual = words.some(function(word) { return visualText.indexOf(word) >= 0; });
                  if (loginStructure && namedAsBrand && brandedVisual && !official(brands[i][1])) return 13;
                }
                if (actionUrl && actionUrl.origin !== location.origin && loginStructure) return 11;
                var metas = document.querySelectorAll('meta[http-equiv]');
                for (var m = 0; m < metas.length; m++) {
                  if ((metas[m].getAttribute('http-equiv') || '').toLowerCase() === 'refresh' && loginStructure) return 11;
                }
                return 10;
              } catch (ignored) {
                return 10;
              }
            })()
        """.trimIndent()
        private val ACTIVE_DOWNLOAD_EXTENSIONS = setOf(
            "bat", "cmd", "com", "exe", "hta", "jar", "js", "msi", "ps1", "scr", "vbs"
        )
        private val DOUBLE_EXTENSION = Regex(
            ".*\\.(?:docx?|gif|jpe?g|mp3|mp4|pdf|png|txt|xlsx?|zip)\\.(?:apk|bat|cmd|com|exe|hta|jar|js|msi|ps1|scr|vbs)$",
            RegexOption.IGNORE_CASE
        )
    }
}
