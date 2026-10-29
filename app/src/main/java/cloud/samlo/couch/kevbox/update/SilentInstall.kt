package cloud.samlo.couch.kevbox.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.os.Build
import android.util.Log
import cloud.samlo.couch.HomeAccessibilityService
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Fetcher
import cloud.samlo.couch.data.UserFiles
import cloud.samlo.couch.update.Installer
import cloud.samlo.couch.update.Release
import cloud.samlo.couch.update.UpdateCheck
import java.io.File
import java.security.MessageDigest

/** What [SilentInstall.onResult] does with Android's answer to a silent session. */
enum class SilentResult {
    /** The update installed. */
    INSTALLED,

    /** Android wanted a confirm screen: show none, abandon the session, mark the version refused. */
    REFUSED,

    /** Anything else: log it. The attempt was counted before the commit. */
    FAILED,
}

/**
 * One silent update attempt for the release the last check stored, and the handling of its result.
 * It copies KevBox TV's attempt order (rules, quiet, write, quiet again, count, commit) and KevBox
 * Live's check of the downloaded APK's package, versionCode and signer. Shares [Installer]'s
 * download, SHA-256 check and app-wide install slot, so a member-started install never runs beside it.
 */
object SilentInstall {
    const val TAG = "KevBoxSilentUpdate"
    private const val EXTRA_SILENT = "tv.kevbox.home.update.SILENT"
    private const val EXTRA_VERSION_CODE = "tv.kevbox.home.update.VERSION_CODE"

    /**
     * Blocking: run it off the main thread. Reads the stored release, applies [SilentRules.eligible],
     * asks [quiet], downloads and checks the APK, asks [quiet] again, counts the attempt, sets the
     * reopen-after-update mark only when [homeOnScreen] is true (and clears any older one otherwise),
     * then commits. Logs one line under [TAG] with [trigger] and returns the outcome; never throws.
     */
    fun run(context: Context, trigger: String, quiet: () -> SilentReason, homeOnScreen: () -> Boolean): SilentReason {
        val app = context.applicationContext
        var code: Long? = null
        return try {
            val fetcher = Fetcher(app)
            val release = UpdateCheck(app, fetcher).pending(Config.load(UserFiles(app).dir))
                ?: return log(trigger, null, SilentReason.NO_UPDATE)
            code = release.versionCode
            val canInstall = try {
                app.packageManager.canRequestPackageInstalls()
            } catch (e: Exception) {
                false
            }
            val state = SilentState.snapshot(app, release.versionCode)
            val rule = SilentRules.eligible(Build.VERSION.SDK_INT, release, System.currentTimeMillis(), canInstall, state)
            if (rule != SilentReason.ELIGIBLE) return log(trigger, code, rule)
            val first = ask(quiet)
            if (first != SilentReason.QUIET) return log(trigger, code, first)
            if (!Installer.claim()) return log(trigger, code, SilentReason.BUSY)
            try {
                attempt(app, Installer(app, fetcher), release, trigger, quiet, homeOnScreen)
            } finally {
                Installer.free()
            }
        } catch (e: Exception) {
            log(trigger, code, SilentReason.FAILED, e.javaClass.simpleName)
        }
    }

    /** The download, the checks and the commit, while this run holds the install slot. */
    private fun attempt(
        app: Context,
        installer: Installer,
        release: Release,
        trigger: String,
        quiet: () -> SilentReason,
        homeOnScreen: () -> Boolean,
    ): SilentReason {
        val code = release.versionCode
        val apk = Installer.apkFile(app)
        try {
            try {
                installer.download(release, apk)
            } catch (e: Exception) {
                // A dropped connection says nothing about the release: no attempt counted.
                return log(trigger, code, SilentReason.DOWNLOAD_FAILED, e.javaClass.simpleName)
            }
            if (!installer.matches(apk, release)) {
                SilentState.recordAttempt(app, code)
                return log(trigger, code, SilentReason.BAD_APK, "sha256")
            }
            if (!apkFits(app, apk)) {
                SilentState.recordAttempt(app, code)
                return log(trigger, code, SilentReason.BAD_APK, "package, versionCode or signer")
            }
            val session = try {
                installer.openSession(apk)
            } catch (e: Exception) {
                SilentState.recordAttempt(app, code)
                return log(trigger, code, SilentReason.FAILED, "session ${e.javaClass.simpleName}")
            }
            // From here the session holds the APK: every path commits it or abandons it.
            var settled = false
            try {
                val again = ask(quiet)
                if (again != SilentReason.QUIET) {
                    installer.abandon(session)
                    settled = true
                    return log(trigger, code, again, "before commit")
                }
                // Counted before the commit: the install kills this process.
                SilentState.recordAttempt(app, code)
                // Only when Home is on screen, so an update never pulls someone out of another app.
                HomeAccessibilityService.reopenAfterUpdate(app, homeOnScreen())
                settled = true
                try {
                    installer.commitSession(session, code)
                } catch (e: Exception) {
                    // commitSession abandoned the session.
                    HomeAccessibilityService.reopenAfterUpdate(app, false)
                    return log(trigger, code, SilentReason.FAILED, "commit ${e.javaClass.simpleName}")
                }
                return log(trigger, code, SilentReason.COMMITTED)
            } finally {
                if (!settled) installer.abandon(session)
            }
        } finally {
            apk.delete()
        }
    }

