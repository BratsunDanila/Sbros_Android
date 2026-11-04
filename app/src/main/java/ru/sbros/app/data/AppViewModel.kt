package ru.sbros.app.data

import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import ru.sbros.app.data.local.*
import ru.sbros.app.data.ai.*

/** Presentation models retain the v15 API; all user records come from Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class SbrosViewModel(saved: SavedStateHandle,val settings: SettingsRepository,val repository: RoomDataRepository,val activeScope: StateFlow<String>): ViewModel() {
    private val savedState=saved
    val analysis=MutableStateFlow(CaptureAnalysisState())
    private var analysisJob: Job?=null
    val appearance=settings.appearance.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    val error=MutableStateFlow<String?>(null)
    val loadError=MutableStateFlow(false)
    private val reload=MutableStateFlow(0)
    private val operations=Channel<suspend ()->Unit>(Channel.UNLIMITED)
    private data class DraftWrite(val scope: String,var text: String)
    private val pendingDraftWrites=mutableMapOf<String,DraftWrite>()
    private var undoReceipt: MutationReceipt?=null
    private var draftRevision=0L
    val draft=MutableStateFlow("")
    private var loadedScope=""
    val workspace=combine(activeScope,reload){scope,_->scope}.flatMapLatest {scope->repository.observe(scope)
        .onStart {loadedScope="";draft.value="";emit(WorkspaceSnapshot(scope))}
        .onEach {w->if(w.loaded&&loadedScope!=w.scope){loadedScope=w.scope;draft.value=w.draft};loadError.value=false}
        .catch {loadError.value=true;error.value="Не удалось открыть записи. Данные не удалены. Повтори попытку.";emit(WorkspaceSnapshot(scope))}
    }.stateIn(viewModelScope,SharingStarted.Eagerly,WorkspaceSnapshot(activeScope.value))
    init {
        viewModelScope.launch {for(operation in operations)operation()}
        viewModelScope.launch {activeScope.collect {scope->if(analysis.value.scope.isNotBlank()&&analysis.value.scope!=scope){analysisJob?.cancel();analysis.value=CaptureAnalysisState()}}}
        saved.get<String>("demo")?.let {legacy->viewModelScope.launch {runCatching {write {repository.importLegacy(legacy,saved.get<String>("draft")?:"")}}.onFailure {error.value="Не удалось перенести данные прежней версии. Исходное состояние сохранено."}}}
    }
    private suspend fun <T> write(block: suspend ()->T): T {
        // A save/confirmation is an ordering barrier: later typing gets a new write.
        pendingDraftWrites.clear()
        val result=CompletableDeferred<T>()
        operations.send {try {result.complete(block())}catch(e: Exception){result.completeExceptionally(e)}}
        return result.await()
    }
    private suspend fun change(expected: Triple<String,Int,String>?=null,transform: (WorkspaceState)->WorkspaceState): Boolean {
        val scope=workspace.value.scope
        return try {write {val result=if(expected==null)repository.change(scope,transform)else repository.checkedChange(scope,expected.first,expected.second,expected.third,transform);if(result.receipt.entries.isNotEmpty())undoReceipt=result.receipt};true}
        catch(e: Exception){if(e is CancellationException)throw e;error.value=when(e){is StaleEditor->"Запись изменилась, пока форма была открыта. Твоя правка остаётся в форме. Скопируй её перед закрытием и открой актуальную запись: прежняя версия не перезаписана.";is RecordTooLarge->"Запись слишком большая (до 240 КБ вместе с оформлением). Текст остаётся в форме: раздели его на несколько записей.";else->"Не удалось сохранить запись. Данные не удалены. Повтори попытку."};false}
    }
    fun retry(){loadError.value=false;reload.value++}
    fun clearError(){error.value=null}
    suspend fun flushDraft(){val scope=workspace.value.scope;val text=draft.value;write {repository.draft(scope,text)}}
    fun draft(text: String){val value=text;draft.value=value;draftRevision++;val scope=workspace.value.scope
        val pending=pendingDraftWrites[scope]
        if(pending!=null){pending.text=value;return}
        val update=DraftWrite(scope,value);pendingDraftWrites[scope]=update
        operations.trySend {
            if(pendingDraftWrites[scope]===update)pendingDraftWrites.remove(scope)
            try{repository.draft(scope,update.text)}catch(e: Exception){error.value="Черновик пока не сохранён. Не закрывай приложение; повтори ввод после освобождения памяти."}
        }
    }
    fun appearance(value: Appearance){viewModelScope.launch {try{settings.update(value)}catch(e: Exception){error.value="Не удалось сохранить настройки"}}}
    fun mutate(transform: (WorkspaceState)->WorkspaceState){viewModelScope.launch {change(transform=transform)}}
    suspend fun mutateAwait(expected: Triple<String,Int,String>?=null,transform: (WorkspaceState)->WorkspaceState)=change(expected,transform)
    fun selectTask(id: Int){mutate {it.focusTask(id)}}
    fun skipTask(){mutate {it.nextTask()}}
    suspend fun complete(id: Int): Long? {var token: Long?=null
        val ok=change {s->if(s.tasks.none {it.id==id&&it.status in listOf("next","waiting","someday")})s else {val next=(s.taskUndo.maxOfOrNull{it.token}?:0)+1;token=next;s.completeTask(id,next)}}
        return if(ok)token else null
    }
    suspend fun undoCompletion(token: Long)=change {it.undoTaskCompletion(token)}
    suspend fun undo(){val receipt=undoReceipt?:return;if(receipt.scope!=workspace.value.scope)return
        try{write {repository.undo(receipt)};undoReceipt=null}catch(e: Exception){error.value="Не удалось отменить изменение. Повтори попытку."}}
    fun favorite(id: Int)=mutate {s->s.copy(thoughts=s.thoughts.map {if(it.id==id)it.copy(favorite=!it.favorite)else it})}
    suspend fun later(): Boolean {val text=draft.value;if(text.isBlank())return false
        if(text.length>CAPTURE_TEXT_LIMIT){error.value="Сократи запись до 5 000 символов. Весь текст сохранён в черновике.";return false}
        val revision=draftRevision;val scope=workspace.value.scope
        val source=if(savedState.get<String>("captureSourceScope")==scope)savedState.get<String>("captureSource") else null
        return try{write {
            if(source==null)undoReceipt=repository.saveCapture(scope,text).receipt else {repository.captureForAnalysis(scope,text,source);undoReceipt=null}
            if(revision==draftRevision&&scope==workspace.value.scope){draft.value="";savedState.remove<String>("captureSource")}
        };true}
        catch(e: Exception){if(e is CancellationException)throw e;error.value="Запись не сохранена. Текст остаётся в поле; повтори попытку.";false}}
    fun resumeCapture(capture: StoredCapture) {
        savedState["captureSource"]=capture.id;savedState["captureSourceScope"]=workspace.value.scope
        draft(capture.text)
    }
    suspend fun prepareAnalysis(): String? {
        val text=draft.value;if(text.isBlank())return null
        if(text.length>CAPTURE_TEXT_LIMIT){error.value="Сократи запись до 5 000 символов перед разбором. Весь текст сохранён в черновике.";return null}
        val target=workspace.value.scope;val revision=draftRevision
        val source=if(savedState.get<String>("captureSourceScope")==target)savedState.get<String>("captureSource") else null
        return try {
            val capture=write {repository.captureForAnalysis(target,text,source)}
            if(revision==draftRevision&&target==workspace.value.scope){draft.value="";savedState.remove<String>("captureSource")}
            capture.id
        } catch(e: Exception) {if(e is CancellationException)throw e;error.value="Не удалось сохранить исходник. Текст остаётся в поле; разбор не отправлен.";null}
    }
    fun openAnalysis(id: String,ai: AiRepository,autoStart: Boolean=false) {
        val target=workspace.value.scope
        if(analysis.value.captureId==id&&analysis.value.scope==target&&analysis.value.phase!="idle") {
            if(autoStart&&analysis.value.phase in listOf("ready","error"))requestAnalysis(ai)
            return
        }
        analysisJob?.cancel();analysis.value=CaptureAnalysisState(scope=target,captureId=id,phase="loading")
        analysisJob=viewModelScope.launch {
            try {
                val original=repository.read(target).data.captures.find {it.id==id}?:throw AiFailure("Исходная запись удалена или недоступна в этом пространстве.")
                val review=repository.analysisReview(target,id)
                analysis.value=CaptureAnalysisState(target,id,original.text,if(original.status=="committed")"saved" else if(review!=null)"review" else "ready",review?.proposals?:emptyList(),review?.model?:ANALYSIS_MODEL)
                if(autoStart&&analysis.value.phase=="ready"){analysis.value=analysis.value.copy(phase="processing");performAnalysis(ai)}
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {analysis.value=analysis.value.copy(phase="error",error=(e as? AiFailure)?.publicMessage?:"Не удалось открыть разбор. Исходник остаётся во входящих.")}
        }
    }
    fun requestAnalysis(ai: AiRepository,consent: Boolean=false) {
        if(analysis.value.busy||analysis.value.original.isBlank()||analysis.value.phase=="saved")return
        analysis.value=analysis.value.copy(phase="processing",error=null)
        analysisJob=viewModelScope.launch {
            try {if(consent)settings.allowAiText();performAnalysis(ai)}
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {analysis.value=analysis.value.copy(phase="error",error=(e as? AiFailure)?.publicMessage?:"Не удалось запустить разбор. Исходник сохранён.")}
        }
    }
    private suspend fun performAnalysis(ai: AiRepository) {
        if(!settings.aiTextConsent.first()){analysis.value=analysis.value.copy(phase="consent",error=null);return}
        val request=analysis.value
        analysis.value=request.copy(phase="processing",error=null)
        val runId=uuid();val startedAt=now()
        var outcome="failed";var failure: String?="provider_error";var count=0;var received=false
        try {
            val proposals=ai.analyze(request.original)
            received=true;count=proposals.size
            write {repository.saveAnalysisReview(request.scope,request.captureId,AnalysisReview(ANALYSIS_MODEL,proposals))}
            outcome="succeeded";failure=null
            analysis.value=request.copy(phase="review",proposals=proposals,model=ANALYSIS_MODEL,error=null)
        } catch(e: CancellationException) {outcome="cancelled";failure="cancelled";throw e}
        catch(e: Exception) {
            failure=if(received)"storage_error" else (e as? AiFailure)?.code?:"provider_error"
            analysis.value=request.copy(phase="error",error=(e as? AiFailure)?.publicMessage?:"Не удалось сохранить результат разбора. Исходник сохранён. Можно повторить или разобрать вручную.")
        } finally {
            // Logging survives navigation cancellation, without waiting on a cancelled UI actor.
            withContext(NonCancellable) {
                try {repository.recordAnalysisRun(request.scope,runId,request.captureId,ANALYSIS_MODEL,outcome,startedAt,now(),count,failure)}
                catch(e: Exception) {
                    if(analysis.value.scope==request.scope&&analysis.value.captureId==request.captureId&&analysis.value.phase=="review")
                        analysis.value=analysis.value.copy(error="Разбор готов, но журнал попытки пока не сохранён. Записи можно подтвердить.")
                }
            }
        }
    }
    private fun persistReview(value: CaptureAnalysisState) {
        operations.trySend {
            try {repository.saveAnalysisReview(value.scope,value.captureId,AnalysisReview(value.model,value.proposals))}
            catch(e: Exception) {if(analysis.value.scope==value.scope&&analysis.value.captureId==value.captureId&&analysis.value.phase=="review")analysis.value=analysis.value.copy(error="Правки пока не сохранены на устройстве. Не закрывай разбор; повтори подтверждение.")}
        }
    }
    fun editProposal(proposal: CaptureProposal) {
        val current=analysis.value;if(current.phase!="review")return
        val proposals=current.proposals.map {if(it.id==proposal.id)proposal else it}.map {
            if(it.projectRef==proposal.id&&(proposal.removed||proposal.kind!="projects"))it.copy(projectRef=null) else it
        }
        val next=current.copy(proposals=proposals,error=null);analysis.value=next;persistReview(next)
    }
    fun addProposal() {
        val current=analysis.value;if(current.phase!="review")return
        if(current.proposals.size>=40){analysis.value=current.copy(error="В одном разборе может быть до 40 записей.");return}
        val next=current.copy(proposals=current.proposals+CaptureProposal(),error=null);analysis.value=next;persistReview(next)
    }
    fun manualAnalysis() {
        val current=analysis.value;if(current.busy||current.original.isBlank()||current.phase=="saved")return
        val next=current.copy(phase="review",model="manual",error=null,proposals=listOf(CaptureProposal(title=current.original.lineSequence().firstOrNull {it.isNotBlank()}.orEmpty().take(80),body=current.original,excerpt=current.original)))
        analysis.value=next;persistReview(next)
    }
    fun confirmAnalysis() {
        val current=analysis.value;if(current.phase!="review")return
        validateProposals(current.proposals,current.original)?.let {analysis.value=current.copy(error=it);return}
        analysis.value=current.copy(phase="saving",error=null)
        analysisJob=viewModelScope.launch {
            try {write {repository.confirmAnalysis(current.scope,current.captureId,AnalysisReview(current.model,current.proposals))};analysis.value=current.copy(phase="saved",error=null)}
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {analysis.value=current.copy(phase="review",error=(e as? AiFailure)?.publicMessage?:"Не удалось подтвердить разбор. Проверь, что исходник не удалён. Правки остаются здесь; повтори попытку.")}
        }
    }
    fun cancelAnalysis() {
        if(analysis.value.phase in listOf("loading","processing")) {
            analysisJob?.cancel();analysis.value=analysis.value.copy(phase="ready",error="Разбор остановлен. Исходник сохранён во входящих.")
        }
    }
    fun resumeAnalysisOriginal() {
        cancelAnalysis();val current=analysis.value
        if(current.original.isNotBlank()&&current.phase!="saved")resumeCapture(StoredCapture(current.captureId,current.original,""))
    }
    suspend fun reset(){write {repository.resetDemo()}}
    suspend fun saveFirstStep(taskId: Int,revision: String,title: String): Boolean {
        if(title.isBlank()||title.length>500)return false
        return change(Triple("action",taskId,revision)) {s->
            val task=s.tasks.first {it.id==taskId&&it.status!="done"}
            val step=ActionTask(s.nextLocalId("action"),title.trim(),task.area,0,"Первый шаг: ${task.title}",projectId=task.projectId,captureId=task.captureId)
            s.copy(tasks=s.tasks+step).focusTask(step.id)
        }
    }
    suspend fun saveTask(id: Int?,title: String,status: String,area: String,projectId: Int?=null,revision: String?=null,dueDate: String?=workspace.value.data.tasks.firstOrNull {it.id==id}?.dueDate): Boolean {
        if(title.isBlank()||title.length>500||!validTaskDeadline(dueDate))return false
        return change(if(id!=null&&revision!=null)Triple("action",id,revision)else null) {s->require(id==null||s.tasks.any{it.id==id});require(projectId==null||s.projects.any{it.id==projectId});require(status in listOf("next","waiting","someday","done"));s.saveTask(id,title,status,area,projectId,dueDate)}
    }
    suspend fun saveProject(id: Int?,title: String,area: String,outcome: String,revision: String?=null): Boolean {
        if(title.isBlank()||title.length>500)return false
        return change(if(id!=null&&revision!=null)Triple("project",id,revision)else null) {s->require(id==null||s.projects.any{it.id==id});s.saveProject(id,title,area,outcome)}
    }
    suspend fun saveMaterial(id: Int?,title: String,body: String,revision: String?=null): Boolean {
        if(title.isBlank()||title.length>500)return false
        return change(if(id!=null&&revision!=null)Triple("reference",id,revision)else null) {s->require(id==null||s.materials.any{it.id==id});s.saveMaterial(id,title,body)}
    }
    suspend fun deleteProject(id: Int,revision: String)=change(Triple("project",id,revision)) {it.deleteProject(id)}
    suspend fun finishProject(id: Int,revision: String)=change(Triple("project",id,revision)) {it.finishProject(id,now())}
    suspend fun reopenProject(id: Int,revision: String)=change(Triple("project",id,revision)) {it.reopenProject(id)}
    suspend fun reopenProjectTask(id: Int,revision: String)=change(Triple("action",id,revision)) {s->
        val task=s.tasks.first {it.id==id&&it.status=="done"}
        s.saveTask(id,task.title,"next",task.area,task.projectId,task.dueDate)
    }
    suspend fun deleteMaterial(id: Int,revision: String)=change(Triple("reference",id,revision)) {s->s.copy(materials=s.materials.filterNot{it.id==id})}
    suspend fun deleteCapture(id: String)=change {s->s.copy(captures=s.captures.filterNot{it.id==id})}
    suspend fun inboxToThought(id: String): Boolean {
        val w=workspace.value;val capture=w.data.captures.find{it.id==id&&it.status=="inbox"}?:return false
        return try{write {undoReceipt=repository.moveToThought(w.scope,capture.id).receipt};true}catch(e: Exception){error.value="Не удалось сохранить мысль. Исходный сброс сохранён.";false}
    }
}
