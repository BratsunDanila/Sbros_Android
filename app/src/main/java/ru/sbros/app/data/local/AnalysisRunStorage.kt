package ru.sbros.app.data.local

import org.json.JSONObject

/** Terminal attempt metadata only: no API key, prompt, original text or raw provider response. */
suspend fun RoomDataRepository.recordAnalysisRun(targetScope: String,runId: String,originId: String,modelName: String,
    status: String,startedAt: String,finishedAt: String,proposalCount: Int,errorCode: String?)=transaction {
    require(status in setOf("succeeded","failed","cancelled")&&proposalCount in 0..40)
    val rows=dao.records(targetScope)
    // The demo can be reset during a request; never create an orphan audit entry.
    if(rows.none {it.id==originId&&it.kind=="capture"}||rows.any {it.id==runId})return@transaction
    val body=JSONObject().put("model",modelName).put("status",status).put("finishedAt",finishedAt)
        .put("proposalCount",proposalCount).put("errorCode",errorCode?:JSONObject.NULL)
    dao.putRecords(listOf(LocalRecord().also {row->
        row.scope=targetScope;row.id=runId;row.kind="analysis_run"
        row.localId=(rows.filter {it.kind=="analysis_run"}.maxOfOrNull {it.localId}?:0)+1
        row.payload=body.toString();row.captureId=originId;row.createdAt=startedAt;row.updatedAt=finishedAt
        row.changeId=uuid();row.dirty=true
    }))
}
