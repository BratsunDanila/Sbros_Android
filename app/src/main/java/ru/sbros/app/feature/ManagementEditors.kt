package ru.sbros.app.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable fun ReferenceField(value: String,change: (String)->Unit,label: String,tag: String=label,multiline: Boolean=false,placeholder: String="",minimumHeight: Dp?=null) {
    val c=LocalSbrosColors.current
    var focused by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(top=16.dp)) {
        if(label.isNotBlank()) {
            Copy(label.uppercase(Locale.forLanguageTag("ru")),11f,c.muted2,750,line=13.2f)
            Spacer(Modifier.height(7.dp))
        }
        BasicTextField(value,change,Modifier.fillMaxWidth().testTag(tag).semantics {contentDescription=label.ifBlank {placeholder}}
            .onFocusChanged {focused=it.isFocused}.heightIn(min=minimumHeight?:if(multiline)116.dp else 42.dp)
            .background(c.background,RoundedCornerShape(15.dp)).border(1.dp,if(focused)c.primary else c.border,RoundedCornerShape(15.dp)).padding(horizontal=13.dp,vertical=12.dp),
            textStyle=TextStyle(fontFamily=Onest,fontSize=13.sp,lineHeight=if(multiline)20.15.sp else 16.sp,color=c.ink),singleLine=!multiline,minLines=if(multiline)4 else 1,cursorBrush=SolidColor(c.primary),
            decorationBox={inner->Box {if(value.isEmpty()&&placeholder.isNotEmpty())Copy(placeholder,13f,c.muted);inner()}})
    }
}

@Composable internal fun ReferenceSelect(value: String,options: List<Pair<String,String>>,label: String,tag: String,change: (String)->Unit) {
    val c=LocalSbrosColors.current
    var expanded by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(top=16.dp)) {
        Copy(label.uppercase(Locale.forLanguageTag("ru")),11f,c.muted2,750,line=13.2f);Spacer(Modifier.height(7.dp))
        Box {
            Row(Modifier.fillMaxWidth().heightIn(min=42.dp).background(c.background,RoundedCornerShape(15.dp)).border(1.dp,c.border,RoundedCornerShape(15.dp)).testTag(tag).clickable {expanded=true}.padding(horizontal=13.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Copy(options.firstOrNull {it.first==value}?.second?:"",13f,modifier=Modifier.weight(1f),line=16f);Box(Modifier.rotate(90f)){Glyph(R.drawable.ic_chevron,14.dp,c.muted2)}
            }
            DropdownMenu(expanded,onDismissRequest={expanded=false},containerColor=c.card) {
                options.forEach {(key,text)->DropdownMenuItem(text={Copy(text,13f)},onClick={change(key);expanded=false},modifier=Modifier.semantics {contentDescription="Выбрать $text"})}
            }
        }
    }
}
@Composable private fun EditorHeading(title: String,subtitle: String?=null) {
    val c=LocalSbrosColors.current
    Copy(title,23f,weight=750,line=27.6f,tracking=-1.035f)
    if(subtitle!=null){Spacer(Modifier.height(6.dp));Copy(subtitle,color=c.muted2,line=19.2f);Spacer(Modifier.height(4.dp))}
}
@Composable private fun EditorActions(enabled: Boolean,saveLabel: String,onSave: ()->Unit,cancel: ()->Unit,icon: Int?=null) {
    Spacer(Modifier.height(17.dp));ActionButton(saveLabel,onSave,Modifier.fillMaxWidth().testTag("editor-save"),enabled=enabled,labelSize=13f,icon=icon)
    Spacer(Modifier.height(10.dp));ActionButton(stringResource(R.string.cancel),cancel,Modifier.fillMaxWidth().testTag("editor-cancel"),primary=false,minHeight=44.dp)
}

