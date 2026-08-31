package fr.ardoise.tasks.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Minimal REST client for the Google Tasks API.
 *
 * The official `google-api-services-tasks` library pulls a large transitive
 * tree and is awkward on Android. Ardoise needs three calls, so it speaks the
 * REST endpoints directly.
 */
class TasksApi(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lists(token: String): List<TaskListDto> {
        val url = baseUrl.newBuilder()
            .addPathSegments("users/@me/lists")
            .addQueryParameter("maxResults", "100")
            .build()
        return json.decodeFromString<TaskListsResponse>(get(url, token)).items
    }

    /**
     * Open tasks of a list, in Google's own ordering.
     *
     * A single page of 100 is deliberate: Ardoise renders at most a dozen
     * lines, and the API already returns them ordered by position.
     */
    suspend fun tasks(token: String, listId: String): List<TaskDto> {
        val url = baseUrl.newBuilder()
            .addPathSegment("lists")
            // Singular, so an id containing a slash cannot rewrite the path.
            .addPathSegment(listId)
            .addPathSegment("tasks")
            .addQueryParameter("showCompleted", "false")
            .addQueryParameter("showHidden", "false")
            .addQueryParameter("maxResults", "100")
            .build()
        return json.decodeFromString<TasksResponse>(get(url, token)).items
    }

    suspend fun completeTask(token: String, listId: String, taskId: String) {
        val url = baseUrl.newBuilder()
            .addPathSegment("lists")
            .addPathSegment(listId)
            .addPathSegment("tasks")
            .addPathSegment(taskId)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .patch(COMPLETE_BODY.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        execute(request)
    }

    private suspend fun get(url: HttpUrl, token: String): String =
        execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        )

    private suspend fun execute(request: Request): String {
        val response = client.newCall(request).await()
        response.use {
            val body = it.body?.string().orEmpty()
            if (it.isSuccessful) return body
            throw classify(it.code, body)
        }
    }

    /**
     * Maps an HTTP status onto something the caller can act on.
     *
     * 403 is the subtle one: Google uses it both for a revoked grant and for
     * quota and rate limits. Treating every 403 as "signed out" told the user
     * to sign in again while their session was fine, and -- because that
     * outcome is not retryable -- stopped WorkManager from ever backing off and
     * trying later, which is exactly what a rate limit needs.
     */
    private fun classify(code: Int, body: String): IOException = when {
        code == 401 -> AuthExpiredException(code, body)
        code == 403 && RATE_LIMIT_REASONS.none { body.contains(it, ignoreCase = true) } ->
            AuthExpiredException(code, body)

        code == 404 -> NotFoundException(code, body)
        else -> ApiException(code, body)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://tasks.googleapis.com/tasks/v1/"
        private const val COMPLETE_BODY = """{"status":"completed"}"""
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val RATE_LIMIT_REASONS = listOf(
            "rateLimitExceeded",
            "userRateLimitExceeded",
            "quotaExceeded",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Suspends on the call and cancels the socket if the coroutine is cancelled.
 *
 * A blocking `execute()` inside `withContext` ignores cancellation entirely,
 * so an abandoned sync used to hold its connection -- and a worker's wakelock --
 * for the full read timeout.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
}

open class ApiException(val code: Int, val payload: String) :
    IOException("Google Tasks API returned $code: ${payload.take(200)}")

/** The grant is gone: only a fresh authorization can fix it. */
class AuthExpiredException(code: Int, payload: String) : ApiException(code, payload)

/** The list or task no longer exists server-side. */
class NotFoundException(code: Int, payload: String) : ApiException(code, payload)
