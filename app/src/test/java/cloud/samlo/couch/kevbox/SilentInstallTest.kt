package cloud.samlo.couch.kevbox

import android.content.pm.PackageInstaller
import cloud.samlo.couch.kevbox.update.SilentInstall
import cloud.samlo.couch.kevbox.update.SilentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentInstallTest {
    private val pkg = "tv.kevbox.home"
    private val installed = 80100L
    private val key = "ab".repeat(32)
    private val oldKey = "cd".repeat(32)
    private val otherKey = "ef".repeat(32)

    private fun check(
        installedCerts: Set<String> = setOf(key),
        apkPkg: String? = pkg,
        code: Long? = 80101L,
        certs: Set<String>? = setOf(key),
    ) = SilentInstall.checkApk(pkg, installed, installedCerts, apkPkg, code, certs)

    @Test
    fun sameAppNewerCodeSameKeyPasses() = assertTrue(check())

    @Test
    fun anotherPackageFails() {
        assertFalse(check(apkPkg = "tv.kevbox.home.dev"))
        assertFalse(check(apkPkg = null))
    }

    @Test
    fun aCodeNotAboveTheInstalledOneFails() {
        assertFalse(check(code = installed))
        assertFalse(check(code = installed - 1))
        assertFalse(check(code = null))
    }

    @Test
    fun anotherKeyFails() = assertFalse(check(certs = setOf(otherKey)))

    @Test
    fun missingCertificatesFail() {
        assertFalse(check(certs = null))
        assertFalse(check(certs = emptySet()))
        assertFalse(check(installedCerts = emptySet()))
    }

    @Test
    fun aRotatedKeyKeepingTheOldOneInItsHistoryPasses() =
        assertTrue(check(installedCerts = setOf(oldKey), certs = setOf(key, oldKey)))

    @Test
    fun anApkMissingOneOfSeveralInstalledSignersFails() =
        assertFalse(check(installedCerts = setOf(key, oldKey), certs = setOf(key)))

    @Test
    fun successIsInstalled() =
        assertEquals(SilentResult.INSTALLED, SilentInstall.resultFor(PackageInstaller.STATUS_SUCCESS))

    @Test
    fun aConfirmRequestIsARefusal() =
        assertEquals(SilentResult.REFUSED, SilentInstall.resultFor(PackageInstaller.STATUS_PENDING_USER_ACTION))

    @Test
    fun everyOtherStatusIsAFailure() {
        listOf(
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE,
            12345,
        ).forEach { assertEquals("status $it", SilentResult.FAILED, SilentInstall.resultFor(it)) }
    }

    @Test
    fun logLineNamesTriggerCodeAndOutcome() {
        assertEquals("trigger=job versionCode=80101 outcome=COMMITTED", SilentInstall.line("job", 80101L, "COMMITTED"))
        assertEquals(
            "trigger=leave versionCode=none outcome=NO_UPDATE (detail)",
            SilentInstall.line("leave", null, "NO_UPDATE", "detail"),
        )
    }
}
