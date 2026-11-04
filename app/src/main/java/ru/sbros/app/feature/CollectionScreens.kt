package ru.sbros.app.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*
import kotlinx.coroutines.launch

val collectionKeys=setOf("projects","waiting","someday","notes","next","done")
private fun shelfTitle(key: String)=when(key){"projects"->R.string.projects;"waiting"->R.string.waiting_screen;"someday"->R.string.someday;"notes"->R.string.materials;"done"->R.string.completed;else->R.string.next_actions}
private fun shelfSubtitle(key: String)=when(key){"projects"->R.string.projects_sub;"waiting"->R.string.waiting_sub;"someday"->R.string.someday_sub;"notes"->R.string.materials_sub;"done"->R.string.done_sub;else->R.string.next_actions_sub}

@Composable private fun CollectionTopBar(backLabel: String,back: ()->Unit,add: (() -> Unit)?,edit: (() -> Unit)?=null) {
    val c=LocalSbrosColors.current
    Row(Modifier.fillMaxWidth().height(39.dp),verticalAlignment=Alignment.CenterVertically) {
        QuietAction(backLabel,R.drawable.ic_left,c.muted2,onClick=back)
        Spacer(Modifier.weight(1f))
        if(edit!=null)IconAction(R.drawable.ic_edit,stringResource(R.string.edit_project),edit)
        if(add!=null)IconAction(R.drawable.ic_plus,stringResource(R.string.add),add)
    }
}

