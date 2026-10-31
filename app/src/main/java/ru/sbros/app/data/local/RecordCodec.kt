package ru.sbros.app.data.local

import org.json.JSONArray
import org.json.JSONObject
import ru.sbros.app.data.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

internal fun now()=Instant.now().toString()
internal fun uuid()=UUID.randomUUID().toString()
internal fun LocalRecord.duplicate()=LocalRecord().also {r->r.scope=scope;r.id=id;r.kind=kind;r.localId=localId;r.payload=payload;r.projectId=projectId;r.captureId=captureId;r.createdAt=createdAt;r.updatedAt=updatedAt;r.deletedAt=deletedAt;r.serverVersion=serverVersion;r.changeId=changeId;r.dirty=dirty}
internal fun JSONObject.optional(key: String)=if(isNull(key))null else optString(key).takeIf {it.isNotEmpty()}
internal fun canonicalJson(value: Any?): String=when(value){null,JSONObject.NULL->"null";is JSONObject->value.keys().asSequence().toList().sorted().joinToString(",","{","}"){JSONObject.quote(it)+":"+canonicalJson(value.get(it))};is JSONArray->(0 until value.length()).joinToString(",","[","]"){canonicalJson(value.get(it))};is String->JSONObject.quote(value);else->value.toString()}