@Composable internal fun DeleteRecordAction(label: String,description: String,enabled: Boolean=true,iconOnly: Boolean=false,delete: suspend ()->Boolean) {
    val scope=rememberCoroutineScope()
    var confirming by rememberSaveable {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var failed by remember {mutableStateOf(false)}
    fun request(){if(enabled&&!busy){failed=false;confirming=true}}
    if(iconOnly)IconAction(R.drawable.ic_trash,label,::request)
    else QuietAction(if(busy)"Удаляем…" else label,R.drawable.ic_trash,MaterialTheme.colorScheme.error, onClick=::request)
    if(confirming)AlertDialog(onDismissRequest={if(!busy)confirming=false},
        title={Copy("$label?",21f,weight=750)},text={Column {
            Copy(description,13f,line=21f)
            if(failed){Spacer(Modifier.height(10.dp));Copy("Не удалось удалить. Попробуй ещё раз.",12f,MaterialTheme.colorScheme.error)}
        }},
        confirmButton={TextButton(enabled=!busy&&enabled,onClick={
            if(!busy){busy=true;scope.launch {
                try {if(delete())confirming=false else failed=true}
                catch(e: CancellationException){throw e}
                catch(e: Exception){failed=true}
                finally {busy=false}
            }}
        }){Text(if(busy)"Удаляем…" else "Удалить",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(enabled=!busy,onClick={confirming=false}){Text("Отмена")}})
}

@Composable fun TaskEditor(task: ActionTask,cancel: ()->Unit,projects: List<Project> = emptyList(),delete: suspend ()->Boolean,save: suspend (String,String,String,String?,Int?)->Boolean) {
    val scope=rememberCoroutineScope()
    var title by rememberSaveable(task.id){mutableStateOf(task.title)}
    var status by rememberSaveable(task.id){mutableStateOf(task.status)}
    var area by rememberSaveable(task.id){mutableStateOf(task.area)}
    var projectId by rememberSaveable(task.id){mutableStateOf(task.projectId)}
    var dueDate by rememberSaveable(task.id){mutableStateOf(task.dueDate.orEmpty())}
    var dateError by rememberSaveable(task.id){mutableStateOf(false)}
    var submitted by remember(task.id){mutableStateOf(false)}
    var error by rememberSaveable(task.id){mutableStateOf(false)}
    EditorHeading(stringResource(R.string.task_editor),stringResource(R.string.task_editor_sub))
    ReferenceField(title,{title=it},stringResource(R.string.what_to_do),"task-title-input")
    val statuses=listOf("next" to stringResource(R.string.next_actions),"waiting" to stringResource(R.string.waiting_screen),"someday" to stringResource(R.string.someday))+
        if(task.status=="done")listOf("done" to stringResource(R.string.completed)) else emptyList()
    ReferenceSelect(status,statuses,stringResource(R.string.stored_in),"task-status-input"){status=it}
    ReferenceField(area,{area=it},stringResource(R.string.area),"task-area-input")
    val selectableProjects=projects.filter {it.completedAt==null||it.id==task.projectId}
    if(selectableProjects.isNotEmpty())ReferenceSelect(projectId?.toString()?:"",listOf("" to "Самостоятельное дело")+selectableProjects.map {it.id.toString() to it.title},"Проект","task-project-input") {projectId=it.toIntOrNull()}
    if(status!="done"&&projects.any {it.id==projectId&&it.completedAt!=null}) {
        Spacer(Modifier.height(10.dp));Copy("Сохранение вернёт этот проект в работу вместе с делом.",11f,LocalSbrosColors.current.muted2,line=17f)
    }
    DateField(dueDate,{dueDate=it;dateError=false},"Срок","task-due-date-input",placeholder="Без срока")
    if(dueDate.isNotBlank())QuietAction("Убрать срок",R.drawable.ic_left) {dueDate="";dateError=false}
    if(dateError)Copy("Проверь дату: ДД.ММ.ГГГГ или оставь поле пустым.",11f,MaterialTheme.colorScheme.error)
    if(error)Copy(stringResource(R.string.save_failed),color=MaterialTheme.colorScheme.error)
    EditorActions(title.isNotBlank()&&title.length<=500&&!submitted,stringResource(R.string.save),{
        if(!validTaskDeadline(dueDate))dateError=true
        else if(!submitted){submitted=true;scope.launch {if(!save(title,status,area,dueDate.takeIf {it.isNotBlank()},projectId)){submitted=false;error=true}}}
    },cancel)
    Spacer(Modifier.height(10.dp))
    DeleteRecordAction(if(task.projectId!=null)"Удалить шаг" else "Удалить дело",
        if(task.projectId!=null)"Этот шаг исчезнет из проекта и общих списков дел. Проект, остальные шаги и исходный сброс останутся." else "Дело исчезнет из списка и календаря. Исходный сброс останется.",enabled=!submitted,delete=delete)
}

@Composable fun NewEntryEditor(initialKind: String,cancel: ()->Unit,save: suspend (String,String,String?)->Boolean) {
    val scope=rememberCoroutineScope()
    var title by rememberSaveable {mutableStateOf("")}
    var kind by rememberSaveable {mutableStateOf(initialKind)}
    var dueDate by rememberSaveable {mutableStateOf("")}
    var dateError by rememberSaveable {mutableStateOf(false)}
    var submitted by remember {mutableStateOf(false)}
    var error by rememberSaveable {mutableStateOf(false)}
    EditorHeading(stringResource(R.string.add_task),stringResource(R.string.one_thought_one_action))
    ReferenceField(title,{title=it},"","new-title-input",true,stringResource(R.string.what_needs_doing))
    ReferenceSelect(kind,listOf("next" to stringResource(R.string.next_actions),"projects" to stringResource(R.string.new_project),"waiting" to stringResource(R.string.waiting_screen),"someday" to stringResource(R.string.someday),"notes" to stringResource(R.string.materials)),stringResource(R.string.save_to),"new-kind-input"){kind=it}
    val taskKind=kind in listOf("next","waiting","someday")
    if(taskKind) {
        DateField(dueDate,{dueDate=it;dateError=false},"Срок","new-due-date-input",placeholder="Без срока")
        if(dueDate.isNotBlank())QuietAction("Убрать срок",R.drawable.ic_left) {dueDate="";dateError=false}
        if(dateError)Copy("Проверь дату: ДД.ММ.ГГГГ или оставь поле пустым.",11f,MaterialTheme.colorScheme.error)
    }
    if(error)Copy(stringResource(R.string.save_failed),color=MaterialTheme.colorScheme.error)
    EditorActions(title.isNotBlank()&&title.length<=500&&!submitted,stringResource(R.string.add),{
        if(taskKind&&!validTaskDeadline(dueDate))dateError=true
        else if(!submitted){submitted=true;scope.launch {if(!save(title,kind,if(taskKind)dueDate.takeIf {it.isNotBlank()} else null)){submitted=false;error=true}}}
    },cancel,R.drawable.ic_check)
}

@Composable fun ProjectEditor(project: Project,cancel: ()->Unit,save: suspend (String,String,String)->Boolean,delete: suspend ()->Boolean) {
    val scope=rememberCoroutineScope()
    var title by rememberSaveable(project.id){mutableStateOf(project.title)}
    var area by rememberSaveable(project.id){mutableStateOf(project.area)}
    var outcome by rememberSaveable(project.id){mutableStateOf(project.outcome)}
    var submitted by remember(project.id){mutableStateOf(false)}
    var error by rememberSaveable(project.id){mutableStateOf(false)}
    EditorHeading(stringResource(R.string.project))
    ReferenceField(title,{title=it},stringResource(R.string.title_label),"project-title-input")
    ReferenceField(outcome,{outcome=it},stringResource(R.string.project_result),"project-outcome-input",true)
    ReferenceField(area,{area=it},stringResource(R.string.area),"project-area-input")
    if(error)Copy(stringResource(R.string.save_failed),color=MaterialTheme.colorScheme.error)
    EditorActions(title.isNotBlank()&&title.length<=500&&!submitted,stringResource(R.string.save),{
        if(!submitted){submitted=true;scope.launch {if(!save(title,area,outcome)){submitted=false;error=true}}}
    },cancel)
    DeleteRecordAction(stringResource(R.string.delete_project),stringResource(R.string.project_tasks_kept),enabled=!submitted,delete=delete)
}
@Composable fun MaterialEditor(material: ReferenceMaterial,cancel: ()->Unit,save: suspend (String,String)->Boolean,delete: suspend ()->Boolean) {
    val scope=rememberCoroutineScope()
    var title by rememberSaveable(material.id){mutableStateOf(material.title)}
    var body by rememberSaveable(material.id){mutableStateOf(material.body)}
    var submitted by remember(material.id){mutableStateOf(false)}
    var error by rememberSaveable(material.id){mutableStateOf(false)}
    EditorHeading(stringResource(R.string.material))
    ReferenceField(title,{title=it},stringResource(R.string.title_label),"material-title-input")
    ReferenceField(body,{body=it},stringResource(R.string.note_label),"material-body-input",true)
    if(error)Copy(stringResource(R.string.save_failed),color=MaterialTheme.colorScheme.error)
    EditorActions(title.isNotBlank()&&title.length<=500&&!submitted,stringResource(R.string.save),{
        if(!submitted){submitted=true;scope.launch {if(!save(title,body)){submitted=false;error=true}}}
    },cancel)
    DeleteRecordAction(stringResource(R.string.delete_material),"Материал исчезнет из списка. Исходная запись и остальные результаты сброса останутся.",enabled=!submitted,delete=delete)
}
