package ru.sbros.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Reader/writer for importing snapshots from pre-Room releases. */
internal fun encode(s: WorkspaceState) = JSONObject().apply {
    put("tasks",JSONArray(s.tasks.map { JSONObject().put("id",it.id).put("title",it.title).put("area",it.area).put("energy",it.energy).put("hint",it.hint).put("status",it.status).put("projectId",it.projectId).put("dueDate",it.dueDate?:JSONObject.NULL) }))
    put("thoughts",JSONArray(s.thoughts.map { JSONObject().put("id",it.id).put("title",it.title).put("body",it.body).put("idea",it.idea).put("days",it.daysAgo).put("time",it.time).put("favorite",it.favorite).put("sourceText",it.sourceText) }))
    put("inbox",JSONArray(s.inbox)); put("events",JSONArray(s.events.map { JSONObject().put("id",it.id).put("title",it.title).put("date",it.date).put("time",it.time) }))
    put("focusedTaskId",s.focusedTaskId)
    put("taskUndo",JSONArray(s.taskUndo.map {JSONObject().put("token",it.token).put("taskId",it.taskId).put("focusBefore",it.focusBefore).put("statusBefore",it.statusBefore)}))
    put("projects",JSONArray(s.projects.map {JSONObject().put("id",it.id).put("title",it.title).put("area",it.area).put("outcome",it.outcome)}))
    put("materials",JSONArray(s.materials.map {JSONObject().put("id",it.id).put("title",it.title).put("body",it.body)}))
}.toString()
internal fun decode(raw: String): WorkspaceState {
    val j=JSONObject(raw)
    fun objects(key: String) = j.getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    return WorkspaceState(objects("tasks").map { ActionTask(it.getInt("id"),it.getString("title"),it.getString("area"),it.getInt("energy"),it.getString("hint"),it.getString("status"),if(it.isNull("projectId"))null else it.getInt("projectId"),dueDate=if(it.isNull("dueDate"))null else it.optString("dueDate").takeIf {date->date.isNotBlank()}) },
        objects("thoughts").map { Thought(it.getInt("id"),it.getString("title"),it.getString("body"),it.getBoolean("idea"),it.getInt("days"),it.getString("time"),it.getBoolean("favorite"),if(it.isNull("sourceText"))null else it.optString("sourceText").takeIf {text->text.isNotBlank()}) },
        j.getJSONArray("inbox").let { a -> (0 until a.length()).map { a.getString(it) } },
        objects("events").map { CalendarEvent(it.getInt("id"),it.getString("title"),it.getString("date"),it.getString("time")) },
        if(j.has("focusedTaskId")&&!j.isNull("focusedTaskId"))j.getInt("focusedTaskId") else null,
        j.optJSONArray("taskUndo")?.let {a->(0 until a.length()).map {i->a.getJSONObject(i).let {u->TaskCompletionUndo(u.getLong("token"),u.getInt("taskId"),if(u.has("focusBefore")&&!u.isNull("focusBefore"))u.getInt("focusBefore") else null,u.optString("statusBefore","next"))}}}?:emptyList(),
        if(j.has("projects"))objects("projects").map {Project(it.getInt("id"),it.getString("title"),it.getString("area"),it.getString("outcome"))} else demoProjects(),
        if(j.has("materials"))objects("materials").map {ReferenceMaterial(it.getInt("id"),it.getString("title"),it.getString("body"))} else demoMaterials())
}
