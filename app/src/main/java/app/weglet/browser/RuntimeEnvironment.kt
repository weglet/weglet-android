package app.weglet.browser

import android.os.Build
import java.util.Locale

internal object RuntimeEnvironment {
    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
        val model = Build.MODEL.lowercase(Locale.US)
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
        val hardware = Build.HARDWARE.lowercase(Locale.US)
        val product = Build.PRODUCT.lowercase(Locale.US)

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            manufacturer.contains("genymotion") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            hardware.contains("vbox86") ||
            product.startsWith("sdk") ||
            product.contains("emulator") ||
            product.contains("simulator")
    }
}