    /** [quiet], with an exception counting as in use. */
    private fun ask(quiet: () -> SilentReason): SilentReason = try {
        quiet()
    } catch (e: Exception) {
        SilentReason.IN_USE
    }

    /**
     * AC-5 on the downloaded APK: this app's package, a versionCode above the installed one, and
     * every certificate the installed app is signed with. Certificates are SHA-256 hex strings.
     * The APK's set may hold more (a rotated key keeps its old one in the history).
     */
    fun checkApk(
        expectedPkg: String,
        installedCode: Long,
        installedCerts: Set<String>,
        pkg: String?,
        code: Long?,
        certs: Set<String>?,
    ): Boolean =
        pkg == expectedPkg &&
            code != null && code > installedCode &&
            installedCerts.isNotEmpty() &&
            !certs.isNullOrEmpty() && certs.containsAll(installedCerts)

    private fun apkFits(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive: PackageInfo? = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        return checkApk(
            expectedPkg = context.packageName,
            installedCode = installed.longVersionCode,
            installedCerts = certificates(installed.signingInfo, withHistory = false),
            pkg = archive?.packageName,
            code = archive?.longVersionCode,
            certs = archive?.let { certificates(it.signingInfo, withHistory = true) },
        )
    }

    /** The current signers' SHA-256, plus for a single signer the older keys it rotated from when [withHistory]. */
    private fun certificates(signing: SigningInfo?, withHistory: Boolean): Set<String> {
        if (signing == null) return emptySet()
        val signers = if (signing.hasMultipleSigners() || !withHistory) {
            signing.apkContentsSigners.orEmpty().toList()
        } else {
            signing.apkContentsSigners.orEmpty().toList() + signing.signingCertificateHistory.orEmpty()
        }
        return signers.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Marks a session's result intent as a silent install of [versionCode]. */
    fun markSilent(intent: Intent, versionCode: Long) {
        intent.putExtra(EXTRA_SILENT, true).putExtra(EXTRA_VERSION_CODE, versionCode)
    }

    /** True for the result of a session [markSilent] marked. */
    fun isSilent(intent: Intent): Boolean = intent.getBooleanExtra(EXTRA_SILENT, false)

    /** AC-7: SUCCESS needs nothing, a confirm request is a refusal, anything else a failure. */
    fun resultFor(status: Int): SilentResult = when (status) {
        PackageInstaller.STATUS_SUCCESS -> SilentResult.INSTALLED
        PackageInstaller.STATUS_PENDING_USER_ACTION -> SilentResult.REFUSED
        else -> SilentResult.FAILED
    }

    /**
     * A silent session's result. Never shows a screen. A refusal abandons the session and marks the
     * version refused, so the Update chip takes over; anything but success drops the reopen mark.
     */
    fun onResult(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val code = intent.getLongExtra(EXTRA_VERSION_CODE, -1L).takeIf { it > 0 }
        val result = resultFor(status)
        if (result != SilentResult.INSTALLED) HomeAccessibilityService.reopenAfterUpdate(context, false)
        if (result == SilentResult.REFUSED) {
            val session = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
            if (session >= 0) {
                try {
                    context.packageManager.packageInstaller.abandonSession(session)
                } catch (e: Exception) {
                    // Already gone.
                }
            }
            if (code != null) SilentState.markRefused(context, code)
        }
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, line("result", code, result.name, "status $status" + (message?.let { " $it" } ?: "")))
    }

    /** One log line: what started it, the versionCode, the outcome and an optional detail. */
    internal fun line(trigger: String, versionCode: Long?, outcome: String, detail: String? = null): String {
        val base = "trigger=$trigger versionCode=${versionCode ?: "none"} outcome=$outcome"
        return if (detail.isNullOrBlank()) base else "$base ($detail)"
    }

    private fun log(trigger: String, versionCode: Long?, reason: SilentReason, detail: String? = null): SilentReason {
        Log.i(TAG, line(trigger, versionCode, reason.name, detail))
        return reason
    }
}
