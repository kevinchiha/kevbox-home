package cloud.samlo.couch

import android.service.notification.NotificationListenerService

/**
 * Exists only so Android lets Couch read active media sessions: reading other apps' sessions
 * requires notification access, granted once in settings (or with
 * `adb shell cmd notification allow_listener cloud.samlo.couch/cloud.samlo.couch.MediaListener`).
 */
class MediaListener : NotificationListenerService()
