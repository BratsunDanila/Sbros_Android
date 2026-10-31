package ru.sbros.app.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*

val collectionKeys=setOf("projects","waiting","someday","notes","next","done")
private fun shelfTitle(key: String)=when(key){"projects"->R.string.projects;"waiting"->R.string.waiting_screen;"someday"->R.string.someday;"notes"->R.string.materials;"done"->R.string.completed;else->R.string.next_actions}
private fun shelfSubtitle(key: String)=when(key){"projects"->R.string.projects_sub;"waiting"->R.string.waiting_sub;"someday"->R.string.someday_sub;"notes"->R.string.materials_sub;"done"->R.string.done_sub;else->R.string.next_actions_sub}

@Composable private fun CollectionTopBar(backLabel: String,back: ()->Unit,add: ()->Unit,edit: (() -> Unit)?=null) {
    val c=LocalSbrosColors.current
    Row(Modifier.fillMaxWidth().height(39.dp),verticalAlignment=Alignment.CenterVertically) {
        QuietAction(backLabel,R.drawable.ic_left,c.muted2,onClick=back)
        Spacer(Modifier.weight(1f))
        if(edit!=null)IconAction(R.drawable.ic_edit,stringResource(R.string.edit_project),edit)
        IconAction(R.drawable.ic_plus,stringResource(R.string.add),add)
    }
}

@Composable fun CollectionScreen(key: String,state: DemoState,back: ()->Unit,add: ()->Unit,
    openProject: (Int)->Unit,editTask: (Int)->Unit,editMaterial: (Int)->Unit,complete: (Int)->Unit) {
    val c=LocalSbrosColors.current
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
                    items(state.projects,key={"project-${it.id}"}) {project ->
                        val count=state.tasks.count {it.projectId==project.id&&it.status!="done"}
                        CollectionRow(project.title,pluralStringResource(R.plurals.project_actions,count,count),R.drawable.ic_folder,"project-row-${project.id}") {openProject(project.id)}
                        Spacer(Modifier.height(9.dp))
                    }
                    if(state.projects.isEmpty())item {EmptyCollection()}
                }
                "notes" -> {
                    items(state.materials,key={"material-${it.id}"}) {material ->
                        CollectionRow(material.title,material.body,R.drawable.ic_note,"material-row-${material.id}") {editMaterial(material.id)}
                        Spacer(Modifier.height(9.dp))
                    }
                    if(state.materials.isEmpty())item {EmptyCollection()}
                }
                else -> {
                    val tasks=state.tasks.filter {it.status==key}.let {if(key=="done")it.reversed()else it}
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

@Composable fun ProjectScreen(project: DemoProject?,state: DemoState,back: ()->Unit,add: ()->Unit,edit: ()->Unit,editTask: (Int)->Unit,complete: (Int)->Unit) {
    val c=LocalSbrosColors.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("project-screen")) {
        LazyColumn(Modifier.fillMaxSize().testTag("project-actions-list"),contentPadding=PaddingValues(start=if(maxWidth<350.dp)15.dp else 20.dp,end=if(maxWidth<350.dp)15.dp else 20.dp,top=18.dp,bottom=24.dp)) {
            item(key="hero") {
                CollectionTopBar(stringResource(R.string.projects),back,add,if(project!=null)edit else null)
                Spacer(Modifier.height(30.dp))
                if(project!=null) Paper(Modifier.fillMaxWidth().testTag("project-hero").clickable(onClick=edit),24.dp,true) {
                    Column(Modifier.padding(22.dp)) {
                        Copy(project.area,10f,c.muted2,700,Modifier.background(c.soft,RoundedCornerShape(13.dp)).padding(horizontal=10.dp,vertical=7.dp))
                        Spacer(Modifier.height(8.dp));Copy(project.title,24f,weight=700,line=28.8f,tracking=-1.2f)
                        Spacer(Modifier.height(8.dp));Copy(project.outcome,12f,c.muted2,line=19.2f)
                    }
                } else EmptyCollection()
                Row(Modifier.fillMaxWidth().padding(top=21.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Eyebrow(stringResource(R.string.project_actions_label),c.muted2,10f)
                    QuietAction(stringResource(R.string.add),R.drawable.ic_plus,onClick=add)
                }
                Spacer(Modifier.height(13.dp))
            }
            val tasks=state.tasks.filter {project!=null&&it.projectId==project.id}
            items(tasks,key={"project-task-${it.id}"}) {task ->ManagementTaskRow(task,state,complete,showProject=false){editTask(task.id)};Spacer(Modifier.height(8.dp))}
            if(tasks.isEmpty()&&project!=null)item {Copy(stringResource(R.string.add_small_step),color=c.muted2,modifier=Modifier.fillMaxWidth().padding(26.dp),align=androidx.compose.ui.text.style.TextAlign.Center)}
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
@Composable internal fun ManagementTaskRow(task: DemoTask,state: DemoState,complete: (Int)->Unit,showDeadline: Boolean=true,showProject: Boolean=true,embedded: Boolean=false,edit: ()->Unit) {
    val c=LocalSbrosColors.current
    TaskRowSurface(Modifier.fillMaxWidth().testTag("management-task-${task.id}"),20.dp,embedded) {
        Row(Modifier.fillMaxWidth().padding(start=5.dp,end=8.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).semantics {contentDescription="Выполнить ${task.title}"}.clickable(enabled=task.status!="done",onClick={complete(task.id)}),contentAlignment=Alignment.Center) {
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
