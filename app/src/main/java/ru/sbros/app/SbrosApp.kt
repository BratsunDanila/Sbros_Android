package ru.sbros.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*
import ru.sbros.app.data.ai.taskAdviceContext
import ru.sbros.app.feature.*
import ru.sbros.app.data.local.BACKUP_MAX_BYTES
import ru.sbros.app.data.local.exportBackup
import ru.sbros.app.data.local.importBackup

data class Destination(val route: String,val label: Int,val icon: Int)
private val destinations=listOf(Destination("tasks",R.string.tasks,R.drawable.ic_bolt_nav),Destination("thoughts",R.string.thoughts,R.drawable.ic_feather_nav),Destination("capture",R.string.capture,R.drawable.ic_mic_nav),Destination("calendar",R.string.calendar,R.drawable.ic_calendar_nav),Destination("overview",R.string.overview,R.drawable.ic_review_nav))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SbrosApp(model: SbrosViewModel, appearance: Appearance, loaded: Boolean) {
    val graph=(LocalContext.current.applicationContext as SbrosApplication).graph
    val auth by graph.auth.state.collectAsState()
    val ai by graph.ai.state.collectAsState()
    val aiTextAllowed by graph.settings.aiTextConsent.collectAsState(initial=false)
    val online by graph.network.online.collectAsState()
    val hasLegacy by remember {graph.data.dao.observeLegacy().map{it>0}}.collectAsState(initial=false)
    val sync by graph.sync.state.collectAsState()
    val pendingImport by graph.settings.pendingImport.collectAsState(initial="")
    val c=LocalSbrosColors.current; val workspace by model.workspace.collectAsState();val state=workspace.data; val draft by model.draft.collectAsState()
    val storageError by model.error.collectAsState();val loadError by model.loadError.collectAsState()
    var sheet by rememberSaveable { mutableStateOf("") }; var selectedThought by rememberSaveable {mutableIntStateOf(0)}
    var selectedEvent by rememberSaveable {mutableIntStateOf(0)}; var eventDate by rememberSaveable {mutableStateOf("")}
    var selectedTask by rememberSaveable {mutableIntStateOf(0)}
    var selectedProject by rememberSaveable {mutableIntStateOf(0)}
    var selectedMaterial by rememberSaveable {mutableIntStateOf(0)}
    var editorRevision by rememberSaveable {mutableStateOf("")}
    var newKind by rememberSaveable {mutableStateOf("next")}
    var newProjectContext by rememberSaveable {mutableStateOf<Int?>(null)}
    var importing by remember {mutableStateOf(false)}
    var accountError by remember {mutableStateOf<String?>(null)}
    var savingCapture by remember {mutableStateOf(false)}
    val conflicts by remember(workspace.scope){graph.data.dao.observeConflicts(workspace.scope)}.collectAsState(initial=emptyList())
    val records by remember(workspace.scope){graph.data.dao.observe(workspace.scope)}.collectAsState(initial=emptyList())
    val snackbar=remember { SnackbarHostState() }; val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var backupBusy by remember {mutableStateOf(false)}
    var backupExportScope by rememberSaveable {mutableStateOf("")}
    var backupImportUri by remember {mutableStateOf<android.net.Uri?>(null)}
    var notificationJob by remember {mutableStateOf<Job?>(null)}
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun clearNotification(){notificationJob?.cancel();snackbar.currentSnackbarData?.dismiss()}
    BackHandler(enabled=sheet in setOf("auth","auth-register")){if(!auth.busy){graph.auth.clearError();sheet=""}}
    fun notify(text: String,undo: Boolean=false,onUndo: (() -> Unit)?=null) {
        notificationJob?.cancel()
        notificationJob=scope.launch {
            val result=snackbar.showSnackbar(text,if(undo||onUndo!=null)"Отменить" else null)
            if(result==SnackbarResult.ActionPerformed) {if(onUndo!=null)onUndo() else model.undo()}
        }
    }
    val exportBackupFile=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->
        if(uri!=null&&!backupBusy&&backupExportScope==workspace.scope) {
            backupBusy=true
            notify("Сохраняем резервную копию…")
            scope.launch {
                try {
                    model.flushDraft()
                    val bytes=graph.data.exportBackup(backupExportScope)
                    withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri,"wt")?.use {it.write(bytes)}?:error("File unavailable")}
                    notify("Резервная копия сохранена")
                } catch(e: Exception) {if(e is kotlinx.coroutines.CancellationException)throw e;model.error.value="Не удалось сохранить резервную копию. Проверь доступ к файлу и свободное место."}
                finally {backupBusy=false}
            }
        }
    }
    val importBackupFile=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(!backupBusy)backupImportUri=uri}
    fun completeTask(id: Int) {scope.launch {model.complete(id)?.let {token->notify("Готово. Хорошая работа.",onUndo={scope.launch {model.undoCompletion(token)}})}}}
    val nav=rememberNavController(); val back by nav.currentBackStackEntryAsState();val current=back?.destination?.route?:"capture"
    fun navigate(route: String) { model.cancelAnalysis();clearNotification();focus.clearFocus();keyboard?.hide();nav.navigate(route) {popUpTo(nav.graph.startDestinationId) {saveState=route!="capture"};launchSingleTop=true;restoreState=route !in setOf("overview","capture")} }
    fun revision(kind: String,id: Int)=state.recordRevisions["$kind:$id"]?:""
    fun editTask(id: Int) {selectedTask=id;editorRevision=revision("action",id);sheet="task-edit"}
    fun editMaterial(id: Int) {selectedMaterial=id;editorRevision=revision("reference",id);sheet="material-edit"}
    fun addEntry(kind: String="next",projectId: Int?=null) {newKind=if(kind in listOf("projects","notes","waiting","someday"))kind else "next";newProjectContext=projectId;sheet="new-entry"}
    fun openOverview(key: String) {if(key in collectionKeys)nav.navigate("overview/shelf/$key") else sheet=key}
    // A previous action's toast must not intercept controls in a newly opened sheet.
    LaunchedEffect(sheet) {
        if(sheet.isNotEmpty()) {
            notificationJob?.cancel()
            snackbar.currentSnackbarData?.dismiss()
        }
    }
    Surface(Modifier.fillMaxSize(),color=c.background) {
        if(sheet in setOf("auth","auth-register")) AuthScreen(sheet=="auth-register",auth,graph.auth.config.configured,
            {if(!auth.busy){graph.auth.clearError();sheet=""}},
            {graph.auth.clearError();sheet=if(sheet=="auth")"auth-register" else "auth"},
            {if(!auth.busy){graph.auth.clearError();sheet="";model.appearance(appearance.copy(welcomed=true))}}) {name,email,password->
                val success=graph.authenticate(sheet=="auth-register",name,email,password)
                if(success)sheet="";success
            }
        else if(!loaded) Box(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.Center){if(loadError)Column(Modifier.padding(28.dp)){Copy(storageError?:"Не удалось открыть записи");ActionButton("Повторить",model::retry)}else Glyph(R.drawable.ic_spark,42.dp)}
        else if(!appearance.welcomed) WelcomeScreen({model.appearance(appearance.copy(welcomed=true))},{sheet="auth-register"},{sheet="auth"})
        else Scaffold(modifier=Modifier.imePadding(),containerColor=c.background,contentWindowInsets=WindowInsets(0,0,0,0),
            snackbarHost={if(sheet.isEmpty())SnackbarHost(snackbar) {data -> Snackbar(data,containerColor=c.ink,contentColor=c.card,actionColor=c.accent)}},
            bottomBar={BottomNavigation(current,::navigate,when(workspace.scope){"demo"->"Демонстрация · к своим записям";"legacy"->"Прежняя версия · к своим записям";else->null}){scope.launch{graph.settings.leaveExamples()}}}) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
                NavHost(nav,startDestination="capture",
                    enterTransition={fadeIn(tween(100,easing=LinearEasing))},
                    exitTransition={fadeOut(tween(100,easing=LinearEasing))},
                    popEnterTransition={fadeIn(tween(100,easing=LinearEasing))},
                    popExitTransition={fadeOut(tween(100,easing=LinearEasing))}) {
                    composable("tasks") {TasksScreen(state,{nav.navigate("settings")},::completeTask,{selectedTask=state.focusedAction?.id?:0;sheet="help"},model::selectTask,model::skipTask,::editTask)}
                    composable("thoughts") {ThoughtsScreen(state,{selectedThought=it;nav.navigate("thought/$it")})}
                    composable("capture") {
                        val speech=ru.sbros.app.core.speech.rememberSpeechInput(draft){text->clearNotification();model.draft(text)}
                        CaptureScreen(draft,{text->clearNotification();model.draft(text)},
                            {speech.cancel();nav.navigate("settings")},{if(!savingCapture){clearNotification();speech.toggle()}},
                            {if(!savingCapture&&!speech.busy){savingCapture=true;scope.launch {try{if(model.later())notify("Сохранено во входящих")}finally{savingCapture=false}}}},
                            {if(!savingCapture&&!speech.busy){clearNotification();focus.clearFocus();keyboard?.hide();savingCapture=true;scope.launch {
                                try {model.prepareAnalysis()?.let {id->model.openAnalysis(id,graph.ai,autoStart=true);nav.navigate("capture/analysis/$id")}}
                                finally {savingCapture=false}
                            }}},savingCapture,
                            speechBusy=speech.busy,speechListening=speech.listening,speechPartial=speech.partial,speechMessage=speech.message,speechLevel=speech.level,
                            clear={clearNotification();speech.discard();model.clearDraft()})
                    }
                    composable("capture/analysis/{id}") {entry->
                        val id=entry.arguments?.getString("id").orEmpty()
                        val review by model.analysis.collectAsState()
                        LaunchedEffect(id) {model.openAnalysis(id,graph.ai)}
                        LaunchedEffect(id,review.captureId,review.scope,review.phase) {
                            if(review.captureId==id&&review.scope==workspace.scope&&review.phase=="saved")navigate("capture")
                        }
                        if(review.phase!="saved"||review.captureId!=id||review.scope!=workspace.scope)
                        CaptureAnalysisScreen(if(review.captureId==id&&review.scope==workspace.scope)review else ru.sbros.app.data.ai.CaptureAnalysisState(phase="loading"),ai.hasKey,
                            start={consent->model.requestAnalysis(graph.ai,consent)},manual=model::manualAnalysis,edit=model::editProposal,add=model::addProposal,confirm=model::confirmAnalysis,
                            back={model.resumeAnalysisOriginal();navigate("capture")},openAi={sheet="ai-settings"})
                    }
                    composable("calendar") {CalendarScreen(state,{nav.navigate("settings")},{event,date -> selectedEvent=event?.id?:0;editorRevision=revision("event",selectedEvent);eventDate=date;sheet="event"},::editTask,::completeTask)}
                    composable("overview") {OverviewScreen(state,{nav.navigate("settings")},::openOverview)}
                    composable("settings") { AppSettingsScreen(appearance,model::appearance,auth,workspace.scope,when {
                        sync.userId==auth.user?.id&&(sync.busy||sync.failed)->sync.message
                        !workspace.scope.startsWith("user:")->"Эти записи остаются только на устройстве"
                        !online->"Без интернета. Изменения сохраняются на устройстве"
                        conflicts.isNotEmpty()->"Есть разные версии записей"
                        records.any{it.dirty&&it.kind in ru.sbros.app.data.sync.syncKinds}&&!sync.busy->"Есть изменения на устройстве. Сохраним в облаке"
                        sync.userId==auth.user?.id->sync.message
                        else->"Записи доступны на устройстве"
                    },conflicts.size,ai,hasLegacy,
                        login={sheet="auth"},register={sheet="auth-register"},logout={scope.launch {graph.signOut()}},
                        sync={auth.user?.let {
                            if(sync.busy)notify("Синхронизация уже выполняется")
                            else {graph.scheduler.enqueue(it.id,manual=true);notify("Синхронизация запрошена. Статус появится в настройках аккаунта.")}
                        }},
                        importLocal={auth.user?.let {user->scope.launch {graph.settings.pendingImport(user.id)}}},
                        switchWorkspace={scope.launch {graph.settings.mode(if(workspace.scope.startsWith("user:"))"local" else "account")}},
                        openConflicts={sheet="sync-conflicts"},openVersions={sheet="conflict-history"},openAi={sheet="ai-settings"},
                        examples={scope.launch {graph.settings.mode("demo")}},leaveExamples={scope.launch {graph.settings.leaveExamples()}},legacy={scope.launch {graph.settings.mode("legacy")}},
                        resetDemo={scope.launch {model.reset();notify("Примеры восстановлены")}},close={nav.popBackStack()},
                        exportBackup={if(!backupBusy){backupExportScope=workspace.scope;exportBackupFile.launch("Сброс-${java.time.LocalDate.now()}.json")}},
                        importBackup={if(!backupBusy)importBackupFile.launch(arrayOf("application/json","text/plain","application/octet-stream"))}) }
                    composable("overview/shelf/{kind}") {entry->
                        val key=entry.arguments?.getString("kind")?:"next"
                        CollectionScreen(key,state,{nav.popBackStack()},{addEntry(key)},{id->nav.navigate("overview/project/$id")},::editTask,::editMaterial,::completeTask)
                    }
                    composable("overview/project/{id}") {entry->
                        val id=entry.arguments?.getString("id")?.toIntOrNull()?:0
                        ProjectScreen(state.projects.find {it.id==id},state,{nav.popBackStack()},{addEntry(projectId=id)},{selectedProject=id;editorRevision=revision("project",id);sheet="project-edit"},::editTask,::completeTask,
                            finish={val saved=model.finishProject(id,revision("project",id));if(saved)notify("Проект завершён. Он сохранён в завершённых.");saved},
                            reopen={val saved=model.reopenProject(id,revision("project",id));if(saved)notify("Проект снова в работе");saved},
                            reopenTask={taskId->val saved=model.reopenProjectTask(taskId,revision("action",taskId));if(saved)notify("Шаг возвращён в работу");saved})
                    }
                    composable("thought/{id}") {entry ->
                        val id=entry.arguments?.getString("id")?.toIntOrNull()?:0
                        val thought=state.thoughts.find {it.id==id}
                        if(thought!=null) ThoughtDetail(thought,{nav.popBackStack()},{model.favorite(id)},{selectedThought=id;editorRevision=revision("thought",id);sheet=it},{
                            val saved=model.mutateAwait(Triple("thought",id,revision("thought",id))) {s->s.copy(thoughts=s.thoughts.filter {it.id!=id})}
                            if(saved){nav.popBackStack();notify("Запись удалена",true)};saved
                        })
                    }
                }
            }
        }
        if(loaded&&appearance.welcomed&&sheet in collectionKeys) {
            LaunchedEffect(sheet) {val key=sheet;sheet="";nav.navigate("overview/shelf/$key")}
        } else if(sheet.isNotEmpty()&&sheet !in setOf("auth","auth-register")) ModalBottomSheet(onDismissRequest={sheet=""},containerColor=c.card,shape=RoundedCornerShape(topStart=30.dp,topEnd=30.dp),dragHandle={Box(Modifier.padding(top=12.dp,bottom=18.dp).size(35.dp,4.dp).background(c.border,CircleShape))},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=20.dp).padding(bottom=24.dp)) {
                when(sheet) {
                    "ai-settings" -> AiSettingsSheet(ai,graph.ai::save,graph.ai::remove,graph.ai::check){sheet=""}
                    "help" -> {
                        val adviceContext=remember(state.tasks,state.projects,selectedTask){state.taskAdviceContext(selectedTask)}
                        key(adviceContext) {TaskAdviceSheet(adviceContext,ai.hasKey,aiTextAllowed,
                            suggest={consent->if(consent)graph.settings.allowAiText();graph.ai.advice(adviceContext?:throw ru.sbros.app.data.ai.AiFailure("Дело больше недоступно."))},
                            openAi={sheet="ai-settings"},close={sheet=""})}
                    }
                    "event" -> EventSheet(state.events.find {it.id==selectedEvent},eventDate,{e ->val saved=model.mutateAwait(if(selectedEvent!=0)Triple("event",selectedEvent,editorRevision)else null) {s->val event=if(e.id==0)e.copy(id=s.nextLocalId("event"))else e;s.copy(events=s.events.filter {it.id!=event.id}+event)};if(saved)sheet="";saved},{val saved=model.mutateAwait(Triple("event",selectedEvent,editorRevision)) {s->s.copy(events=s.events.filter {it.id!=selectedEvent})};if(saved){sheet="";notify("Событие удалено",true)};saved})
                    "task-edit" -> state.tasks.find {it.id==selectedTask}?.let {task->TaskEditor(task,{sheet=""},projects=state.projects,
                        delete={val saved=model.deleteTask(task.id,editorRevision);if(saved){sheet="";notify(if(task.projectId!=null)"Шаг удалён" else "Дело удалено",true)};saved}) {title,status,area,dueDate,projectId->
                        val saved=model.saveTask(task.id,title,status,area,projectId,editorRevision,dueDate);if(saved)sheet="";saved
                    }}
                    "new-entry" -> NewEntryEditor(newKind,{sheet=""}) {title,kind,dueDate->
                        val saved=when(kind){"projects"->model.saveProject(null,title,"Личное","");"notes"->model.saveMaterial(null,title,"");else->model.saveTask(null,title,kind,"Личное",newProjectContext,dueDate=dueDate)}
                        if(saved){sheet="";notify("Добавлено")};saved
                    }
                    "project-edit" -> state.projects.find {it.id==selectedProject}?.let {project->ProjectEditor(project,{sheet=""},{title,area,outcome->
                        val saved=model.saveProject(project.id,title,area,outcome,editorRevision);if(saved)sheet="";saved
                    },{val saved=model.deleteProject(project.id,editorRevision);if(saved){sheet="";nav.popBackStack();notify("Проект удалён. Дела сохранены.",true)};saved})}
                    "material-edit" -> state.materials.find {it.id==selectedMaterial}?.let {material->MaterialEditor(material,{sheet=""},{title,body->
                        val saved=model.saveMaterial(material.id,title,body,editorRevision);if(saved)sheet="";saved
                    },{val saved=model.deleteMaterial(material.id,editorRevision);if(saved){sheet="";notify("Материал удалён",true)};saved})}
                    "edit-thought","continue","to-action" -> state.thoughts.find {it.id==selectedThought}?.let {thought -> ThoughtEditSheet(thought,sheet,{updated ->val saved=model.mutateAwait(Triple("thought",thought.id,editorRevision)) {s->s.copy(thoughts=s.thoughts.map {if(it.id==thought.id)updated else it})};if(saved)sheet="";saved},{title ->val saved=model.mutateAwait {s->s.copy(tasks=s.tasks+ActionTask(s.nextLocalId("action"),title,"Личное",0,"Из раздела «Мысли»",captureId=thought.captureId))};if(saved){sheet="";notify("Действие добавлено. Исходная мысль сохранена.")};saved}) }
                    "related" -> RelatedThoughtsSheet(state.thoughts.find {it.id==selectedThought},state.thoughts,{id->sheet="";selectedThought=id;nav.navigate("thought/$id"){launchSingleTop=true}},{sheet=""})
                    "thought-source" -> state.thoughts.find {it.id==selectedThought}?.sourceText?.let {InfoSheet(stringResource(R.string.thought_source),it){sheet=""}}
                    "history" -> CaptureHistorySheet(state,delete={id->val saved=model.deleteCapture(id);if(saved)notify("Сброс удалён из истории",true);saved}){sheet=""}
                    "sync-conflicts" -> SyncConflictsSheet(conflicts,{id,keepLocal->try{val user=auth.user?:error("No account");graph.sync.resolve(user.id,id,keepLocal);graph.scheduler.enqueue(user.id);true}catch(e: Exception){false}}){sheet=""}
                    "conflict-history" -> ConflictHistorySheet(records.filter{it.kind=="local_conflict_archive"}){sheet=""}
                    "inbox" -> InboxSheet(state.captures.filter{it.status=="inbox"},{capture ->sheet="";model.openAnalysis(capture.id,graph.ai,autoStart=true);nav.navigate("capture/analysis/${capture.id}")},{id ->val saved=model.deleteCapture(id);if(saved)notify("Запись удалена",true);saved},
                        {id->val saved=model.inboxToThought(id);if(saved)notify("Сохранено в «Мысли»",true);saved}, {sheet=""},{sheet="";navigate("capture")})
                }
            }
            SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(start=15.dp,end=15.dp,bottom=76.dp)) {data->Snackbar(data,containerColor=c.ink,contentColor=c.card,actionColor=c.accent)}
            }
        }
    }
    if(storageError!=null&&loaded)AlertDialog(onDismissRequest=model::clearError,title={Copy("Не удалось сохранить",21f,weight=750)},text={Copy(storageError!!)},confirmButton={TextButton(onClick=model::clearError){Text("Понятно")}})
    if(backupImportUri!=null)AlertDialog(onDismissRequest={if(!backupBusy)backupImportUri=null},
        title={Copy("Восстановить записи?",22f,weight=750)},
        text={Copy("Копии записей появятся в локальном пространстве. Текущие записи останутся. Черновики объединятся, а тот же файл повторно не добавится. Ключи ИИ и вход в аккаунт не восстанавливаются.",13f,line=21f)},
        confirmButton={TextButton(enabled=!backupBusy,onClick={
            val uri=backupImportUri
            if(uri!=null&&!backupBusy){backupBusy=true;scope.launch {
                try {
                    model.flushDraft()
                    val bytes=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.use {input->
                        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true){val count=input.read(buffer);if(count<0)break;require(out.size()+count<=BACKUP_MAX_BYTES);out.write(buffer,0,count)}
                        out.toByteArray()
                    }?:error("File unavailable")}
                    val count=graph.data.importBackup(bytes)
                    graph.settings.mode("local");model.retry();backupImportUri=null
                    notify(if(count==0)"Этот файл уже восстановлен или не содержит записей" else "Восстановлено записей: $count")
                } catch(e: Exception) {if(e is kotlinx.coroutines.CancellationException)throw e;model.error.value="Не удалось восстановить файл. Нужна резервная копия «Сброса» размером до 16 МБ. Текущие записи сохранены."}
                finally {backupBusy=false}
            }}
        }){Text(if(backupBusy)"Восстанавливаем…" else "Восстановить")}},
        dismissButton={TextButton(enabled=!backupBusy,onClick={backupImportUri=null}){Text("Отмена")}})
    if(loaded&&auth.user!=null&&pendingImport==auth.user!!.id)AlertDialog(onDismissRequest={if(!importing)scope.launch {graph.keepLocal()}},
        title={Copy("Добавить записи в облако?",23f,weight=750)},
        text={Column {Copy("У тебя есть записи на этом устройстве. Хочешь добавить их в облако? Дела, мысли и дневник будут загружены в твой аккаунт. Оригиналы останутся на устройстве.",13f,line=21f);if(accountError!=null){Spacer(Modifier.height(10.dp));Copy(accountError!!,12f,c.muted2)}}},
        confirmButton={TextButton(enabled=!importing,onClick={if(!importing){importing=true;scope.launch {try{graph.importLocal();accountError=null}catch(e: Exception){accountError="Не удалось подготовить перенос. Локальные записи сохранены."};importing=false}}}){Text(if(importing)"Подготавливаем…" else "Синхронизировать")}},
        dismissButton={TextButton(enabled=!importing,onClick={scope.launch {graph.keepLocal()}}){Text("Не сейчас")}})
}

