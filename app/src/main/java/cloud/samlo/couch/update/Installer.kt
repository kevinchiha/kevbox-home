package cloud.samlo.couch.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import cloud.samlo.couch.HomeAccessibilityService
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.kevbox.update.SilentInstall
import cloud.samlo.couch.kevbox.update.VersionJson
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads a release's APK, checks it against the SHA-256 in version.json and hands it to
 * Android's package installer. On Android 12 and later, when KevBox Home may install apps, Android
 * applies the update with no screen: an app updating itself needs only the manifest's
 * UPDATE_PACKAGES_WITHOUT_USER_ACTION, whoever installed it first. Otherwise it asks the user to
 * confirm (and, the first time, to let KevBox Home install apps). [InstallResult] receives the
 * outcome. One install runs at a time, app-wide, member-started or silent ([SilentInstall] shares
 * the download, the check and the slot).
 */
class Installer(context: Context, private val fetcher: Fetcher) {
    private val context = context.applicationContext

    /**
     * [onDone] runs on the main thread with null once Android has the update, or with a message
     * string resource when it failed. Ignored while an install runs.
     */
    fun install(release: Release, onDone: (Int?) -> Unit) {
        if (!claim()) return
        executor.execute {
            val apk = apkFile(context)
            val error = try {
                download(release, apk)
                verify(apk, release)
                commit(apk)
                null
            } catch (e: Failure) {
                e.messageRes
            } catch (e: Exception) {
                R.string.update_failed
            } finally {
                apk.delete()
            }
            if (error != null) HomeAccessibilityService.reopenAfterUpdate(context, false)
            free()
            main.post { onDone(error) }
        }
    }

    internal fun download(release: Release, apk: File) {
        fetcher.open(release.apkUrl).use { input -> apk.outputStream().use { input.copyTo(it) } }
    }

    /**
     * True when the APK matches the `sha256` version.json published with it. It catches a broken
     * or swapped download; Android's same-signature rule is what keeps a foreign APK out.
     */
    internal fun matches(apk: File, release: Release): Boolean = VersionJson.matches(sha256(apk), release.sha256)

    private fun verify(apk: File, release: Release) {
        if (!matches(apk, release)) throw Failure(R.string.update_corrupt)
    }

    private fun commit(apk: File) {
        val id = openSession(apk)
        HomeAccessibilityService.reopenAfterUpdate(context, true)
        commitSession(id)
    }

    /** Creates an install session holding [apk] and returns its id; abandons it and throws on failure. */
    internal fun openSession(apk: File): Int {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // With UPDATE_PACKAGES_WITHOUT_USER_ACTION in the manifest and the "install unknown
            // apps" permission, Android 12+ applies the update with no screen, member-started too:
            // AOSP skips the screen for the installer of record or for an app updating itself.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite(APK, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
            }
        } catch (e: Exception) {
            abandon(id)
            throw e
        }
        return id
    }

    /**
     * Commits a session from [openSession]; abandons it and throws when Android refuses. A
     * [silentCode] marks the result as a silent install of that versionCode for [InstallResult].
     */
    internal fun commitSession(sessionId: Int, silentCode: Long? = null) {
        try {
            context.packageManager.packageInstaller.openSession(sessionId).use { session ->
                session.commit(resultIntent(sessionId, silentCode).intentSender)
            }
        } catch (e: Exception) {
            abandon(sessionId)
            throw e
        }
    }

    /** Never throws. */
    internal fun abandon(sessionId: Int) {
        try {
            context.packageManager.packageInstaller.abandonSession(sessionId)
        } catch (e: Exception) {
            // Already gone.
        }
    }

    private fun resultIntent(sessionId: Int, silentCode: Long?): PendingIntent {
        // Mutable: the installer adds the status and the confirmation intent as extras.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(context, InstallResult::class.java).setPackage(context.packageName)
        if (silentCode != null) SilentInstall.markSilent(intent, silentCode)
        return PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** A failure with its own message for the user. */
    private class Failure(val messageRes: Int) : Exception()

    companion object {
        /** The cache file and installer session name for the download. */
        private const val APK = "kevbox-home.apk"
        private val executor = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
        private val busy = AtomicBoolean(false)

        val running: Boolean get() = busy.get()

        /** Takes the app-wide install slot; false while another install runs. Pair with [free]. */
        internal fun claim(): Boolean = busy.compareAndSet(false, true)

        internal fun free() = busy.set(false)

        /** Where the download goes; only the install holding the slot uses it. */
        internal fun apkFile(context: Context): File = File(context.cacheDir, APK)
    }
}
