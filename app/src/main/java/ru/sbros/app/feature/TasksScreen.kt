package ru.sbros.app.feature

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import android.os.SystemClock
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
    help: ()->Unit, select: (Int)->Unit, skip: ()->Unit, edit: (Int)->Unit = {},
    swipeComplete: suspend (Int)->Boolean={complete(it);true},swipeSkip: suspend (Int)->Boolean={skip();true}) {
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
                TaskDeck(task,state.projects.find {it.id==task?.projectId}?.title,compact,narrow,all.size>1,complete,skip,edit,swipeComplete,swipeSkip)
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

@Composable private fun TaskDeck(task: ActionTask?,projectTitle: String?,compact: Boolean,narrow: Boolean,canSkip: Boolean,
    complete: (Int)->Unit,skip: ()->Unit,edit: (Int)->Unit,swipeComplete: suspend (Int)->Boolean,swipeSkip: suspend (Int)->Boolean) {
    val c=LocalSbrosColors.current
    // Keep action coroutines alive when the focused task changes to the next card.
    val scope=rememberCoroutineScope()
    val currentTaskId by rememberUpdatedState(task?.id)
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
            var animating by remember {mutableStateOf(false)}
            var busy by remember {mutableStateOf(false)}
            var animationJob by remember {mutableStateOf<Job?>(null)}
            val density=LocalDensity.current
            val haptic=LocalHapticFeedback.current
            var width by remember {mutableFloatStateOf(with(density){300.dp.toPx()})}
            val movement=remember {Animatable(0f)}
            val appearance=remember {Animatable(.65f)}
            LaunchedEffect(task.id){appearance.animateTo(1f,tween(130))}
            val translation=if(animating)movement.value else drag
            val onComplete by rememberUpdatedState(complete)
            val onSkip by rememberUpdatedState(skip)
            val onSwipeComplete by rememberUpdatedState(swipeComplete)
            val onSwipeSkip by rememberUpdatedState(swipeSkip)
            val currentCanSkip by rememberUpdatedState(canSkip)
            val threshold=width*.24f
            val progress=(abs(translation)/threshold).coerceIn(0f,1f)
            val swipeColor=if(translation>=0)c.good else c.primary
            val tintProgress=if(translation<0&&!canSkip)0f else progress
            fun settle(direction: Int=0) {
                val start=if(animating)movement.value else drag
                animationJob?.cancel()
                busy=direction!=0
                animationJob=scope.launch {
                    movement.snapTo(start);animating=true
                    if(direction!=0) {
                        movement.animateTo(direction*width*1.2f,tween(160,easing=FastOutLinearInEasing))
                        if(currentTaskId!=task.id)return@launch
                        val applied=if(direction>0)onSwipeComplete(task.id)else onSwipeSkip(task.id)
                        if(applied)return@launch
                    }
                    movement.animateTo(0f,spring(dampingRatio=1f,stiffness=600f))
                    drag=0f;animating=false;busy=false
                }
            }
            Paper(Modifier.fillMaxWidth().testTag("focused-task-card").heightIn(min=if(compact)222.dp else 253.dp)
                .onSizeChanged {if(it.width>0)width=it.width.toFloat()}
                // Listen outside the moving layer so pointer coordinates stay stable.
                .pointerInput(task.id) {
                    val velocity=VelocityTracker()
                    var lastPosition=Offset.Zero
                    var thresholdFeedback=false
                    detectHorizontalDragGestures(onDragStart={
                        if(!busy){
                            val start=if(animating)movement.value else drag
                            animationJob?.cancel();drag=start;animating=false
                            velocity.resetTracking();thresholdFeedback=false
                        }
                    },onDragEnd={
                        if(!busy) {
                            velocity.addPosition(SystemClock.uptimeMillis(),lastPosition)
                            val speed=velocity.calculateVelocity().x
                            val fastSpeed=with(density){650.dp.toPx()}
                            val intentionalFling=abs(drag)>=with(density){24.dp.toPx()}&&abs(speed)>=fastSpeed&&drag*speed>0
                            val reversing=abs(speed)>=fastSpeed&&drag*speed<0
                            val commit=(abs(drag)>=width*.24f&&!reversing)||intentionalFling
                            val direction=if(commit&&(drag>0||currentCanSkip))sign(drag).toInt()else 0
                            if(direction!=0&&!thresholdFeedback)haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            settle(direction)
                        }
                    },onDragCancel={if(!busy)settle()},onHorizontalDrag={change,amount->
                        if(!busy) {
                            change.consume();drag+=amount
                            lastPosition=change.position
                            velocity.addPosition(change.uptimeMillis,change.position)
                            if(!thresholdFeedback&&abs(drag)>=width*.24f&&(drag>0||currentCanSkip)) {
                                thresholdFeedback=true;haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    })
                }
                .graphicsLayer {
                    translationX=translation;rotationZ=(translation/width*6f).coerceIn(-2f,2f)
                    val departure=((abs(translation)-threshold)/(width-threshold)).coerceIn(0f,1f)
                    alpha=appearance.value*(1f-departure*.45f)
                }
                .clickable(enabled=!busy,interactionSource=remember {MutableInteractionSource()},indication=null,onClickLabel="Редактировать дело",onClick={edit(task.id)}),25.dp) {
                Box(Modifier.matchParentSize().background(swipeColor.copy(alpha=tintProgress*.075f))
                    .border((1f+tintProgress*.5f).dp,lerp(c.border,swipeColor,tintProgress*.75f),RoundedCornerShape(25.dp)))
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
                        QuietAction(stringResource(R.string.another),R.drawable.ic_arrowleft,c.muted2){if(!busy)onSkip()}
                        QuietAction(stringResource(R.string.done),R.drawable.ic_arrowright,c.good,trailing=true) {if(!busy)onComplete(task.id)}
                    }
                }
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
