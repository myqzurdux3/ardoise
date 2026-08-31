package fr.ardoise.tasks.data

import kotlinx.serialization.Serializable

@Serializable
data class TaskListDto(
    val id: String,
    val title: String = "",
)

@Serializable
data class TaskListsResponse(
    val items: List<TaskListDto> = emptyList(),
)

@Serializable
data class TaskDto(
    val id: String,
    val title: String = "",
    val status: String = STATUS_NEEDS_ACTION,
    val due: String? = null,
    val parent: String? = null,
) {
    val isCompleted: Boolean get() = status == STATUS_COMPLETED
    val isSubtask: Boolean get() = parent != null

    companion object {
        const val STATUS_NEEDS_ACTION = "needsAction"
        const val STATUS_COMPLETED = "completed"
    }
}

@Serializable
data class TasksResponse(
    val items: List<TaskDto> = emptyList(),
)
