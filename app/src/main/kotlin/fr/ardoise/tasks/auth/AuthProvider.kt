package fr.ardoise.tasks.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
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
                else -> Outcome.Failed(IllegalStateException("Authorization returned no access token"))
            }
        } catch (error: Exception) {
            Outcome.Failed(error)
        }

    /** A token without any chance of UI, for workers. Null when consent is missing. */
    suspend fun silentToken(): String? = (authorize() as? Outcome.Granted)?.token

    /** Reads the token out of the Intent returned by the consent screen. */
    fun tokenFromConsent(intent: Intent): String? =
        runCatching { client.getAuthorizationResultFromIntent(intent).accessToken }.getOrNull()

    sealed interface Outcome {
        data class Granted(val token: String) : Outcome
        data class ConsentRequired(val pendingIntent: PendingIntent) : Outcome
        data class Failed(val error: Throwable) : Outcome
    }

    companion object {
        const val SCOPE_TASKS = "https://www.googleapis.com/auth/tasks"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> continuation.resume(value) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
