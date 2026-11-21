package nl.markmaaktmedia.guitartuner.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Hands a downloaded APK to the system. */
interface ApkInstaller {
    /** Whether Android lets this app install packages yet. It is allowed per app, once. */
    fun canInstall(): Boolean

    /** Opens the system screen where that is allowed. */
    fun askPermission()

    /** Throws [UpdateException] when the file cannot be handed over; later failures come as [InstallOutcomes]. */
    suspend fun install(file: File, release: AppRelease)
}

/**
 * Installs through a PackageInstaller session.
 *
 * On Android 12 and later the session says the person does not have to confirm it
 * (`USER_ACTION_NOT_REQUIRED`), and with `UPDATE_PACKAGES_WITHOUT_USER_ACTION` in the manifest
 * the system then replaces the app by itself once this app is its installer of record, which is
 * so after the first update made through the app. Until then, and whenever the system wants a
 * confirmation anyway, it answers `STATUS_PENDING_USER_ACTION` and [InstallStatusReceiver] puts
 * its question on the screen, so nobody has to press anything in the app a second time.
 *
 * The system checks the signature and the version, and refuses a build signed with another key
 * or older than the one installed. This class checks that the file is the build that was
 * announced first, so a broken download is named as that and not as a system failure.
 */
class SessionApkInstaller(private val context: Context) : ApkInstaller {

    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun askPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override suspend fun install(file: File, release: AppRelease): Unit = withContext(Dispatchers.IO) {
        val packages = context.packageManager
        val archive = packages.getPackageArchiveInfo(file.path, 0)
        ArchiveCheck.problem(
            archivePackage = archive?.packageName,
            archiveCode = archive?.let { codeOf(it) },
            ownPackage = context.packageName,
            announcedCode = release.versionCode,
        )?.let { throw UpdateException(it) }

        val installer = packages.packageInstaller
        // A session left behind by an attempt that was cut short would only sit there.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = try {
            installer.createSession(params)
        } catch (e: IOException) {
            throw UpdateException(UpdateProblem.Storage, e.message)
        }
        try {
            installer.openSession(id).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                // Mutable on purpose: the system fills in the status and the confirmation
                // intent, and with an immutable one nothing would come back at all.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val result = PendingIntent.getBroadcast(
                    context, id, Intent(context, InstallStatusReceiver::class.java), flags,
                )
                session.commit(result.intentSender)
            }
        } catch (e: IOException) {
            runCatching { installer.abandonSession(id) }
            throw UpdateException(if (e.message?.contains("ENOSPC") == true) UpdateProblem.Storage else UpdateProblem.Other, e.message)
        } catch (e: SecurityException) {
            runCatching { installer.abandonSession(id) }
            throw UpdateException(UpdateProblem.Blocked, e.message)
        }
    }

    private fun codeOf(info: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
}

/** What the system installer said about a session that did not end well. */
data class InstallOutcome(val status: Int, val message: String?)

/** The way [InstallStatusReceiver] tells the running app, which owns the card, what happened. */
object InstallOutcomes {
    val events = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 8)
}

/**
 * Receives the installer's answer for a session.
 *
 * A confirmation question is put on the screen straight away. Success needs nothing: the system
 * replaces the app, which ends this process, and Android does not let the app start itself again
 * from the background, so the person opens it. Anything else is passed on so the card can say why.
 */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let { question ->
                    runCatching { context.startActivity(question.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> InstallOutcomes.events.tryEmit(
                InstallOutcome(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)),
            )
        }
    }
}
