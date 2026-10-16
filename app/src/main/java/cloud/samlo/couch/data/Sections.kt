package cloud.samlo.couch.data

/** Row ids and the user's order for them, shared by the home screen and settings. */
object Sections {
    const val APPS = "apps"
    const val INPUTS = "inputs"

    /** [ids] in the user's order; rows the user never placed keep their default order, at the end. */
    fun order(ids: List<String>, config: Config): List<String> =
        config.sectionOrder.filter { it in ids } + ids.filter { it !in config.sectionOrder }

    fun channelId(id: Long) = "ch:$id"
}
