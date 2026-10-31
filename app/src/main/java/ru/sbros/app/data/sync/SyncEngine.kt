package ru.sbros.app.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.*
import ru.sbros.app.data.auth.*
import ru.sbros.app.data.local.*
import java.time.Instant
import java.util.UUID

val syncKinds=setOf("capture","project","action","thought","event","reference","analysis_run")
data class SyncState(val userId: String?=null,val busy: Boolean=false,val message: String="Записи на устройстве",val lastSyncedAt: String?=null,val failed: Boolean=false)
enum class SyncOutcome { SUCCESS, RETRY, NEEDS_LOGIN }

class SyncEngine(private val data: RoomDataRepository,private val auth: AuthRepository) {
    val state=MutableStateFlow(SyncState())
    private val mutex=Mutex()
    private fun current(userId: String)=auth.state.value.user?.id==userId&&!auth.state.value.needsLogin
    suspend fun run(userId: String): SyncOutcome {
        if(!mutex.tryLock())return SyncOutcome.SUCCESS
        val scope="user:$userId"
        try {
            if(!current(userId))return SyncOutcome.NEEDS_LOGIN
            if(!data.transaction {data.dao.workspace(scope)?.syncEnabled==true})return SyncOutcome.SUCCESS
            state.value=SyncState(userId,true,"Сохраняем изменения…",state.value.takeIf{it.userId==userId}?.lastSyncedAt)
            val blocked=data.transaction {data.dao.conflicts(scope).map{it.recordId}.toMutableSet()}
            // Dependencies are sent before referring records. A second pass handles edits made in flight.
            repeat(5) {
                val pending=dependencyOrder(data.transaction {data.dao.records(scope).filter {it.dirty&&it.kind in syncKinds&&it.id !in blocked}})
                for(batch in uploadBatches(pending)) {
                    if(!current(userId))return SyncOutcome.NEEDS_LOGIN
                    val response=auth.request(userId,"/rest/v1/rpc/sync_push_v3",JSONObject().put("p_changes",JSONArray(batch.map {recordJson(it)})).toString())
                    checkResponse(response.status)
                    val results=JSONArray(response.body);require(results.length()==batch.size)
                    val sent=batch.associateBy {it.id};val received=mutableSetOf<String>()
                    data.transaction {
                        val existing=data.dao.records(scope).associateBy{it.id}
                        for(i in 0 until results.length()) {
                            val result=results.getJSONObject(i);val remote=result.getJSONObject("record");val id=remote.getString("id")
                            require(received.add(id));val original=sent[id]?:error("Unexpected sync record");validate(remote)
                            val local=existing[id]?:continue
                            if(result.getString("status")=="conflict") {
                                blocked+=id;saveConflict(scope,local,remote)
                            } else {
                                require(result.getString("status")=="ok"&&remote.getString("change_id")==original.changeId)
                                val ack=local.duplicate().apply {serverVersion=remote.getLong("version");dirty=local.changeId!=original.changeId}
                                data.dao.putRecords(listOf(ack))
                            }
                        }
                    }
                }
            }
            var cursor: String?=null
            do {
                if(!current(userId))return SyncOutcome.NEEDS_LOGIN
                val response=auth.request(userId,"/rest/v1/rpc/sync_pull_v3",JSONObject().put("p_after",cursor?:JSONObject.NULL).put("p_limit",20).toString())
                checkResponse(response.status);val rows=JSONArray(response.body)
                data.transaction {
                    val local=data.dao.records(scope).associateBy {it.id}.toMutableMap()
                    val high=local.values.groupBy{it.kind}.mapValues{(_,v)->v.maxOf{it.localId}}.toMutableMap()
                    for(i in 0 until rows.length()) {
                        val remote=rows.getJSONObject(i)
                        // Older v3 servers can still return the retired weekly-review kind.
                        if(remote.optString("kind")=="review")continue
                        validate(remote);val id=remote.getString("id");val old=local[id]
                        if(old!=null&&remote.getLong("version")<=old.serverVersion)continue
                        if(old!=null&&old.dirty){saveConflict(scope,old,remote);blocked+=id;continue}
                        val kind=remote.getString("kind");val row=fromRemote(scope,remote,old?.localId?:((high[kind]?:0)+1))
                        high[kind]=maxOf(high[kind]?:0,row.localId);local[id]=row;data.dao.putRecords(listOf(row))
                    }
                    // Reject any malformed projection within this transaction; keep the previous database.
                    RecordCodec.decode(local.values.toList(),data.dao.workspace(scope)?.uiState?:"{}")
                }
                if(rows.length()==0)break
                val next=rows.getJSONObject(rows.length()-1).getString("id")
                require(cursor==null||next>cursor!!);cursor=next
            } while(rows.length()==20)
            if(!current(userId))return SyncOutcome.NEEDS_LOGIN
            val conflicts=data.transaction {data.dao.conflicts(scope).size}
            val pending=data.transaction {data.dao.records(scope).count {it.dirty&&it.kind in syncKinds&&it.id !in blocked}}
            state.value=SyncState(userId,false,if(conflicts>0)"Есть разные версии записей" else if(pending>0)"Есть изменения на устройстве" else "Всё сохранено",now())
            return if(pending>0)SyncOutcome.RETRY else SyncOutcome.SUCCESS
        } catch(e: Exception){if(e is CancellationException)throw e
            if(auth.state.value.user?.id==userId)state.value=SyncState(userId,false,(e as? AuthFailure)?.publicMessage?:"Не удалось синхронизировать. Записи остаются на устройстве.",state.value.lastSyncedAt,failed=true)
            return if(auth.state.value.needsLogin)SyncOutcome.NEEDS_LOGIN else SyncOutcome.RETRY
        } finally {mutex.unlock()}
    }
    private fun checkResponse(status: Int){if(status !in 200..299)throw AuthFailure(when(status){404->"Нужна новая схема облачной базы: выполни 003_relational_domain.sql в Supabase SQL Editor.";429->"Облако просит подождать. Повторим позже.";else->"Облако пока недоступно. Записи сохранены на устройстве."})}
    private fun dependencyOrder(records: List<LocalRecord>): List<LocalRecord> {
        val waiting=records.sortedWith(compareBy<LocalRecord>{when(it.kind){"capture"->0;"project"->1;else->2}}.thenBy {it.createdAt}).associateBy {it.id}.toMutableMap()
        val ordered=mutableListOf<LocalRecord>()
        while(waiting.isNotEmpty()) {
            val ready=waiting.values.filter {row->listOfNotNull(row.captureId,row.projectId).none {it in waiting}}
            if(ready.isEmpty())throw AuthFailure("Обнаружена циклическая связь записей. Данные остаются на устройстве; синхронизация остановлена.")
            ordered+=ready;ready.forEach {waiting.remove(it.id)}
        }
        return ordered
    }
    private fun uploadBatches(records: List<LocalRecord>): List<List<LocalRecord>> {
        val batches=mutableListOf<List<LocalRecord>>();var batch=mutableListOf<LocalRecord>();var size=0
        records.forEach {record->
            val bytes=recordJson(record).toString().toByteArray(Charsets.UTF_8).size+1
            if(batch.isNotEmpty()&&(batch.size>=30||size+bytes>900_000)){batches+=batch;batch=mutableListOf();size=0}
            batch+=record;size+=bytes
        }
        if(batch.isNotEmpty())batches+=batch
        return batches
    }
    private fun saveConflict(scope: String,local: LocalRecord,remote: JSONObject) {
        data.dao.putConflict(SyncConflict().apply {this.scope=scope;recordId=local.id;localJson=recordJson(local).toString();remoteJson=remote.toString();createdAt=now()})
    }
    suspend fun resolve(userId: String,id: String,keepLocal: Boolean) {
        check(auth.state.value.user?.id==userId)
        data.transaction {
            val scope="user:$userId";val conflict=data.dao.conflicts(scope).find {it.recordId==id}?:return@transaction
            val records=data.dao.records(scope);val local=records.find{it.id==id}?:return@transaction
            val remote=JSONObject(conflict.remoteJson);validate(remote,remote.optBoolean("pending_local_copy"))
            val archive=LocalRecord().apply {this.scope=scope;this.id=uuid();kind="local_conflict_archive";localId=(records.filter{it.kind==kind}.maxOfOrNull{it.localId}?:0)+1;payload=JSONObject().put("local",recordJson(local)).put("cloud",remote).toString();createdAt=now();updatedAt=createdAt;changeId=uuid();dirty=false}
            val resolved=if(keepLocal){
                if(remote.optBoolean("pending_local_copy")) {
                    val guest=JSONObject(conflict.localJson)
                    fromRemote(scope,guest.put("version",remote.getLong("version")),local.localId).apply{changeId=uuid();updatedAt=now();dirty=true}
                } else local.duplicate().apply{serverVersion=remote.getLong("version");changeId=uuid();updatedAt=now();dirty=true}
            }else fromRemote(scope,remote,local.localId).apply {if(remote.optBoolean("pending_local_copy")){dirty=true;changeId=uuid()}}
            data.dao.putRecords(listOf(archive,resolved));data.dao.clearConflict(scope,id)
        }
    }
    private fun validate(p: JSONObject,allowZero: Boolean=false) {
        UUID.fromString(p.getString("id"));UUID.fromString(p.getString("change_id"));require(p.getString("kind") in syncKinds&&p.getLong("version")>=if(allowZero)0 else 1)
        Instant.parse(p.getString("created_at"));Instant.parse(p.getString("updated_at"));p.optional("deleted_at")?.let {Instant.parse(it)}
        p.optional("project_id")?.let {UUID.fromString(it)};p.optional("capture_id")?.let {UUID.fromString(it)};p.getJSONObject("payload")
    }
    private fun fromRemote(scope: String,p: JSONObject,id: Int)=LocalRecord().apply {
        this.scope=scope;this.id=p.getString("id");kind=p.getString("kind");localId=id;payload=p.getJSONObject("payload").toString();projectId=p.optional("project_id");captureId=p.optional("capture_id")
        createdAt=Instant.parse(p.getString("created_at")).toString();updatedAt=Instant.parse(p.getString("updated_at")).toString();deletedAt=p.optional("deleted_at");serverVersion=p.getLong("version");changeId=p.getString("change_id");dirty=false
    }
}
