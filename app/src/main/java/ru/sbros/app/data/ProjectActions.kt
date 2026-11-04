package ru.sbros.app.data

data class ProjectProgress(val done: Int,val total: Int) {
    val allDone get()=total>0&&done==total
    val label get()=when {
        total==0->"Добавь первое действие"
        allDone->"Все шаги выполнены · $done из $total"
        else->"Выполнено $done из $total"
    }
}

fun WorkspaceState.projectProgress(id: Int): ProjectProgress {
    val steps=tasks.filter {it.projectId==id}
    return ProjectProgress(steps.count {it.status=="done"},steps.size)
}

fun WorkspaceState.finishProject(id: Int,at: String): WorkspaceState {
    val project=projects.first {it.id==id}
    require(project.completedAt==null&&project.completionReady&&projectProgress(id).allDone)
    return copy(projects=projects.map {if(it.id==id)it.copy(completedAt=at)else it})
}

fun WorkspaceState.reopenProject(id: Int): WorkspaceState = copy(
    projects=projects.map {if(it.id==id)it.copy(completedAt=null)else it},
)
