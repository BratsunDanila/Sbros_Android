package ru.sbros.app.data

fun validTaskDeadline(value: String?): Boolean=value.isNullOrBlank()||
    (value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))&&runCatching {java.time.LocalDate.parse(value)}.isSuccess)

data class TaskCompletionUndo(val token: Long, val taskId: Int, val focusBefore: Int?, val statusBefore: String = "next")

val WorkspaceState.availableActions: List<ActionTask> get() {
    val completed=projects.filter {it.completedAt!=null}.map {it.id}.toSet()
    return tasks.filter {it.status=="next"&&it.projectId !in completed}
}
val WorkspaceState.focusedAction: ActionTask? get() = availableActions.let { list ->
    list.firstOrNull { it.id == focusedTaskId } ?: list.firstOrNull()
}
val WorkspaceState.remainingActions: List<ActionTask> get() {
    val focused=focusedAction?.id
    return availableActions.filter {it.id!=focused}
}

fun WorkspaceState.reconcileTaskFocus(): WorkspaceState = copy(
    focusedTaskId = focusedAction?.id,
    taskUndo = taskUndo.filter { undo -> tasks.any { it.id == undo.taskId && it.status == "done" } },
)

fun WorkspaceState.focusTask(id: Int): WorkspaceState =
    if (availableActions.any { it.id == id }) copy(focusedTaskId = id) else this

fun WorkspaceState.nextTask(): WorkspaceState {
    val list = availableActions
    if (list.isEmpty()) return reconcileTaskFocus()
    val index = list.indexOfFirst { it.id == focusedAction?.id }
    return copy(focusedTaskId = list[(index + 1) % list.size].id)
}

fun WorkspaceState.completeTask(id: Int, token: Long): WorkspaceState {
    val target=tasks.firstOrNull {it.id==id&&it.status in listOf("next","waiting","someday")}?:return this
    val before = focusedAction?.id
    val list = availableActions
    val after = copy(
        tasks = tasks.map { if (it.id == id) it.copy(status = "done") else it },
        taskUndo = taskUndo.filterNot { it.taskId == id } + TaskCompletionUndo(token, id, before,target.status),
    )
    val focus = if (before == id) {
        after.availableActions.getOrNull(list.indexOfFirst { it.id == id })?.id
            ?: after.availableActions.firstOrNull()?.id
    } else before
    return after.copy(focusedTaskId = focus).reconcileTaskFocus()
}

fun WorkspaceState.undoTaskCompletion(token: Long): WorkspaceState {
    val undo = taskUndo.firstOrNull { it.token == token } ?: return this
    if (tasks.none { it.id == undo.taskId && it.status == "done" }) return this
    // Restore only this status. Never roll back other completions, edits, thoughts or captures.
    return copy(
        tasks = tasks.map { if (it.id == undo.taskId) it.copy(status = undo.statusBefore) else it },
        focusedTaskId = if (undo.focusBefore == undo.taskId) undo.taskId else focusedAction?.id,
        taskUndo = taskUndo.filterNot { it.token == token },
    ).reconcileTaskFocus()
}
