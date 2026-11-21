package nl.markmaaktmedia.guitartuner

import android.app.Application
import nl.markmaaktmedia.markupdate.MarkUpdate
import nl.markmaaktmedia.markupdate.MarkUpdateConfig

/**
 * Sets up the updater once for the process. The updater is the shared markupdate module, the same
 * code in every app; only this configuration is Guitar Tuner's own.
 */
class GuitarTunerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MarkUpdate.init(
            this,
            MarkUpdateConfig(
                owner = "Marukiee",
                repo = "GuitarTuner",
                apkAsset = "guitartuner.apk",
                // Releases are tagged v<run number>, and the run number is also the versionCode, so
                // that is the version the tag can be compared with. versionName ("1.0.<n>") would
                // read as older than every tag.
                currentVersion = BuildConfig.VERSION_CODE.toString(),
                appName = "Guitar Tuner",
                notificationIcon = R.drawable.sym_system_update,
                // changelog.json lives in the assets here and is keyed by versionName, which the
                // tag cannot be matched to; the card takes its two lines from the release text.
                changelogPath = null,
            ),
        )
    }
}
