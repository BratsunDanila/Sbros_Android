package ru.sbros.app.feature

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*

@Composable fun TasksScreen(state: WorkspaceState, settings: ()->Unit, complete: (Int)->Unit,
    help: ()->Unit, select: (Int)->Unit, skip: ()->Unit, edit: (Int)->Unit = {}) {
    val c=LocalSbrosColors.current
    val listState=rememberLazyListState()
    val compact=LocalConfiguration.current.screenHeightDp<=740
    val task=state.focusedAction
    val all=state.availableActions
    val rest=state.remainingActions
    val groups=remember(rest,state.projects) {projectTaskGroups(rest,state.projects)}
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow=maxWidth<350.dp
        LazyColumn(Modifier.fillMaxSize().testTag("tasks-list"),state=listState,
            contentPadding=PaddingValues(start=if(narrow)15.dp else 20.dp,end=if(narrow)15.dp else 20.dp,top=ContentTopPadding,bottom=24.dp)) {
            item(key="header",contentType="header") {
                Heading(stringResource(R.string.tasks),stringResource(R.string.tasks_sub)) {
                    IconAction(R.drawable.ic_sliders,stringResource(R.string.settings),settings)
                }
                Spacer(Modifier.height(contentHeadingGap()))
            }
            item(key="focus",contentType="focus") {
                Row(Modifier.fillMaxWidth().padding(horizontal=1.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Eyebrow(stringResource(R.string.suggested_now),c.muted)
                    Copy(if(task==null)stringResource(R.string.free_now) else stringResource(R.string.task_position,all.indexOfFirst {it.id==task.id}+1,all.size),10f,c.muted,650)
                }
                Spacer(Modifier.height(12.dp))
                TaskDeck(task,state.projects.find {it.id==task?.projectId}?.title,compact,narrow,complete,skip,edit)
            }
            if(task!=null) item(key="help",contentType="help") {
                Spacer(Modifier.height(11.dp))
                Paper(Modifier.fillMaxWidth().clickable(onClick=help),16.dp) {
                    Row(Modifier.padding(horizontal=15.dp).heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                        Glyph(R.drawable.ic_spark,16.dp);Spacer(Modifier.width(9.dp))
                        Copy(stringResource(R.string.first_step),11.5f,weight=650,modifier=Modifier.weight(1f))
                        Glyph(R.drawable.ic_arrowright,15.dp,c.muted)
                    }
                }
            }
            item(key="remaining-header",contentType="list-header") {
                Spacer(Modifier.height(if(compact)13.dp else 19.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal=1.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Copy(stringResource(R.string.remaining_tasks),if(narrow)13f else 14f,weight=750,tracking=-.35f)
                    Copy(stringResource(R.string.remaining_count,rest.size),11f,c.muted2,650)
                }
                Spacer(Modifier.height(if(compact)6.dp else 10.dp))
            }
            if(rest.isEmpty()) item(key="empty-remaining",contentType="empty") {
                Paper(Modifier.fillMaxWidth(),17.dp) {Copy(stringResource(if(all.size==1)R.string.one_task_left else R.string.no_new_tasks),11f,c.muted2,modifier=Modifier.padding(13.dp))}
            }
            items(groups,key={it.key},contentType={if(it.project!=null)"project" else "task"}) { group ->
                if(group.project!=null) {
                    ProjectTaskGroup(group) {action ->
                        TaskListRow(action,null,compact,Modifier,complete,{edit(action.id)},embedded=true)
                    }
                } else {
                    val action=group.tasks.first()
                    TaskListRow(action,state.projects.find {it.id==action.projectId}?.title,compact,Modifier.animateItem(),complete,{edit(action.id)})
                }
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable private fun TaskDeck(task: ActionTask?,projectTitle: String?,compact: Boolean,narrow: Boolean,complete: (Int)->Unit,skip: ()->Unit,edit: (Int)->Unit) {
    val c=LocalSbrosColors.current
    Box(Modifier.fillMaxWidth().padding(bottom=13.dp)) {
        Box(Modifier.matchParentSize().padding(horizontal=25.dp).offset(y=10.dp).background(c.accent,RoundedCornerShape(25.dp)).border(1.dp,c.border,RoundedCornerShape(25.dp)))
        Box(Modifier.matchParentSize().padding(horizontal=13.dp).offset(y=5.dp).background(c.soft,RoundedCornerShape(25.dp)).border(1.dp,c.border,RoundedCornerShape(25.dp)))
        if(task==null) Paper(Modifier.fillMaxWidth().heightIn(min=222.dp),25.dp) {
            Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Glyph(R.drawable.ic_spark,40.dp);Spacer(Modifier.height(14.dp))
                Copy(stringResource(R.string.empty_tasks),19f,weight=700)
                Copy(stringResource(R.string.empty_tasks_sub),color=c.muted2,align=TextAlign.Center)
            }
        } else key(task.id) {
            var drag by remember {mutableFloatStateOf(0f)}
            var dragging by remember {mutableStateOf(false)}
            val density=LocalDensity.current
            val translation by animateFloatAsState(drag*.55f,if(dragging)snap() else spring(stiffness=450f),label="task swipe")
            val onComplete by rememberUpdatedState(complete)
            val onSkip by rememberUpdatedState(skip)
            Paper(Modifier.fillMaxWidth().testTag("focused-task-card").heightIn(min=if(compact)222.dp else 253.dp)
                .clickable(interactionSource=remember {MutableInteractionSource()},indication=null,onClickLabel="Редактировать дело",onClick={edit(task.id)})
                .graphicsLayer {translationX=translation;rotationZ=translation*.012f}
                .pointerInput(task.id) {
                    detectHorizontalDragGestures(onDragStart={dragging=true},onDragEnd={
                        val distance=drag/density.density
                        dragging=false;drag=0f
                        if(distance>100f)onComplete(task.id) else if(distance< -100f)onSkip()
                    },onDragCancel={dragging=false;drag=0f},onHorizontalDrag={change,amount->change.consume();drag+=amount})
                },25.dp) {
                Rings(Modifier.matchParentSize().graphicsLayer {scaleX=.93f;scaleY=.93f;transformOrigin=TransformOrigin(1f,0f)},true)
                Copy("✧",19f,c.primary.copy(alpha=.64f),modifier=Modifier.align(Alignment.TopEnd).padding(top=88.dp,end=123.dp))
                Column(Modifier.fillMaxWidth().padding(start=19.dp,end=19.dp,top=19.dp,bottom=14.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Tag(task.area,Modifier.weight(1f,fill=false));Spacer(Modifier.width(8.dp))
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            Glyph(R.drawable.ic_bolt,13.dp);Copy(stringResource(R.string.next_action),if(narrow)8f else 9.5f,c.muted,700)
                        }
                    }
                    Column(Modifier.fillMaxWidth().heightIn(min=if(compact)116.dp else 147.dp),verticalArrangement=Arrangement.Center) {
                        Eyebrow(stringResource(R.string.one_at_a_time),size=8.5f);Spacer(Modifier.height(8.dp))
                        Copy(task.title,if(compact||narrow)19f else 21f,weight=700,line=if(compact||narrow)23.37f else 25.83f,
                            tracking=if(compact||narrow)-.817f else -.903f,modifier=Modifier.widthIn(max=295.dp).testTag("focused-task-title"))
                        if(projectTitle!=null){Spacer(Modifier.height(8.dp));ProjectStepPill(projectTitle)}
                        if(!task.dueDate.isNullOrBlank()){Spacer(Modifier.height(8.dp));TaskDeadlinePill(task.dueDate)}
                        Spacer(Modifier.height(8.dp));Copy(task.hint.ifBlank {stringResource(R.string.small_start)},11f,c.muted,line=17f,modifier=Modifier.widthIn(max=270.dp))
                    }
                    HorizontalDivider(color=c.border)
                    Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        QuietAction(stringResource(R.string.another),R.drawable.ic_arrowleft,c.muted2,onClick=skip)
                        QuietAction(stringResource(R.string.done),R.drawable.ic_arrowright,c.good,trailing=true) {complete(task.id)}
                    }
                }
                if(kotlin.math.abs(drag/density.density)>40f) Eyebrow(if(drag>0)"СДЕЛАНО ✓" else "ДРУГОЕ →",if(drag>0)c.good else c.primary)
            }
        }
    }
}

@Composable private fun TaskListRow(task: ActionTask,projectTitle: String?,compact: Boolean,modifier: Modifier,complete: (Int)->Unit,edit: ()->Unit,embedded: Boolean=false) {
    val c=LocalSbrosColors.current
    TaskRowSurface(modifier.fillMaxWidth().testTag("remaining-task-${task.id}").clickable(onClickLabel="Редактировать дело",onClick=edit),17.dp,embedded) {
        Row(Modifier.fillMaxWidth().heightIn(min=if(compact)52.dp else 59.dp).padding(start=2.dp,end=7.dp,top=if(compact)3.dp else 5.dp,bottom=if(compact)3.dp else 5.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).testTag("complete-task-${task.id}").semantics {contentDescription="Выполнить ${task.title}"}
                .clickable(role=Role.Checkbox,onClick={complete(task.id)}),contentAlignment=Alignment.Center) {
                Box(Modifier.size(21.dp).border(1.5.dp,lerp(c.border,c.primary,.48f),CircleShape))
            }
            Row(Modifier.weight(1f).heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).heightIn(min=44.dp),verticalArrangement=Arrangement.Center) {
                    Copy(task.title,11.5f,weight=700,line=14.6f,maxLines=1)
                    Spacer(Modifier.height(3.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Copy(task.area,9.5f,c.muted,modifier=Modifier.weight(1f),maxLines=1)
                        if(!task.dueDate.isNullOrBlank())TaskDeadlinePill(task.dueDate,compact=true)
                    }
                    if(projectTitle!=null){Spacer(Modifier.height(4.dp));ProjectStepPill(projectTitle,compact=true)}
                }
                Box(Modifier.width(32.dp).height(44.dp),contentAlignment=Alignment.Center){Glyph(R.drawable.ic_chevron,16.dp,c.muted)}
            }
        }
    }
}

@Composable private fun Tag(label: String,modifier: Modifier=Modifier) {
    val c=LocalSbrosColors.current
    Copy(label,10f,c.muted2,700,modifier.background(c.soft,RoundedCornerShape(13.dp)).padding(horizontal=10.dp,vertical=6.dp),maxLines=1)
}
