package fr.ardoise.tasks.data

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TasksApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: TasksApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = TasksApi(baseUrl = server.url("/tasks/v1/").toString().toHttpUrl())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lists are parsed and the token is sent as a bearer`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"abc","title":"Courses"},{"id":"def","title":"Travail"}]}"""
            )
        )

        val lists = api.lists("TOKEN")

        assertEquals(listOf("Courses", "Travail"), lists.map { it.title })
        val request = server.takeRequest()
        assertEquals("Bearer TOKEN", request.getHeader("Authorization"))
        assertTrue(request.path!!.contains("/users/@me/lists"))
    }

    @Test
    fun `tasks are requested without completed or hidden entries`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"items":[{"id":"1","title":"Pain","status":"needsAction","due":"2026-08-30T00:00:00.000Z"}]}"""
            )
        )

        val tasks = api.tasks("TOKEN", "listId")

        assertEquals("Pain", tasks.single().title)
        assertEquals("2026-08-30T00:00:00.000Z", tasks.single().due)
        val path = server.takeRequest().path!!
        assertTrue(path.contains("showCompleted=false"))
        assertTrue(path.contains("showHidden=false"))
    }

    /** Google adds fields over time; an unknown one must not break a sync. */
    @Test
    fun `unknown JSON fields are ignored`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"kind":"tasks#tasks","etag":"x","items":[{"id":"1","title":"Pain","selfLink":"..."}]}"""
            )
        )

        assertEquals("Pain", api.tasks("TOKEN", "l").single().title)
    }

    @Test
    fun `completing a task sends a PATCH with the completed status`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        api.completeTask("TOKEN", "listId", "taskId")

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertTrue(request.path!!.endsWith("/lists/listId/tasks/taskId"))
        assertEquals("""{"status":"completed"}""", request.body.readUtf8())
    }

    @Test
    fun `a 401 is surfaced as an auth failure, not a generic error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid"}"""))

        val error = runCatching { api.tasks("STALE", "l") }.exceptionOrNull()

        assertTrue(error is AuthExpiredException)
    }

    @Test
    fun `a plain 403 is treated as an auth failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"forbidden"}}"""))

        assertTrue(runCatching { api.lists("T") }.exceptionOrNull() is AuthExpiredException)
    }

    /**
     * Google reuses 403 for quota as well as for a revoked grant. Calling every
     * 403 an auth failure told the user to sign in again while their session
     * was fine, and marked the outcome unretryable so the sync never backed off.
     */
    @Test
    fun `a 403 for a rate limit stays retryable rather than looking like a sign-out`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"error":{"errors":[{"reason":"rateLimitExceeded"}],"message":"Rate Limit Exceeded"}}"""
            )
        )

        val error = runCatching { api.lists("T") }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertTrue(error !is AuthExpiredException)
    }

    @Test
    fun `a 403 for exceeded quota is also retryable`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"errors":[{"reason":"quotaExceeded"}]}}""")
        )

        assertTrue(runCatching { api.lists("T") }.exceptionOrNull() !is AuthExpiredException)
    }

    /** A deleted list must be distinguishable, so the selection can be cleared. */
    @Test
    fun `a 404 is surfaced as not found`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("gone"))

        assertTrue(runCatching { api.tasks("T", "missing") }.exceptionOrNull() is NotFoundException)
    }

    @Test
    fun `a list id is path-encoded rather than splicing into the path`() = runTest {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))

        api.tasks("T", "a/b")

        val path = server.takeRequest().path!!
        assertTrue(path.contains("a%2Fb"))
    }

    @Test
    fun `a 500 stays a plain API error so the worker retries`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = runCatching { api.lists("T") }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertTrue(error !is AuthExpiredException)
        assertEquals(500, (error as ApiException).code)
    }
}