@Composable fun CollectionScreen(key: String,state: WorkspaceState,back: ()->Unit,add: ()->Unit,
    openProject: (Int)->Unit,editTask: (Int)->Unit,editMaterial: (Int)->Unit,complete: (Int)->Unit) {
    val c=LocalSbrosColors.current
    var completedExpanded by rememberSaveable {mutableStateOf(false)}
    BoxWithConstraints(Modifier.fillMaxSize().testTag("collection-screen-$key")) {
        LazyColumn(Modifier.fillMaxSize().testTag("collection-list"),contentPadding=PaddingValues(start=if(maxWidth<350.dp)15.dp else 20.dp,end=if(maxWidth<350.dp)15.dp else 20.dp,top=18.dp,bottom=24.dp)) {
            item(key="header") {
                CollectionTopBar(stringResource(R.string.overview),back,add)
                Spacer(Modifier.height(28.dp))
                Copy(stringResource(shelfTitle(key)),39f,weight=750,line=44.85f,tracking=-2.73f)
                Spacer(Modifier.height(5.dp));Copy(stringResource(shelfSubtitle(key)),color=c.muted2,line=19.2f)
                Spacer(Modifier.height(25.dp))
            }
            when(key) {
                "projects" -> {
                    val active=state.projects.filter {it.completedAt==null}
                    val completed=state.projects.filter {it.completedAt!=null}.sortedByDescending {it.completedAt}
                    items(active,key={"project-${it.id}"}) {project ->
                        ProjectCollectionRow(project,state.projectProgress(project.id)) {openProject(project.id)}
                        Spacer(Modifier.height(9.dp))
                    }
                    if(state.projects.isEmpty())item {EmptyCollection()}
                    else if(active.isEmpty())item {Copy("Активных проектов пока нет.",color=c.muted2,modifier=Modifier.padding(vertical=16.dp))}
                    if(completed.isNotEmpty()) {
                        item(key="completed-projects") {FoldRow("Завершённые · ${completed.size}",completedExpanded){completedExpanded=!completedExpanded}}
                        if(completedExpanded)items(completed,key={"project-${it.id}"}) {project->
                            ProjectCollectionRow(project,state.projectProgress(project.id)){openProject(project.id)}
                            Spacer(Modifier.height(9.dp))
                        }
                    }
                }
                "notes" -> {
                    items(state.materials,key={"material-${it.id}"}) {material ->
                        CollectionRow(material.title,material.body,R.drawable.ic_note,"material-row-${material.id}") {editMaterial(material.id)}
                        Spacer(Modifier.height(9.dp))
                    }
                    if(state.materials.isEmpty())item {EmptyCollection()}
                }
                else -> {
                    val tasks=(if(key=="next")state.availableActions else state.tasks.filter {it.status==key}).let {if(key=="done")it.reversed()else it}
                    if(key=="next") {
                        items(projectTaskGroups(tasks,state.projects),key={it.key}) {group ->
                            if(group.project!=null)ProjectTaskGroup(group) {task ->
                                ManagementTaskRow(task,state,complete,showProject=false,embedded=true) {editTask(task.id)}
                            } else {
                                val task=group.tasks.first()
                                ManagementTaskRow(task,state,complete) {editTask(task.id)}
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    } else {
                        items(tasks,key={"action-${it.id}"}) {task ->
                            ManagementTaskRow(task,state,complete) {editTask(task.id)}
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if(tasks.isEmpty())item {EmptyCollection()}
                }
            }
        }
    }
}

@Composable fun ProjectScreen(project: Project?,state: WorkspaceState,back: ()->Unit,add: ()->Unit,edit: ()->Unit,editTask: (Int)->Unit,complete: (Int)->Unit,
    finish: suspend ()->Boolean,reopen: suspend ()->Boolean,reopenTask: suspend (Int)->Boolean) {
    val c=LocalSbrosColors.current
    val scope=rememberCoroutineScope()
    var completedExpanded by rememberSaveable(project?.id) {mutableStateOf(false)}
    var busy by remember(project?.id) {mutableStateOf(false)}
    val archived=project?.completedAt!=null
    val tasks=state.tasks.filter {project!=null&&it.projectId==project.id}
    val active=tasks.filter {it.status!="done"}
    val done=tasks.filter {it.status=="done"}
    val progress=ProjectProgress(done.size,tasks.size)
    fun perform(action: suspend ()->Boolean) {if(!busy){busy=true;scope.launch {try {action()}finally{busy=false}}}}
    BoxWithConstraints(Modifier.fillMaxSize().testTag("project-screen")) {
        LazyColumn(Modifier.fillMaxSize().testTag("project-actions-list"),contentPadding=PaddingValues(start=if(maxWidth<350.dp)15.dp else 20.dp,end=if(maxWidth<350.dp)15.dp else 20.dp,top=18.dp,bottom=24.dp)) {
            item(key="hero") {
                CollectionTopBar(stringResource(R.string.projects),back,if(project!=null&&!archived)add else null,if(project!=null)edit else null)
                Spacer(Modifier.height(30.dp))
                if(project!=null)ProjectHero(project,progress,edit) else EmptyCollection()
                if(project!=null&&archived) {
                    Spacer(Modifier.height(16.dp))
                    ActionButton(if(busy)"Сохраняем…" else "Вернуть в работу",{perform(reopen)},Modifier.fillMaxWidth(),primary=false,enabled=!busy)
                } else if(project!=null&&project.completionReady&&progress.allDone) {
                    Spacer(Modifier.height(16.dp))
                    Paper(Modifier.fillMaxWidth(),22.dp) {Column(Modifier.padding(18.dp)) {
                        Copy("Все шаги выполнены.",17f,weight=750,tracking=-.5f)
                        Spacer(Modifier.height(6.dp));Copy("Результат достигнут? Можно завершить проект или добавить ещё один шаг.",12f,c.muted2,line=19f)
                        Spacer(Modifier.height(15.dp))
                        ActionButton(if(busy)"Сохраняем…" else "Завершить проект",{perform(finish)},Modifier.fillMaxWidth(),enabled=!busy)
                        QuietAction("Добавить шаг",R.drawable.ic_plus){if(!busy)add()}
                    }}
                }
                if(project!=null&&!archived)Row(Modifier.fillMaxWidth().padding(top=21.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Eyebrow(stringResource(R.string.project_actions_label),c.muted2,10f)
                    QuietAction(stringResource(R.string.add),R.drawable.ic_plus,onClick=add)
                }
                Spacer(Modifier.height(13.dp))
            }
            items(active,key={"project-task-${it.id}"}) {task ->ManagementTaskRow(task,state,complete,showProject=false){editTask(task.id)};Spacer(Modifier.height(8.dp))}
            if(tasks.isEmpty()&&project!=null)item {Copy(if(archived)"В проекте нет шагов." else stringResource(R.string.add_small_step),color=c.muted2,modifier=Modifier.fillMaxWidth().padding(26.dp),align=androidx.compose.ui.text.style.TextAlign.Center)}
            else if(project!=null&&active.isEmpty()&&!archived&&!(project.completionReady&&progress.allDone))item {Copy("Незавершённых шагов нет. Можно добавить новое действие.",color=c.muted2,modifier=Modifier.padding(vertical=16.dp))}
            if(done.isNotEmpty()) {
                item(key="completed-steps") {FoldRow("Выполнено · ${done.size}",completedExpanded){completedExpanded=!completedExpanded}}
                if(completedExpanded)items(done,key={"project-task-${it.id}"}) {task->
                    ManagementTaskRow(task,state,complete,showProject=false,reopen={id->perform {reopenTask(id)}}){editTask(task.id)}
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable private fun ProjectHero(project: Project,progress: ProjectProgress,edit: ()->Unit) {
    val c=LocalSbrosColors.current
    val archived=project.completedAt!=null
    Paper(Modifier.fillMaxWidth().testTag("project-hero").clickable(onClick=edit),25.dp,true) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(c.card.copy(alpha=.8f),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {
                    Glyph(R.drawable.ic_folder,20.dp)
                }
                Spacer(Modifier.width(10.dp))
                Copy(project.area.ifBlank {"Личное"},10.5f,c.muted2,650,Modifier.weight(1f),maxLines=1)
                Spacer(Modifier.width(8.dp))
                Row(Modifier.background(c.card.copy(alpha=.65f),RoundedCornerShape(12.dp)).padding(horizontal=9.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).background(if(archived)c.good else c.primary,CircleShape))
                    Spacer(Modifier.width(5.dp));Copy(if(archived)"Завершён" else "В работе",9.5f,c.muted2,650)
                }
            }
            Spacer(Modifier.height(17.dp))
            Copy(project.title,27f,weight=750,line=32f,tracking=-1.35f)
            Spacer(Modifier.height(14.dp))
            if(project.outcome.isNotBlank()) {
                Eyebrow("Результат",c.muted2,8f)
                Spacer(Modifier.height(6.dp));Copy(project.outcome,12.5f,c.muted2,line=20f,maxLines=4)
            } else {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Glyph(R.drawable.ic_edit,13.dp,c.muted2);Spacer(Modifier.width(6.dp))
                    Copy("Добавь желаемый результат",11f,c.muted2,line=17f)
                }
            }
            Spacer(Modifier.height(17.dp))
            androidx.compose.material3.HorizontalDivider(color=c.primary.copy(alpha=.12f))
            Spacer(Modifier.height(14.dp))
            ProjectProgressLine(progress)
            if(archived){Spacer(Modifier.height(9.dp));Copy("Завершён ${projectDate(project.completedAt!!)}",10f,c.muted2)}
        }
    }
}

private fun projectDate(value: String)=runCatching {java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}.getOrDefault("")

@Composable private fun FoldRow(label: String,expanded: Boolean,toggle: ()->Unit) {
    val c=LocalSbrosColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick=toggle).padding(vertical=18.dp).semantics {stateDescription=if(expanded)"Развёрнуто" else "Свёрнуто"},verticalAlignment=Alignment.CenterVertically) {
        Copy(label,12f,c.muted2,700,Modifier.weight(1f))
        Copy(if(expanded)"−" else "+",20f,c.muted2)
    }
}

@Composable private fun ProjectProgressLine(progress: ProjectProgress) {
    val c=LocalSbrosColors.current
    Copy(progress.label,11f,c.muted2,line=16f)
    if(progress.total>0) {
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.soft)) {
            if(progress.done>0)Box(Modifier.fillMaxHeight().fillMaxWidth(progress.done.toFloat()/progress.total).background(c.primary.copy(alpha=.65f)))
        }
    }
}

@Composable private fun ProjectCollectionRow(project: Project,progress: ProjectProgress,open: ()->Unit) {
    val c=LocalSbrosColors.current
    Paper(Modifier.fillMaxWidth().testTag("project-row-${project.id}").clickable(onClick=open),20.dp) {
        Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(39.dp).background(c.soft,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Glyph(if(project.completedAt==null)R.drawable.ic_folder else R.drawable.ic_check,20.dp)}
            Column(Modifier.weight(1f)) {
                Copy(project.title,13f,weight=750,line=18f)
                Spacer(Modifier.height(6.dp))
                if(project.completedAt!=null)Copy("Завершён ${projectDate(project.completedAt)}",11f,c.muted2)
                else ProjectProgressLine(progress)
            }
            Glyph(R.drawable.ic_chevron,16.dp,c.muted)
        }
    }
}

@Composable private fun CollectionRow(title: String,subtitle: String,icon: Int,tag: String,click: ()->Unit) {
    val c=LocalSbrosColors.current
    Paper(Modifier.fillMaxWidth().testTag(tag).clickable(onClick=click),20.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(39.dp).background(c.soft,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Glyph(icon,20.dp)}
            Column(Modifier.weight(1f)){Copy(title,13f,weight=750);if(subtitle.isNotBlank()){Spacer(Modifier.height(4.dp));Copy(subtitle,11f,c.muted2,line=14.3f)}}
            Glyph(R.drawable.ic_chevron,16.dp,c.muted)
        }
    }
}
@Composable internal fun ManagementTaskRow(task: ActionTask,state: WorkspaceState,complete: (Int)->Unit,showDeadline: Boolean=true,showProject: Boolean=true,embedded: Boolean=false,reopen: ((Int)->Unit)?=null,edit: ()->Unit) {
    val c=LocalSbrosColors.current
    TaskRowSurface(Modifier.fillMaxWidth().testTag("management-task-${task.id}"),20.dp,embedded) {
        Row(Modifier.fillMaxWidth().padding(start=5.dp,end=8.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).semantics {contentDescription=if(task.status!="done")"Выполнить ${task.title}" else if(reopen!=null)"Вернуть в работу: ${task.title}" else "Выполнено: ${task.title}"}.clickable(enabled=task.status!="done"||reopen!=null,onClick={if(task.status=="done")reopen?.invoke(task.id)else complete(task.id)}),contentAlignment=Alignment.Center) {
                Box(Modifier.size(21.dp).background(if(task.status=="done")c.good else androidx.compose.ui.graphics.Color.Transparent,CircleShape).border(1.5.dp,if(task.status=="done")c.good else c.muted,CircleShape),contentAlignment=Alignment.Center){if(task.status=="done")Glyph(R.drawable.ic_check,16.dp,androidx.compose.ui.graphics.Color.White)}
            }
            Row(Modifier.weight(1f).heightIn(min=44.dp).testTag("management-edit-task-${task.id}").clickable(onClick=edit),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Copy(task.title,12.5f,weight=700,line=18.1f)
                    Spacer(Modifier.height(5.dp));Copy(task.area,10.5f,c.muted)
                    if(showProject)state.projects.find {it.id==task.projectId}?.let {project->Spacer(Modifier.height(6.dp));ProjectStepPill(project.title,compact=true)}
                    if(showDeadline&&!task.dueDate.isNullOrBlank()){Spacer(Modifier.height(6.dp));TaskDeadlinePill(task.dueDate,compact=true)}
                }
                Spacer(Modifier.width(8.dp));Glyph(R.drawable.ic_chevron,16.dp,c.muted)
            }
        }
    }
}
@Composable private fun EmptyCollection(){val c=LocalSbrosColors.current;Copy(stringResource(R.string.empty_collection),color=c.muted2,modifier=Modifier.fillMaxWidth().padding(vertical=26.dp,horizontal=10.dp),align=androidx.compose.ui.text.style.TextAlign.Center)}
