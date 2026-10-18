package app.weglet.browser

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.widget.EditText
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.util.Locale

internal data class WegletEnginePageState(
    val url: String,
    val title: String = "",
    val loading: Boolean,
    val progress: Int = if (loading) 8 else 100,
    val error: String? = null
)

internal data class WegletEngineDownload(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String,
    val contentLength: Long
)

internal fun clearGeckoBrowsingData(context: Context, onComplete: (Throwable?) -> Unit) {
    WegletGeckoRuntime.get(context).storageController
        .clearData(StorageController.ClearFlags.ALL)
        .accept({ onComplete(null) }, { error -> onComplete(error) })
}

/** One GeckoView and one GeckoSession per Weglet tab. */
internal class WegletEngineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GeckoView(context, attrs) {

    var onStateChanged: ((WegletEnginePageState) -> Unit)? = null
    var onNavigationRequested: ((String) -> Unit)? = null
    var onNewTabRequested: ((String) -> Unit)? = null
    var onBlockedNavigation: ((String) -> Unit)? = null
    var onDownloadRequested: ((WegletEngineDownload) -> Unit)? = null
    var isRequestAllowed: ((String) -> Boolean)? = null

    private val geckoSession = newSession()
    private var opened = false
    private var destroyed = false
    private var currentUrl = "about:blank"
    private var currentTitle = ""
    private var loading = false
    private var progressValue = 0
    private var backAvailable = false
    private var forwardAvailable = false

    init {
        setBackgroundColor(Color.rgb(21, 21, 27))
        coverUntilFirstPaint(Color.rgb(21, 21, 27))
    }

    fun loadUrl(url: String) {
        if (destroyed) return
        ensureOpen()
        currentUrl = url
        currentTitle = Uri.parse(url).host?.removePrefix("www.").orEmpty()
        loading = true
        progressValue = 4
        dispatchState()
        geckoSession.loadUri(url)
    }

    fun reload() {
        if (opened && !destroyed) geckoSession.reload()
    }

    fun canGoBack(): Boolean = backAvailable

    fun canGoForward(): Boolean = forwardAvailable

    fun goBack(): Boolean {
        if (!opened || !backAvailable) return false
        geckoSession.goBack()
        return true
    }

    fun goForward(): Boolean {
        if (!opened || !forwardAvailable) return false
        geckoSession.goForward()
        return true
    }

    fun setPaused(paused: Boolean) {
        if (!opened || destroyed) return
        geckoSession.setActive(!paused)
        geckoSession.setFocused(!paused)
    }

    fun stop() {
        if (opened && !destroyed) geckoSession.stop()
    }

    fun destroyEngine() {
        if (destroyed) return
        destroyed = true
        runCatching { releaseSession() }
        runCatching { geckoSession.close() }
        opened = false
        onStateChanged = null
        onNavigationRequested = null
        onNewTabRequested = null
        onBlockedNavigation = null
        onDownloadRequested = null
        isRequestAllowed = null
    }

    private fun ensureOpen() {
        if (opened) return
        geckoSession.open(WegletGeckoRuntime.get(context))
        setSession(geckoSession)
        geckoSession.setActive(true)
        geckoSession.setFocused(true)
        opened = true
    }

