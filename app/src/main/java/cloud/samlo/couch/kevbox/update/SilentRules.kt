package cloud.samlo.couch.kevbox.update

import cloud.samlo.couch.update.Release

/** Why a silent install may or may not run now. Logged as is, so each check has its own name. */
enum class SilentReason {
    // eligible() says yes, and quiet() says nobody is watching.
    ELIGIBLE, QUIET,
    // eligible(), in the order it checks them.
    OLD_ANDROID, NO_BLOCK, NOT_YET, NO_PERMISSION, REFUSED, OUT_OF_ATTEMPTS,
    // quiet().
    PLAYING, IN_USE,
    // SilentInstall.run: no stored release newer than this build, another install holds the slot,
    // the download broke off (not counted), the APK failed a check, the session or commit failed,
    // and Android has the update.
    NO_UPDATE, BUSY, DOWNLOAD_FAILED, BAD_APK, FAILED, COMMITTED,
}

/**
 * The rules behind the silent update, free of Android so they run as JVM tests: the caller reads the
 * SDK level, the clocks, the install permission and the stored state and passes them in. They copy
 * KevBox TV's and KevBox Live's, minus the early-user list and the 24 h wait (Home has no sign-in),
 * and with "nothing playing" required whether Home is on screen or not.
 */
object SilentRules {
    /** Android 12, the first that installs an update with no screen. */
    const val MIN_SDK = 31
    const val MAX_ATTEMPTS = 3
    /** With every Home activity out of sight, this long since the last one stopped counts as nobody watching. */
    const val OUT_OF_SIGHT_MS = 15 * 60_000L
    /** With a Home activity on screen, this long with no remote key counts as nobody watching. */
    const val IDLE_ON_SCREEN_MS = 3 * 60 * 60_000L
    /** A release still not installed this long after its `after` brings the Update chip back. */
    const val SAFETY_NET_MS = 72 * 60 * 60_000L

    /** The server's clock from a `Date` header in epoch ms, or [boxNowMs] when there was none. */
    fun serverNow(dateMs: Long?, boxNowMs: Long): Long = dateMs?.takeIf { it > 0 } ?: boxNowMs

    /**
     * [afterMs] (server clock) moved onto the box clock: `after + boxNow - serverNow`. Saturates at
     * [Long.MAX_VALUE] instead of wrapping, and keeps [afterMs] when the clock gap itself overflows.
     */
    fun afterOnBox(afterMs: Long, serverNowMs: Long, boxNowMs: Long): Long {
        val gap = try {
            Math.subtractExact(boxNowMs, serverNowMs)
        } catch (e: ArithmeticException) {
            return afterMs
        }
        return try {
            Math.addExact(afterMs, gap)
        } catch (e: ArithmeticException) {
            if (gap > 0) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }

    /**
     * [release] as stored after a fetch: its block's `after` moved onto the box clock, using the
     * response's `Date` header [dateMs] when it had one. A release with no block is returned as is.
     */
    fun onBoxClock(release: Release, dateMs: Long?, boxNowMs: Long): Release {
        val block = release.silent ?: return release
        return release.withSilentAfterBox(afterOnBox(block.afterMs, serverNow(dateMs, boxNowMs), boxNowMs))
    }

    /**
     * [SilentReason.ELIGIBLE], or the first check that fails: Android 12 or later, a silent block,
     * [boxNowMs] at or past `after` (on the box clock), the install permission, not refused for this
     * versionCode, fewer than [MAX_ATTEMPTS] attempts. [state] must be for [release]'s versionCode.
     */
    fun eligible(sdk: Int, release: Release, boxNowMs: Long, canInstall: Boolean, state: SilentState.Snapshot): SilentReason {
        if (sdk < MIN_SDK) return SilentReason.OLD_ANDROID
        val after = afterBox(release) ?: return SilentReason.NO_BLOCK
        if (boxNowMs < after) return SilentReason.NOT_YET
        if (!canInstall) return SilentReason.NO_PERMISSION
        if (state.refused) return SilentReason.REFUSED
        if (state.attempts >= MAX_ATTEMPTS) return SilentReason.OUT_OF_ATTEMPTS
        return SilentReason.ELIGIBLE
    }

    /**
     * [SilentReason.QUIET] when nobody is watching: nothing [playing], and either the screen is off,
     * every Home activity has been out of sight for [OUT_OF_SIGHT_MS], or one is on screen with no
     * key for [IDLE_ON_SCREEN_MS]. [screenOff] covers a box that went to sleep with Home on top:
     * Android never stops Home then, so [onScreen] stays true. [onScreen] null (unknown) counts as on
     * screen. A null time is unknown and counts as just now, so it never makes the box look idle.
     */
    fun quiet(
        playing: Boolean,
        onScreen: Boolean?,
        sinceHiddenMs: Long?,
        sinceKeyMs: Long?,
        screenOff: Boolean = false,
    ): SilentReason {
        if (playing) return SilentReason.PLAYING
        if (screenOff) return SilentReason.QUIET
        val idle = if (onScreen == false) {
            sinceHiddenMs != null && sinceHiddenMs >= OUT_OF_SIGHT_MS
        } else {
            sinceKeyMs != null && sinceKeyMs >= IDLE_ON_SCREEN_MS
        }
        return if (idle) SilentReason.QUIET else SilentReason.IN_USE
    }

    /**
     * Whether the Update chip stays hidden on Android 12 and later because silent install can still
     * handle [release]: it has a block, the box has the install permission, the version is not
     * refused and has attempts left, and fewer than [SAFETY_NET_MS] have passed since `after` on the
     * box clock. Always false below Android 12, where the chip behaves as Couch's.
     */
    fun holdsPrompt(sdk: Int, release: Release, boxNowMs: Long, canInstall: Boolean, state: SilentState.Snapshot): Boolean {
        if (sdk < MIN_SDK || !canInstall) return false
        val after = afterBox(release) ?: return false
        if (state.refused || state.attempts >= MAX_ATTEMPTS) return false
        // Overflow-safe form of `boxNowMs < after + SAFETY_NET_MS`.
        return boxNowMs < after || boxNowMs - after < SAFETY_NET_MS
    }

    /**
     * Whether leaving Home queues the run 15 min later. Not below Android 12, and not while a silent
     * run is in progress: JobScheduler stops a running job when a job with its id is scheduled again,
     * and the run in progress already reads the latest state.
     */
    fun queuesAfterLeaving(sdk: Int, running: Boolean): Boolean = sdk >= MIN_SDK && !running

    /** The block's `after` on the box clock; the block's own when the release was stored without one. */
    private fun afterBox(release: Release): Long? {
        val block = release.silent ?: return null
        return release.silentAfterBoxMs ?: block.afterMs
    }
}
