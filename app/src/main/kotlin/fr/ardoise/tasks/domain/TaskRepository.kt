package fr.ardoise.tasks.domain

import fr.ardoise.tasks.auth.AuthProvider
import fr.ardoise.tasks.data.ArdoiseSettings
import fr.ardoise.tasks.data.AuthExpiredException
import fr.ardoise.tasks.data.SettingsStore
import fr.ardoise.tasks.data.SnapshotStore
import fr.ardoise.tasks.data.TaskListDto
import fr.ardoise.tasks.data.TasksApi
import fr.ardoise.tasks.render.SurfaceRenderer

/**
 * The single place that knows the order of operations: get a token, read the
 * list, cache it, redraw both surfaces.
 *
 * Failures never blank the lock screen. The cached snapshot stays on screen,
 * flagged stale, until a later sync succeeds.
 */
class TaskRepository(
    private val api: TasksApi,
    private val auth: AuthProvider,
    private val settingsStore: SettingsStore,
    private val snapshotStore: SnapshotStore,
    private val surfaces: SurfaceRenderer,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun sync(): SyncOutcome {
        val settings = settingsStore.current()
        val listId = settings.listId
        if (listId.isNullOrBlank()) return SyncOutcome.NotConfigured

        val token = auth.silentToken() ?: return degrade(settings, SyncOutcome.NeedsSignIn)

        return try {
            val dtos = api.tasks(token, listId)
            val snapshot = SnapshotMapper.toSnapshot(listId, settings.listTitle, dtos, now())
            snapshotStore.save(snapshot)
            surfaces.refresh(snapshot, settings)
            SyncOutcome.Success(snapshot)
        } catch (expired: AuthExpiredException) {
            degrade(settings, SyncOutcome.NeedsSignIn)
        } catch (error: Exception) {
            degrade(settings, SyncOutcome.Failed(error))
        }
    }

    /** Completes a task, then resyncs so both surfaces reflect it immediately. */
    suspend fun completeTask(taskId: String): SyncOutcome {
        val settings = settingsStore.current()
        val listId = settings.listId
        if (listId.isNullOrBlank()) return SyncOutcome.NotConfigured

        val token = auth.silentToken() ?: return degrade(settings, SyncOutcome.NeedsSignIn)

        return try {
            api.completeTask(token, listId, taskId)
            sync()
        } catch (expired: AuthExpiredException) {
            degrade(settings, SyncOutcome.NeedsSignIn)
        } catch (error: Exception) {
            degrade(settings, SyncOutcome.Failed(error))
        }
    }

    suspend fun availableLists(): Result<List<TaskListDto>> {
        val token = auth.silentToken() ?: return Result.failure(IllegalStateException("Not authorized"))
        return runCatching { api.lists(token) }
    }

    /** Redraws from cache alone, for boot and for settings changes. */
    suspend fun refreshSurfacesFromCache() {
        surfaces.refresh(snapshotStore.current(), settingsStore.current())
    }

    private suspend fun degrade(settings: ArdoiseSettings, outcome: SyncOutcome): SyncOutcome {
        snapshotStore.markStale()
        surfaces.refresh(snapshotStore.current(), settings)
        return outcome
    }
}

sealed interface SyncOutcome {
    data class Success(val snapshot: RenderSnapshot) : SyncOutcome
    data object NotConfigured : SyncOutcome
    data object NeedsSignIn : SyncOutcome
    data class Failed(val error: Throwable) : SyncOutcome

    val isSuccess: Boolean get() = this is Success
    /** Worth a WorkManager retry; a missing list or missing consent is not. */
    val isRetryable: Boolean get() = this is Failed
}
