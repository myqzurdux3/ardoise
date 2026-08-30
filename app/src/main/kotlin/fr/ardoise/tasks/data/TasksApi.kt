package fr.ardoise.tasks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

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
        val body = get(url, token)
        return json.decodeFromString<TaskListsResponse>(body).items
    }

    /**
     * Open tasks of a list, in Google's own ordering.
     *
     * A single page of 100 is deliberate: Ardoise renders at most a dozen
     * lines, and the API already returns them ordered by position.
     */
    suspend fun tasks(token: String, listId: String): List<TaskDto> {
        val url = baseUrl.newBuilder()
            .addPathSegments("lists/$listId/tasks")
            .addQueryParameter("showCompleted", "false")
            .addQueryParameter("showHidden", "false")
            .addQueryParameter("maxResults", "100")
            .build()
        val body = get(url, token)
        return json.decodeFromString<TasksResponse>(body).items
    }

    suspend fun completeTask(token: String, listId: String, taskId: String) {
        val url = baseUrl.newBuilder()
            .addPathSegments("lists/$listId/tasks/$taskId")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .patch(COMPLETE_BODY.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        execute(request)
    }

    private suspend fun get(url: HttpUrl, token: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) return@use body
            // 401 means the cached access token expired; the caller re-authorizes
            // silently rather than surfacing an error to the user.
            if (response.code == 401 || response.code == 403) {
                throw AuthExpiredException(response.code, body)
            }
            throw ApiException(response.code, body)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://tasks.googleapis.com/tasks/v1/"
        private const val COMPLETE_BODY = """{"status":"completed"}"""
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

open class ApiException(val code: Int, val payload: String) :
    IOException("Google Tasks API returned $code: ${payload.take(200)}")

class AuthExpiredException(code: Int, payload: String) : ApiException(code, payload)