/** JSON is a per-record payload, not a second database or an AI heuristic. */
internal object RecordCodec {
    fun decode(records: List<LocalRecord>,ui: String): DemoState {
        val rows=records.filter {it.deletedAt==null}
        fun of(kind: String)=rows.filter {it.kind==kind}
        val projects=of("project").map {r->val p=JSONObject(r.payload);DemoProject(r.localId,p.getString("title"),p.optString("area"),p.optString("outcome"))}
        val projectIds=of("project").associate {it.id to it.localId}
        val captures=of("capture").sortedByDescending {it.createdAt}.map {r->val p=JSONObject(r.payload);StoredCapture(r.id,p.getString("text"),r.createdAt,p.optString("status","inbox"),p.optional("confirmedAt"),p.optString("source","text"),r.captureId)}
        val tasks=of("action").map {r->val p=JSONObject(r.payload);DemoTask(r.localId,p.getString("title"),p.optString("area"),p.optInt("energy"),p.optString("hint"),p.getString("status"),projectIds[r.projectId],r.captureId,p.optional("dueDate"))}
        val thoughts=of("thought").sortedByDescending {it.createdAt}.map {r->val p=JSONObject(r.payload);val date=Instant.parse(r.createdAt).atZone(ZoneId.systemDefault()).toLocalDate();Thought(r.localId,p.getString("title"),p.getString("body"),p.optBoolean("idea"),ChronoUnit.DAYS.between(date,LocalDate.now()).toInt().coerceAtLeast(0),p.optString("time"),p.optBoolean("favorite"),p.optional("sourceText"),r.captureId)}
        val events=of("event").map {r->val p=JSONObject(r.payload);DemoEvent(r.localId,p.getString("title"),p.getString("date"),p.optString("time"))}
        val materials=of("reference").map {r->val p=JSONObject(r.payload);DemoMaterial(r.localId,p.getString("title"),p.optString("body"))}
        val meta=JSONObject(ui)
        val undo=meta.optJSONArray("undo")?.let {a->(0 until a.length()).map {i->a.getJSONObject(i).let {TaskCompletionUndo(it.getLong("token"),it.getInt("task"),it.optInt("focus").takeIf{n->n>0},it.optString("status","next"))}}}?:emptyList()
        val results=rows.filter{it.captureId!=null&&it.kind in setOf("action","project","thought","event","reference")}.groupBy{it.captureId!!}.mapValues{(_,v)->v.map{JSONObject(it.payload).getString("title")}}
        return DemoState(tasks,thoughts,captures.filter {it.status=="inbox"}.map {it.text},events,meta.optInt("focus").takeIf {it>0},undo,projects,materials,captures,records.groupBy{it.kind}.mapValues {(_,v)->v.maxOf {it.localId}},records.associate{"${it.kind}:${it.localId}" to it.changeId},results).reconcileTaskFocus()
    }
    fun ui(state: DemoState)=JSONObject().put("focus",state.focusedTaskId).put("undo",JSONArray(state.taskUndo.map {JSONObject().put("token",it.token).put("task",it.taskId).put("focus",it.focusBefore).put("status",it.statusBefore)})).toString()
    fun encode(scope: String,state: DemoState,old: List<LocalRecord>): List<LocalRecord> {
        val prior=old.associateBy {it.kind to it.localId};val output=mutableListOf<LocalRecord>();val timestamp=now()
        fun add(kind: String,id: Int,payload: JSONObject,project: String?=null,capture: String?=null,stableId: String?=null,created: String?=null) {
            val before=prior[kind to id]
            val captureRef=capture?:before?.captureId
            if(kind=="capture"&&before!=null)require(JSONObject(before.payload).getString("text")==payload.getString("text")){"Original capture is immutable"}
            val merged=JSONObject(before?.payload?:"{}")
            payload.keys().forEach {key->merged.put(key,payload.get(key))}
            val body=canonicalJson(merged)
            if(before!=null&&canonicalJson(JSONObject(before.payload))==body&&before.projectId==project&&before.captureId==captureRef&&before.deletedAt==null){output+=before;return}
            if(body.toByteArray(Charsets.UTF_8).size>240_000)throw RecordTooLarge()
            output+=LocalRecord().also {r->r.scope=scope;r.id=before?.id?:stableId?:uuid();r.kind=kind;r.localId=id;r.payload=body;r.projectId=project;r.captureId=captureRef;r.createdAt=before?.createdAt?:created?:timestamp;r.updatedAt=timestamp;r.serverVersion=before?.serverVersion?:0;r.changeId=uuid();r.dirty=true}
        }
        state.projects.forEach {p->add("project",p.id,JSONObject().put("title",p.title).put("area",p.area).put("outcome",p.outcome))}
        val projectUuids=output.filter {it.kind=="project"}.associate {it.localId to it.id}
        state.tasks.forEach {t->val completed=if(t.status=="done")prior["action" to t.id]?.let {JSONObject(it.payload).optional("completedAt")}?:timestamp else null
            add("action",t.id,JSONObject().put("title",t.title).put("area",t.area).put("energy",t.energy).put("hint",t.hint).put("status",t.status).put("dueDate",t.dueDate?:JSONObject.NULL).put("completedAt",completed?:JSONObject.NULL),projectUuids[t.projectId],t.captureId)}
        state.thoughts.forEach {t->add("thought",t.id,JSONObject().put("title",t.title).put("body",t.body).put("idea",t.idea).put("time",t.time).put("favorite",t.favorite).put("sourceText",t.sourceText?:JSONObject.NULL),capture=t.captureId,created=LocalDate.now().minusDays(t.daysAgo.toLong()).atTime(runCatching{LocalTime.parse(t.time)}.getOrDefault(LocalTime.MIDNIGHT)).atZone(ZoneId.systemDefault()).toInstant().toString())}
        state.events.forEach {e->add("event",e.id,JSONObject().put("title",e.title).put("date",e.date).put("time",e.time))}
        state.materials.forEach {m->add("reference",m.id,JSONObject().put("title",m.title).put("body",m.body))}
        var captureId=old.filter {it.kind=="capture"}.maxOfOrNull{it.localId}?:0
        state.captures.forEach {c->val id=old.find {it.id==c.id}?.localId?:++captureId;add("capture",id,JSONObject().put("text",c.text).put("status",c.status).put("confirmedAt",c.confirmedAt?:JSONObject.NULL).put("source",c.source),capture=c.parentId,stableId=c.id,created=c.createdAt)}
        output+=old.filter{it.kind.startsWith("local_")||it.kind=="analysis_run"}
        val kept=output.map {it.id}.toSet()
        old.filter {it.id !in kept&&it.kind!="review"}.forEach {r->output+=if(r.deletedAt!=null)r else r.duplicate().apply {deletedAt=timestamp;updatedAt=timestamp;changeId=uuid();dirty=true}}
        return output
    }
}
