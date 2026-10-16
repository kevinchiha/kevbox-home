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
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads a release's APK, checks it against the published SHA-256 and hands it to Android's
 * package installer. Android then asks the user to confirm (and, the first time, to let Couch
 * install apps); [InstallResult] receives the outcome. One install runs at a time, app-wide.
 */
class Installer(context: Context, private val fetcher: Fetcher) {
    private val context = context.applicationContext

    /**
     * [onDone] runs on the main thread with null once Android has the update (it asks the user
     * next), or with a message string resource when it failed. Ignored while an install runs.
     */
    fun install(release: Release, onDone: (Int?) -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            val apk = File(context.cacheDir, Release.APK)
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
            busy.set(false)
            main.post { onDone(error) }
        }
    }

    private fun download(release: Release, apk: File) {
        fetcher.open(release.apkUrl).use { input -> apk.outputStream().use { input.copyTo(it) } }
    }

    /**
     * The checksum file reads `<sha256>  couch-vX.Y.Z.apk`; only the hash matters. It catches a
     * broken download; Android's same-signature rule is what keeps a foreign APK out.
     */
    private fun verify(apk: File, release: Release) {
        val expected = fetcher.text(release.checksumUrl)?.trim()?.split(Regex("\\s+"))?.firstOrNull()
            ?: throw Failure(R.string.update_failed)
        if (!expected.equals(sha256(apk), ignoreCase = true)) throw Failure(R.string.update_corrupt)
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Once Couch installed itself, Android 12+ can apply later updates without asking.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            try {
                apk.inputStream().use { input ->
                    session.openWrite(Release.APK, 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                HomeAccessibilityService.reopenAfterUpdate(context, true)
                session.commit(resultIntent(id).intentSender)
            } catch (e: Exception) {
                session.abandon()
                throw e
            }
        }
    }

    private fun resultIntent(sessionId: Int): PendingIntent {
        // Mutable: the installer adds the status and the confirmation intent as extras.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(context, InstallResult::class.java).setPackage(context.packageName)
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
        private val executor = Executors.newSingleThreadExecutor()
        private val main = Handler(Looper.getMainLooper())
        private val busy = AtomicBoolean(false)

        val running: Boolean get() = busy.get()
    }
}
