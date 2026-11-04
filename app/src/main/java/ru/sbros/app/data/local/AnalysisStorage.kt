package ru.sbros.app.data.local

import org.json.JSONObject
import ru.sbros.app.data.ai.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private fun reviewId(captureId: String)=UUID.nameUUIDFromBytes("sbros-review:$captureId".toByteArray(Charsets.UTF_8)).toString()

suspend fun RoomDataRepository.analysisReview(scope: String,captureId: String): AnalysisReview?=transaction {
    dao.records(scope).find {it.id==reviewId(captureId)&&it.deletedAt==null}?.let {decodeReview(it.payload)}
}

/** Review drafts stay on this device; only confirmed records belong to the sync whitelist. */
suspend fun RoomDataRepository.saveAnalysisReview(scope: String,captureId: String,review: AnalysisReview)=transaction {
    val targetScope=scope;val originId=captureId
    val rows=dao.records(scope)
    val original=rows.find {it.id==captureId&&it.kind=="capture"&&it.deletedAt==null}?:throw StaleEditor()
    if(JSONObject(original.payload).optString("status")!="inbox")throw StaleEditor()
    val payload=reviewJson(review)
    if(payload.toByteArray(Charsets.UTF_8).size>240_000)throw RecordTooLarge()
    val previous=rows.find {it.id==reviewId(captureId)}
    val row=previous?.duplicate()?:LocalRecord().apply {
        this.scope=targetScope;id=reviewId(originId);kind="local_ai_review";this.captureId=originId
        localId=(rows.filter {it.kind==kind}.maxOfOrNull {it.localId}?:0)+1;createdAt=now()
    }
    row.payload=payload;row.updatedAt=now();row.deletedAt=null;row.changeId=uuid();row.dirty=false
    dao.putRecords(listOf(row))
}

/** The source status and every accepted result commit in one Room transaction, once. */
suspend fun RoomDataRepository.confirmAnalysis(scope: String,captureId: String,review: AnalysisReview): Unit=transaction {
    val targetScope=scope;val originId=captureId
    val rows=dao.records(scope)
    val original=rows.find {it.id==captureId&&it.kind=="capture"&&it.deletedAt==null}?:throw StaleEditor()
    val source=JSONObject(original.payload)
    val commitId=UUID.nameUUIDFromBytes(reviewJson(review).toByteArray(Charsets.UTF_8)).toString()
    if(source.optString("status")=="committed") {
        if(source.optString("analysisCommit")==commitId)return@transaction
        throw AiFailure("Этот сброс уже разобран. Открой историю: повторные записи не созданы.")
    }
    require(source.optString("status")=="inbox")
    validateProposals(review.proposals,source.getString("text"))?.let {throw AiFailure(it)}
    val accepted=review.proposals.filterNot {it.removed}
    val timestamp=now()
    val high=rows.groupBy {it.kind}.mapValues {(_,v)->v.maxOf {it.localId}}.toMutableMap()
    val output=mutableListOf<LocalRecord>()
    val projects=accepted.filter {it.kind=="projects"}.associate {it.id to uuid()}
    fun add(recordKind: String,p: CaptureProposal,payload: JSONObject,stableId: String=uuid(),parent: String?=null) {
        payload.put("analysisModel",review.model).put("sourceExcerpt",p.excerpt).put("analysisCommit",commitId)
        val body=payload.toString()
        if(body.toByteArray(Charsets.UTF_8).size>240_000)throw RecordTooLarge()
        val local=(high[recordKind]?:0)+1;high[recordKind]=local
        output+=LocalRecord().apply {
            this.scope=targetScope;this.id=stableId;this.kind=recordKind;localId=local;this.payload=body
            this.captureId=originId;projectId=parent;createdAt=timestamp;updatedAt=timestamp;changeId=uuid();dirty=true
        }
    }
    for(p in accepted) {
        val title=p.title.trim();val body=p.body.trim().ifBlank {p.excerpt.ifBlank {title}}
        when(p.kind) {
            "projects"->add("project",p,JSONObject().put("title",title).put("area",p.area.trim().ifBlank {"Личное"}).put("outcome",body),projects.getValue(p.id))
            "next","waiting","someday"->add("action",p,JSONObject().put("title",title).put("area",p.area.trim().ifBlank {"Личное"}).put("energy",p.energy)
                .put("hint",p.body.ifBlank {p.excerpt.takeIf {it.isNotBlank()}?.let {"Из сброса: «$it»"}?:"Из сброса"}).put("status",p.kind).put("dueDate",p.dueDate.takeIf {it.isNotBlank()}?:JSONObject.NULL).put("completedAt",JSONObject.NULL),parent=p.projectRef?.let {projects.getValue(it)})
            "reflection","idea"->add("thought",p,JSONObject().put("title",title).put("body",body).put("idea",p.kind=="idea").put("favorite",false)
                .put("time",LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))).put("sourceText",source.getString("text")))
            "cal"->add("event",p,JSONObject().put("title",title).put("body",p.body).put("date",p.date).put("time",p.time))
            "notes"->add("reference",p,JSONObject().put("title",title).put("body",body))
            "inbox"->add("capture",p,JSONObject().put("text",p.body.ifBlank {title}).put("status","inbox").put("confirmedAt",JSONObject.NULL).put("source","analysis"))
        }
    }
    output+=original.duplicate().apply {
        payload=source.put("status","committed").put("confirmedAt",timestamp).put("analysisModel",review.model).put("analysisCommit",commitId).toString()
        updatedAt=timestamp;changeId=uuid();dirty=true
    }
    rows.find {it.id==reviewId(captureId)}?.let {output+=it.duplicate().apply {deletedAt=timestamp;updatedAt=timestamp;changeId=uuid();dirty=false}}
    dao.putRecords(output)
}