@Composable private fun BottomNavigation(current: String, navigate: (String)->Unit,banner: String?=null,leaveExamples: ()->Unit={}) {
    val c=LocalSbrosColors.current
    Column(Modifier.fillMaxWidth().background(c.card).navigationBarsPadding()) {
        if(banner!=null)Box(Modifier.fillMaxWidth().background(c.soft).clickable(onClick=leaveExamples).padding(vertical=7.dp),contentAlignment=Alignment.Center){Copy(banner,10f,c.primary,650)}
        HorizontalDivider(color=c.border,thickness=1.dp)
        Row(Modifier.fillMaxWidth().height(75.dp).padding(horizontal=6.dp,vertical=7.dp)) {
            destinations.forEach {d ->
                val active=current==d.route||(d.route=="capture"&&current.startsWith("capture/"))||(d.route=="thoughts"&&current.startsWith("thought/"))||(d.route=="overview"&&current.startsWith("overview/"));val capture=d.route=="capture"
                val bg by animateColorAsState(if(active)c.soft else Color.Transparent,tween(250),label="navigation selection")
                Column(Modifier.weight(1f).fillMaxHeight().selectable(active,interactionSource=remember {MutableInteractionSource()},indication=null,onClick={navigate(d.route)},role=Role.Tab).semantics {contentDescription="Вкладка ${if(d.route=="tasks")"Дела" else if(d.route=="thoughts")"Мысли" else if(d.route=="capture")"Сброс" else if(d.route=="calendar")"Календарь" else "Обзор"}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Box(Modifier.size(if(capture)41.dp else 44.dp,if(capture)36.dp else 32.dp).then(if(capture)Modifier.background(Brush.linearGradient(listOf(lerp(c.card,c.primary,if(active).24f else .15f),c.accent)),RoundedCornerShape(15.dp)).border(1.dp,c.primary.copy(alpha=if(active).30f else .15f),RoundedCornerShape(15.dp)) else Modifier.background(bg,RoundedCornerShape(14.dp))),contentAlignment=Alignment.Center){Glyph(d.icon,19.dp,if(active||capture)c.primary else c.muted2)}
                    Spacer(Modifier.height(3.dp));Copy(stringResource(d.label),9f,if(active)c.primary else c.muted2,if(active)750 else 600,line=13.5f,maxLines=1)
                }
            }
        }
    }
}

@Composable private fun WelcomeScreen(start: ()->Unit,register: ()->Unit,login: ()->Unit) {
    val c=LocalSbrosColors.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f));Glyph(R.drawable.ic_spark,32.dp);Spacer(Modifier.height(15.dp));Eyebrow(stringResource(R.string.app_name).uppercase())
        Spacer(Modifier.height(30.dp));Copy(stringResource(R.string.welcome_title),48f,weight=760,line=50f,tracking=-3f,align=TextAlign.Center)
        Spacer(Modifier.height(18.dp));Copy(stringResource(R.string.welcome_sub),16f,c.primary,650,align=TextAlign.Center)
        Spacer(Modifier.height(14.dp));Copy(stringResource(R.string.welcome_body),12f,c.muted2,line=20f,align=TextAlign.Center)
        MicrophoneVisual(200.dp,true,start)
        Spacer(Modifier.weight(1f));ActionButton("Начать без аккаунта",start,Modifier.fillMaxWidth(),icon=R.drawable.ic_arrowright)
        Spacer(Modifier.height(9.dp));ActionButton("Создать аккаунт",register,Modifier.fillMaxWidth(),primary=false)
        Spacer(Modifier.height(9.dp));ActionButton(stringResource(R.string.have_account),login,Modifier.fillMaxWidth(),primary=false)
        Spacer(Modifier.height(16.dp));Copy("Твои записи могут оставаться только на этом устройстве.",10f,c.muted,align=TextAlign.Center)
    }
}