    private fun newSession(): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .allowJavascript(true)
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(true)
            .build()
        return GeckoSession(settings).also { session ->
            session.setNavigationDelegate(NavigationDelegate())
            session.setProgressDelegate(ProgressDelegate())
            session.setContentDelegate(ContentDelegate())
            session.setPermissionDelegate(PermissionDelegate())
            session.setPromptDelegate(PromptDelegate())
        }
    }

    private fun dispatchState(error: String? = null) {
        onStateChanged?.invoke(
            WegletEnginePageState(
                url = currentUrl,
                title = currentTitle,
                loading = loading,
                progress = progressValue.coerceIn(0, 100),
                error = error
            )
        )
    }

    private fun isWebUri(value: String): Boolean {
        val scheme = runCatching { Uri.parse(value).scheme.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        return scheme in setOf("http", "https", "about", "data", "blob")
    }

    private inner class NavigationDelegate : GeckoSession.NavigationDelegate {
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            permissions: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean
        ) {
            if (!url.isNullOrBlank()) currentUrl = url
            dispatchState()
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            backAvailable = canGoBack
            dispatchState()
        }

        override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
            forwardAvailable = canGoForward
            dispatchState()
        }

        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest
        ): GeckoResult<AllowOrDeny> {
            val target = request.uri
            if (!isWebUri(target)) {
                onNavigationRequested?.invoke(target)
                return GeckoResult.deny()
            }
            if (target.startsWith("http", true) && isRequestAllowed?.invoke(target) == false) {
                onBlockedNavigation?.invoke(target)
                return GeckoResult.deny()
            }
            return GeckoResult.allow()
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
            onNewTabRequested?.invoke(uri)
            return GeckoResult.fromValue(null)
        }

        override fun onLoadError(
            session: GeckoSession,
            uri: String?,
            error: WebRequestError
        ): GeckoResult<String> {
            loading = false
            progressValue = 100
            val message = when (error.category) {
                WebRequestError.ERROR_CATEGORY_SECURITY -> "TLS/security error (${error.code})"
                WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> "Unsafe page blocked (${error.code})"
                WebRequestError.ERROR_CATEGORY_NETWORK -> "Network error (${error.code})"
                else -> "Page error (${error.code})"
            }
            dispatchState(message)
            return GeckoResult.fromValue(null)
        }
    }

    private inner class ProgressDelegate : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            currentUrl = url
            loading = true
            progressValue = 5
            dispatchState()
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) {
            progressValue = progress
            loading = progress < 100
            dispatchState()
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            loading = false
            progressValue = 100
            dispatchState(if (success) null else "Page stopped before completion")
        }
    }

    private inner class ContentDelegate : GeckoSession.ContentDelegate {
        override fun onTitleChange(session: GeckoSession, title: String?) {
            currentTitle = title.orEmpty().take(120)
            dispatchState()
        }

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            if (response.requestExternalApp) {
                onNavigationRequested?.invoke(response.uri)
                runCatching { response.body?.close() }
                return
            }
            val headers = response.headers.entries.associate { it.key.lowercase(Locale.US) to it.value }
            onDownloadRequested?.invoke(
                WegletEngineDownload(
                    url = response.uri,
                    userAgent = GeckoSession.getDefaultUserAgent(),
                    contentDisposition = headers["content-disposition"].orEmpty(),
                    mimeType = headers["content-type"].orEmpty().substringBefore(';'),
                    contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
                )
            )
            runCatching { response.body?.close() }
        }

        override fun onCrash(session: GeckoSession) {
            loading = false
            progressValue = 100
            dispatchState("Gecko content process crashed")
        }

        override fun onKill(session: GeckoSession) {
            loading = false
            progressValue = 100
            dispatchState("Gecko content process was stopped by Android")
        }
    }

    private inner class PermissionDelegate : GeckoSession.PermissionDelegate {
        override fun onContentPermissionRequest(
            session: GeckoSession,
            permission: GeckoSession.PermissionDelegate.ContentPermission
        ): GeckoResult<Int> {
            val allow = permission.permission in setOf(
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
                GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS,
                GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE
            )
            return GeckoResult.fromValue(
                if (allow) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
            )
        }

        override fun onAndroidPermissionsRequest(
            session: GeckoSession,
            permissions: Array<out String>?,
            callback: GeckoSession.PermissionDelegate.Callback
        ) = callback.reject()

        override fun onMediaPermissionRequest(
            session: GeckoSession,
            uri: String,
            video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback
        ) = callback.reject()
    }

    private inner class PromptDelegate : GeckoSession.PromptDelegate {
        override fun onAlertPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.AlertPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            AlertDialog.Builder(context)
                .setTitle(prompt.title?.takeIf { it.isNotBlank() } ?: "Сайт сообщает")
                .setMessage(prompt.message)
                .setPositiveButton("OK") { _, _ -> result.complete(prompt.dismiss()) }
                .setOnCancelListener { result.complete(prompt.dismiss()) }
                .show()
            return result
        }

        override fun onButtonPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.ButtonPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            AlertDialog.Builder(context)
                .setTitle(prompt.title?.takeIf { it.isNotBlank() } ?: "Подтверждение")
                .setMessage(prompt.message)
                .setNegativeButton("Отмена") { _, _ ->
                    result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE))
                }
                .setPositiveButton("OK") { _, _ ->
                    result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))
                }
                .setOnCancelListener { result.complete(prompt.dismiss()) }
                .show()
            return result
        }

        override fun onTextPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.TextPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val input = EditText(context).apply { setText(prompt.defaultValue) }
            AlertDialog.Builder(context)
                .setTitle(prompt.title?.takeIf { it.isNotBlank() } ?: "Введите значение")
                .setMessage(prompt.message)
                .setView(input)
                .setNegativeButton("Отмена") { _, _ -> result.complete(prompt.dismiss()) }
                .setPositiveButton("OK") { _, _ -> result.complete(prompt.confirm(input.text.toString())) }
                .setOnCancelListener { result.complete(prompt.dismiss()) }
                .show()
            return result
        }

        override fun onBeforeUnloadPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
            GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

        override fun onRepostConfirmPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
            GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))

        override fun onPopupPrompt(
            session: GeckoSession,
            prompt: GeckoSession.PromptDelegate.PopupPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            prompt.targetUri?.let { onNewTabRequested?.invoke(it) }
            return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
        }
    }
}

private object WegletGeckoRuntime {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime {
        runtime?.let { return it }
        return synchronized(this) {
            runtime ?: create(context.applicationContext).also { runtime = it }
        }
    }

    private fun create(context: Context): GeckoRuntime {
        val blocking = ContentBlocking.Settings.Builder()
            .antiTracking(ContentBlocking.AntiTracking.STRICT)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookiePurging(true)
            .strictSocialTrackingProtection(true)
            .queryParameterStrippingEnabled(true)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .bounceTrackingProtectionMode(
                ContentBlocking.BounceTrackingProtectionMode.BOUNCE_TRACKING_PROTECTION_MODE_ENABLED
            )
            .build()
        val settings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .webFontsEnabled(true)
            .webManifest(true)
            .globalPrivacyControlEnabled(true)
            .remoteDebuggingEnabled(false)
            .consoleOutput(false)
            .fissionEnabled(true)
            .lowMemoryDetection(true)
            .contentBlocking(blocking)
            .build()
        return GeckoRuntime.create(context, settings)
    }
}
