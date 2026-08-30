package fr.ardoise.tasks.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * The app's own package name and signing fingerprint, read at runtime.
 *
 * Ardoise cannot ship a working OAuth client -- Google binds one to a package
 * name plus a signing certificate, and every install signed with a different
 * key needs its own. So the app shows the two values it is actually running
 * with, which is what the setup screen in Google Cloud Console asks for.
 *
 * Reading them from [PackageManager] rather than hardcoding them means the
 * numbers stay right for a debug build, a release build, or someone else's
 * fork.
 */
object SigningIdentity {

    fun packageName(context: Context): String = context.packageName

    /** Uppercase colon-separated SHA-1, the format the Cloud Console expects. */
    fun sha1(context: Context): String? = certificate(context)?.let(::fingerprint)

    /**
     * Kept separate from [PackageManager] so the formatting -- the only part
     * with any logic in it -- can be checked without an Android runtime.
     */
    fun fingerprint(der: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(der)
            .joinToString(":") { "%02X".format(it) }

    private fun certificate(context: Context): ByteArray? = runCatching {
        val manager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val info = manager.getPackageInfo(context.packageName, flags)
            val signing = info.signingInfo ?: return@runCatching null
            val signatures = if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
            signatures?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures
                ?.firstOrNull()
                ?.toByteArray()
        }
    }.getOrNull()
}
