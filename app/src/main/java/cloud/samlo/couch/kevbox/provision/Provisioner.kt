package cloud.samlo.couch.kevbox.provision

import java.io.File

/** Stub: task 11 fills this in. */
object Provisioner {
    enum class Outcome { NONE, APPLIED, REJECTED, FAILED }

    @Suppress("UNUSED_PARAMETER")
    fun applyIfPresent(dir: File?): Outcome = Outcome.NONE
}
