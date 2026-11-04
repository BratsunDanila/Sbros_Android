package ru.sbros.app.data

data class ActionTask(val id: Int, val title: String, val area: String, val energy: Int, val hint: String, val status: String = "next", val projectId: Int? = null, val captureId: String?=null, val dueDate: String?=null)
data class Project(val id: Int, val title: String, val area: String, val outcome: String,
    val completedAt: String?=null,val completionReady: Boolean=false)
data class ReferenceMaterial(val id: Int, val title: String, val body: String)
data class Thought(val id: Int, val title: String, val body: String, val idea: Boolean, val daysAgo: Int, val time: String, val favorite: Boolean = false, val sourceText: String? = null, val captureId: String?=null, val createdAt: String?=null)
data class CalendarEvent(val id: Int, val title: String, val date: String, val time: String)
data class WorkspaceState(val tasks: List<ActionTask>, val thoughts: List<Thought>, val inbox: List<String>, val events: List<CalendarEvent>,
    val focusedTaskId: Int? = null, val taskUndo: List<TaskCompletionUndo> = emptyList(),
    val projects: List<Project> = emptyList(), val materials: List<ReferenceMaterial> = emptyList(),
    val captures: List<StoredCapture> = emptyList(), val idHighWater: Map<String,Int> = emptyMap(),val recordRevisions: Map<String,String> = emptyMap(),val captureResults: Map<String,List<String>> = emptyMap())

data class StoredCapture(val id: String,val text: String,val createdAt: String,val status: String="inbox",val confirmedAt: String?=null,val source: String="text",val parentId: String?=null)


fun emptyState()=WorkspaceState(emptyList(),emptyList(),emptyList(),emptyList(),projects=emptyList(),materials=emptyList())
fun WorkspaceState.nextLocalId(kind: String): Int = maxOf(idHighWater[kind]?:0,when(kind){"action"->tasks.maxOfOrNull{it.id};"project"->projects.maxOfOrNull{it.id};"thought"->thoughts.maxOfOrNull{it.id};"event"->events.maxOfOrNull{it.id};"reference"->materials.maxOfOrNull{it.id};else->0}?:0)+1
