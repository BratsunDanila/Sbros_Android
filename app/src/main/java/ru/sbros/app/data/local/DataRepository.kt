package ru.sbros.app.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import ru.sbros.app.data.*
import java.util.concurrent.Callable
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class WorkspaceSnapshot(val scope: String,val data: WorkspaceState=emptyState(),val draft: String="",val loaded: Boolean=false)
data class UndoEntry(val before: LocalRecord?,val afterId: String,val afterChangeId: String)
data class MutationReceipt(val scope: String,val entries: List<UndoEntry>)
data class ChangeResult(val state: WorkspaceState,val receipt: MutationReceipt)
class StaleEditor: Exception("Record changed while the editor was open")
class RecordTooLarge: Exception("Record exceeds the shared local/cloud size limit")

interface DataRepository {
    fun observe(scope: String): Flow<WorkspaceSnapshot>
    suspend fun read(scope: String): WorkspaceSnapshot
    suspend fun change(scope: String,transform: (WorkspaceState)->WorkspaceState): ChangeResult
    suspend fun draft(scope: String,text: String)
    suspend fun saveCapture(scope: String,text: String): ChangeResult
    suspend fun moveToThought(scope: String,id: String): ChangeResult
    suspend fun undo(receipt: MutationReceipt)
}

class RoomDataRepository(val db: SbrosDatabase): DataRepository {
    val dao=db.localDao()
    suspend fun <T> transaction(block: ()->T): T=withContext(Dispatchers.IO){db.runInTransaction(Callable {block()})}
    private fun ensure(scope: String): WorkspaceRow {
        require(scope=="local"||scope=="demo"||scope=="legacy"||scope.startsWith("user:"))
        return dao.workspace(scope)?:WorkspaceRow().apply {this.scope=scope}.also {workspace->
            dao.putWorkspace(workspace)
            if(scope=="demo") {
                val demo=withCaptures(fixtures());dao.putRecords(RecordCodec.encode(scope,demo,emptyList()));workspace.uiState=RecordCodec.ui(demo);dao.putWorkspace(workspace)
            }
        }
    }
    private fun withCaptures(state: WorkspaceState)=if(state.captures.isEmpty()&&state.inbox.isNotEmpty())state.copy(captures=state.inbox.map {StoredCapture(uuid(),it,now())})else state
    override suspend fun read(scope: String)=transaction {val w=ensure(scope);WorkspaceSnapshot(scope,RecordCodec.decode(dao.records(scope),w.uiState),w.draft,true)}
    override fun observe(scope: String): Flow<WorkspaceSnapshot> = flow {
        emit(read(scope))
        val records=dao.observe(scope).distinctUntilChanged {old,next->
            old.size==next.size&&old.zip(next).all {(a,b)->a.id==b.id&&a.changeId==b.changeId}
        }
        val ui=dao.observeWorkspace(scope).map {it?.uiState?:"{}"}.distinctUntilChanged()
        // Draft-only writes no longer decode every record in the workspace.
        emitAll(combine(records,ui) {rows,state->WorkspaceSnapshot(scope,RecordCodec.decode(rows,state),loaded=true)})
    }.flowOn(Dispatchers.IO)
    private fun write(scope: String,workspace: WorkspaceRow,before: List<LocalRecord>,state: WorkspaceState): ChangeResult {
        val rows=RecordCodec.encode(scope,state,before)
        val old=before.associateBy {it.id}
        val changed=rows.filter {it.changeId!=old[it.id]?.changeId}
        if(changed.isNotEmpty())dao.putRecords(changed)
        workspace.uiState=RecordCodec.ui(state);dao.putWorkspace(workspace)
        return ChangeResult(RecordCodec.decode(rows,workspace.uiState),MutationReceipt(scope,changed.map {UndoEntry(old[it.id]?.duplicate(),it.id,it.changeId)}))
    }
    override suspend fun change(scope: String,transform: (WorkspaceState)->WorkspaceState)=transaction {
        val w=ensure(scope);val old=dao.records(scope);write(scope,w,old,transform(RecordCodec.decode(old,w.uiState)))
    }
    suspend fun checkedChange(scope: String,kind: String,id: Int,revision: String,transform: (WorkspaceState)->WorkspaceState)=transaction {
        val w=ensure(scope);val old=dao.records(scope)
        val record=old.find{it.kind==kind&&it.localId==id}
        if(record==null||record.deletedAt!=null||record.changeId!=revision)throw StaleEditor()
        write(scope,w,old,transform(RecordCodec.decode(old,w.uiState)))
    }
    override suspend fun draft(scope: String,text: String)=transaction {val w=ensure(scope);w.draft=text;dao.putWorkspace(w)}
    override suspend fun saveCapture(scope: String,text: String)=transaction {
        require(text.isNotBlank()&&text.length<=CAPTURE_TEXT_LIMIT)
        val w=ensure(scope);val old=dao.records(scope);val state=RecordCodec.decode(old,w.uiState)
        // Original and draft removal commit atomically, before success is shown.
        w.draft=""
        write(scope,w,old,state.copy(captures=listOf(StoredCapture(uuid(),text,now()))+state.captures))
    }
    suspend fun captureForAnalysis(scope: String,text: String,sourceId: String?): StoredCapture=transaction {
        require(text.isNotBlank()&&text.length<=CAPTURE_TEXT_LIMIT)
        val w=ensure(scope);val old=dao.records(scope);val state=RecordCodec.decode(old,w.uiState)
        val source=state.captures.find {it.id==sourceId&&it.status=="inbox"}
        val capture=source?.takeIf {it.text==text}?:StoredCapture(uuid(),text,now(),parentId=source?.id)
        w.draft=""
        if(capture===source)dao.putWorkspace(w)
        else write(scope,w,old,state.copy(captures=listOf(capture)+state.captures))
        capture
    }
    override suspend fun moveToThought(scope: String,id: String)=change(scope) {state->
        val capture=state.captures.find {it.id==id&&it.status=="inbox"}?:return@change state
        val thought=Thought(state.nextLocalId("thought"),capture.text.trim().lineSequence().first().take(60),capture.text,false,0,LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),sourceText=capture.text,captureId=capture.id)
        state.copy(thoughts=listOf(thought)+state.thoughts,captures=state.captures.map {if(it.id==id)it.copy(status="committed",confirmedAt=now())else it})
    }
    override suspend fun undo(receipt: MutationReceipt)=transaction {
        val current=dao.records(receipt.scope).associateBy {it.id}
        val restore=receipt.entries.mapNotNull {entry->
            val row=current[entry.afterId]?:return@mapNotNull null
            if(row.changeId!=entry.afterChangeId)return@mapNotNull null
            (entry.before?.duplicate()?:row.duplicate().apply {deletedAt=now()}).apply {serverVersion=row.serverVersion;updatedAt=now();changeId=uuid();dirty=true}
        }
        if(restore.isNotEmpty())dao.putRecords(restore)
    }
    suspend fun importLegacy(raw: String,draft: String)=transaction {
        if(dao.workspace("legacy")!=null)return@transaction
        val state=withCaptures(decode(raw));val w=ensure("legacy");w.draft=draft;write("legacy",w,emptyList(),state)
    }
    suspend fun hasWorkspace(scope: String)=transaction {dao.workspace(scope)!=null}
    suspend fun discardWeeklyReviews()=transaction {dao.clearWeeklyReviewConflicts();dao.clearWeeklyReviews()}
    suspend fun countLocalRecords()=transaction {dao.records("local").count {it.deletedAt==null&&!it.kind.startsWith("local_")&&it.kind!="review"}}
    suspend fun enableSync(userId: String)=transaction {val w=ensure("user:$userId");w.syncEnabled=true;dao.putWorkspace(w)}
    suspend fun resetDemo()=transaction {dao.clearDemo();dao.clearDemoWorkspace();ensure("demo");Unit}
    suspend fun importLocal(userId: String)=transaction {
        val scope="user:$userId";val w=ensure(scope);val existing=dao.records(scope).associateBy {it.id}.toMutableMap()
        val high=existing.values.groupBy {it.kind}.mapValues {(_,v)->v.maxOf {it.localId}}.toMutableMap()
        val sources=dao.records("local").filter{!it.kind.startsWith("local_")&&it.kind!="review"}
        val byId=sources.associateBy {it.id}
        val required=sources.filter {it.deletedAt==null}.map {it.id}.toMutableSet()
        val pending=java.util.ArrayDeque(required)
        // Keep referenced tombstones too: a surviving result can still reference a deleted source.
        while(pending.isNotEmpty()) {
            val row=byId[pending.removeFirst()]?:continue
            listOfNotNull(row.captureId,row.projectId).forEach {id->if(id in byId&&required.add(id))pending.addLast(id)}
        }
        sources.filter{it.id in required}.forEach {source->
            val current=existing[source.id]
            if(current==null){
                val copy=source.duplicate().apply {this.scope=scope;localId=(high[kind]?:0)+1;serverVersion=0;dirty=true}
                high[copy.kind]=copy.localId;dao.putRecords(listOf(copy));existing[copy.id]=copy
            } else if(current!=null&&(current.payload!=source.payload||current.deletedAt!=source.deletedAt||current.projectId!=source.projectId||current.captureId!=source.captureId)) {
                // Re-importing a changed guest copy never overwrites an account record.
                dao.putConflict(SyncConflict().apply {this.scope=scope;recordId=source.id;localJson=recordJson(source).toString();remoteJson=recordJson(current).put("pending_local_copy",true).toString();createdAt=now()})
            }
        }
        w.syncEnabled=true;dao.putWorkspace(w)
    }
}

/** Whitelist: neither UI state, draft, tokens nor provider settings can enter a sync payload. */
fun recordJson(r: LocalRecord)=org.json.JSONObject().put("id",r.id).put("kind",r.kind).put("payload",org.json.JSONObject(r.payload))
    .put("project_id",r.projectId?:org.json.JSONObject.NULL).put("capture_id",r.captureId?:org.json.JSONObject.NULL)
    .put("created_at",r.createdAt).put("updated_at",r.updatedAt).put("deleted_at",r.deletedAt?:org.json.JSONObject.NULL)
    .put("version",r.serverVersion).put("change_id",r.changeId)
