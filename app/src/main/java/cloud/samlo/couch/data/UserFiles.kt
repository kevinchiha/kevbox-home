package cloud.samlo.couch.data

import android.content.Context
import java.io.File

/**
 * The folder the user can reach over ADB (`Android/data/<package>/files`). Resolved lazily
 * because the first lookup touches storage, which must stay off the UI thread's critical path.
 */
class UserFiles(private val context: Context) {
    val dir: File? by lazy { context.getExternalFilesDir(null) }
    val icons: File? by lazy { dir?.let { File(it, "icons") } }
}
