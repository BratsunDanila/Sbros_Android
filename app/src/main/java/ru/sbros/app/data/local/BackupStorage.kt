package ru.sbros.app.data.local

import org.json.JSONArray
import org.json.JSONObject
import ru.sbros.app.data.ai.decodeReview
import ru.sbros.app.data.sync.syncKinds
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

const val BACKUP_MAX_BYTES=16*1024*1024
private val backupKinds=syncKinds+setOf("local_ai_review","local_conflict_archive")

suspend fun RoomDataRepository.exportBackup(scope: String): ByteArray=transaction {
    val workspace=dao.workspace(scope)?:error("Workspace unavailable")
    val rows=dao.records(scope).filter {it.kind in backupKinds}
    val bytes=JSONObject().put("format","sbros-backup").put("version",1).put("exportedAt",now())
        .put("records",JSONArray(rows.map(::recordJson))).put("draft",workspace.draft)
        .toString(2).toByteArray(Charsets.UTF_8)
    require(bytes.size<=BACKUP_MAX_BYTES){"Backup too large"}
    bytes
}

/** Restore copies into guest storage. Existing records and account data are never overwritten. */
suspend fun RoomDataRepository.importBackup(bytes: ByteArray): Int=transaction {
    require(bytes.size in 1..BACKUP_MAX_BYTES)
    val root=JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.getString("format")=="sbros-backup"&&root.getInt("version")==1)
    val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    val old=dao.records("local")
    if(old.any {it.kind=="local_backup_receipt"&&JSONObject(it.payload).optString("digest")==digest})return@transaction 0
    val list=root.getJSONArray("records");require(list.length()<=50_000)
    val ids=mutableSetOf<String>()
    val input=(0 until list.length()).map {i->
        val p=list.getJSONObject(i);val id=UUID.fromString(p.getString("id")).toString()
        require(ids.add(id));val kind=p.getString("kind");require(kind in backupKinds)
        val payload=p.getJSONObject("payload").toString()
        require(payload.toByteArray(Charsets.UTF_8).size<=if(kind.startsWith("local_"))BACKUP_MAX_BYTES else 240_000)
        if(kind=="local_ai_review")decodeReview(payload)
        if(kind=="local_conflict_archive") {
            val archive=JSONObject(payload)
            archive.getJSONObject("local").getJSONObject("payload")
            archive.getJSONObject("cloud").getJSONObject("payload")
            archive.optJSONArray("analysis_results")?.let {children->
                for(n in 0 until children.length())children.getJSONObject(n).getJSONObject("payload")
            }
        }
        LocalRecord().apply {
            this.id=id;this.kind=kind;this.payload=payload
            captureId=p.optional("capture_id")?.let {UUID.fromString(it).toString()}
            projectId=p.optional("project_id")?.let {UUID.fromString(it).toString()}
            createdAt=Instant.parse(p.getString("created_at")).toString()
            updatedAt=Instant.parse(p.getString("updated_at")).toString()
            deletedAt=p.optional("deleted_at")?.let {Instant.parse(it).toString()}
        }
    }
    val byId=input.associateBy {it.id}
    input.forEach {row->
        require(row.captureId==null||byId[row.captureId]?.kind=="capture")
        require(row.projectId==null||(row.kind=="action"&&byId[row.projectId]?.kind=="project"))
        val ancestors=mutableSetOf(row.id)
        var parent=row.captureId
        while(parent!=null){require(ancestors.add(parent));parent=byId[parent]?.captureId}
    }
    val mapping=input.associate {it.id to uuid()}.toMutableMap()
    input.filter {it.kind=="local_ai_review"}.forEach {review->
        val source=mapping[review.captureId]?:error("Missing review source")
        mapping[review.id]=UUID.nameUUIDFromBytes("sbros-review:$source".toByteArray(Charsets.UTF_8)).toString()
    }
    val high=old.groupBy {it.kind}.mapValues {(_,rows)->rows.maxOf {it.localId}}.toMutableMap()
    val restored=input.map {row->row.duplicate().apply {
        scope="local";id=mapping.getValue(row.id);captureId=row.captureId?.let(mapping::getValue)
        projectId=row.projectId?.let(mapping::getValue);localId=(high[kind]?:0)+1;high[kind]=localId
        serverVersion=0;changeId=uuid();dirty=kind in syncKinds
    }}
    // Decode before writing anything; an invalid backup rolls back completely.
    RecordCodec.decode(old+restored,"{}")
    val workspace=dao.workspace("local")?:WorkspaceRow().apply {scope="local"}
    val draft=root.optString("draft");require(draft.toByteArray(Charsets.UTF_8).size<=BACKUP_MAX_BYTES)
    val merged=listOf(workspace.draft,draft).filter {it.isNotBlank()}.joinToString("\n\n")
    require(merged.toByteArray(Charsets.UTF_8).size<=BACKUP_MAX_BYTES)
    workspace.draft=merged
    val receipt=LocalRecord().apply {
        scope="local";id=uuid();kind="local_backup_receipt";localId=(high[kind]?:0)+1
        payload=JSONObject().put("digest",digest).toString();createdAt=now();updatedAt=createdAt;changeId=uuid();dirty=false
    }
    dao.putRecords(restored+receipt);dao.putWorkspace(workspace)
    restored.count {it.kind in syncKinds&&it.deletedAt==null}
}
