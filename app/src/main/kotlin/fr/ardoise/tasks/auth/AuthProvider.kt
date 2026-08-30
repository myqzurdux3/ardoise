package fr.ardoise.tasks.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Access tokens for the Google Tasks scope.
 *
 * Ardoise deliberately stores no token and holds no client secret. Google
 * Identity Services hands back a fresh access token on every [authorize] call
 * once the user has consented, so there is nothing to persist, nothing to
 * refresh, and no server to run.
 */
class AuthProvider(context: Context) {

    private val client = Identity.getAuthorizationClient(context.applicationContext)

    private val request: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE_TASKS)))
        .build()

    /**
     * Asks for a token. Returns [Outcome.ConsentRequired] the first time, which
     * only an Activity can resolve; background sync treats that as "not yet
     * signed in" rather than an error.
     */
    suspend fun authorize(): Outcome =
        try {
            val result = client.authorize(request).await()
            val pending = result.pendingIntent
            val token = result.accessToken
            when {
                result.hasResolution() && pending != null -> Outcome.ConsentRequired(pending)
                token != null -> Outcome.Granted(token)
                else -> {
                    Log.w(TAG, "authorize() succeeded but carried no access token")
                    Outcome.Failed(NO_TOKEN)
                }
            }
        } catch (error: Exception) {
            val detail = describe(error)
            Log.w(TAG, "authorize() failed: $detail", error)
            Outcome.Failed(detail, notRegistered = isUnregistered(error))
        }

    /** A token without any chance of UI, for workers. Null when consent is missing. */
    suspend fun silentToken(): String? = (authorize() as? Outcome.Granted)?.token

    /**
     * Reads the token out of the Intent returned by the consent screen.
     *
     * Failures are described rather than swallowed: a bare "authorization
     * refused" hides the one thing needed to fix the setup, which is the
     * Google Play services status code.
     */
    fun tokenFromConsent(intent: Intent): Result<String> =
        runCatching {
            val result = client.getAuthorizationResultFromIntent(intent)
            result.accessToken ?: error("Consent returned no access token")
        }.onFailure { error ->
            Log.w(TAG, "Consent result unreadable: ${describe(error)}", error)
        }

    private fun describe(error: Throwable): String = when (error) {
        is ApiException -> {
            val name = CommonStatusCodes.getStatusCodeString(error.statusCode)
            "${error.statusCode} $name${error.status.statusMessage?.let { " ($it)" }.orEmpty()}"
        }

        else -> "${error::class.java.simpleName}: ${error.message}"
    }

    sealed interface Outcome {
        data class Granted(val token: String) : Outcome
        data class ConsentRequired(val pendingIntent: PendingIntent) : Outcome
        data class Failed(val detail: String, val notRegistered: Boolean = false) : Outcome
    }

    companion object {
        const val SCOPE_TASKS = "https://www.googleapis.com/auth/tasks"

        /** Sentinel meaning "authorize() worked but handed back nothing usable". */
        const val NO_TOKEN = "no-access-token"

        private const val TAG = "ArdoiseAuth"
        private const val UNREGISTERED = "UNREGISTERED_ON_API_CONSOLE"

        /**
         * The single most likely failure for anyone installing Ardoise: Google
         * has no OAuth client for this package name and signing certificate,
         * so it rejects the grant right after the account picker -- which
         * looks to the user like their own consent was refused.
         *
         * Play services reports it as a generic status 8 INTERNAL_ERROR and
         * only names the real cause in the status message, so that is where it
         * has to be read from.
         */
        fun isUnregistered(error: Throwable): Boolean =
            error is ApiException &&
                (error.status.statusMessage?.contains(UNREGISTERED) == true ||
                    error.message?.contains(UNREGISTERED) == true)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> continuation.resume(value) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
