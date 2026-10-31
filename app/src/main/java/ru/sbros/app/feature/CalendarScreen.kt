package ru.sbros.app.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru=Locale.forLanguageTag("ru")
@Composable fun CalendarScreen(state: DemoState, settings: ()->Unit, event: (DemoEvent?,String)->Unit,editTask: (Int)->Unit={},completeTask: (Int)->Unit={}) {
    val c=LocalSbrosColors.current
    var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }; val month=YearMonth.parse(monthString)
    var selected by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=if(maxWidth<350.dp)15.dp else 20.dp).padding(top=ContentTopPadding,bottom=24.dp)) {
            Heading(stringResource(R.string.calendar),stringResource(R.string.calendar_sub),true) {IconAction(R.drawable.ic_sliders,stringResource(R.string.settings),settings)}
            Spacer(Modifier.height(contentHeadingGap())); SoftHero(stringResource(R.string.calendar_hero_label),stringResource(R.string.calendar_hero_title),stringResource(R.string.calendar_hero_sub),calendar=true)
            Spacer(Modifier.height(13.dp))
            Paper(Modifier.fillMaxWidth(),24.dp) {
                Column(Modifier.padding(start=12.dp,end=12.dp,top=9.dp,bottom=11.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=3.dp),verticalAlignment=Alignment.CenterVertically) {
                        Copy(month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy 'г.'",ru)).replaceFirstChar {it.uppercase(ru)},13f,weight=760,modifier=Modifier.weight(1f))
                        MonthArrow(R.drawable.ic_left,"Предыдущий месяц") {monthString=month.minusMonths(1).toString();selected=month.minusMonths(1).atDay(1).toString()}
                        MonthArrow(R.drawable.ic_chevron,"Следующий месяц") {monthString=month.plusMonths(1).toString();selected=month.plusMonths(1).atDay(1).toString()}
                    }
                    Spacer(Modifier.height(3.dp))
                    Row { listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach {Box(Modifier.weight(1f).height(27.dp),contentAlignment=Alignment.Center){Copy(it,10f,c.muted,700)}} }
                    val first=month.atDay(1).dayOfWeek.value-1
                    val rows=(first+month.lengthOfMonth()+6)/7
                    repeat(rows) {row ->
                        Row(Modifier.fillMaxWidth()) { repeat(7) {col -> val day=row*7+col-first+1
                            Box(Modifier.weight(1f).height(37.dp),contentAlignment=Alignment.Center) {
                                if(day in 1..month.lengthOfMonth()) { val date=month.atDay(day).toString(); val active=date==selected
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(14.dp)).background(if(active)c.primary else androidx.compose.ui.graphics.Color.Transparent).clickable {selected=date}.semantics {contentDescription=displayDate(date);this.selected=active},contentAlignment=Alignment.Center) {
                                        Copy("$day",11f,if(active)c.onPrimary else c.ink,if(active)700 else 400)
                                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom=2.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                                            if(state.events.any {it.date==date})Box(Modifier.size(4.dp).background(if(active)c.onPrimary else c.primary,CircleShape))
                                            if(state.tasks.any {it.dueDate==date})Box(Modifier.size(4.dp).background(if(active)c.onPrimary else c.primary,RoundedCornerShape(1.dp)))
                                        }
                                    }
                                }
                            }
                        } }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) { Eyebrow(stringResource(R.string.selected_day),c.muted); QuietAction(stringResource(R.string.add),R.drawable.ic_plus) {event(null,selected)} }
            Copy(displayDate(selected),18f,weight=750,tracking=-.72f)
            Spacer(Modifier.height(9.dp))
            val chosen=state.events.filter {it.date==selected}
            val dueTasks=state.tasks.filter {it.dueDate==selected}.sortedBy {it.status=="done"}
            if(chosen.isEmpty()&&dueTasks.isEmpty()) Paper(Modifier.fillMaxWidth(),20.dp) {
                Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(39.dp).background(c.soft,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {Glyph(R.drawable.ic_spark,16.dp)}
                    Column {Copy(stringResource(R.string.free_day),12f,weight=700);Spacer(Modifier.height(4.dp));Copy(stringResource(R.string.free_day_sub),10.5f,c.muted2,line=16.3f)}
                }
            }
            if(chosen.isNotEmpty()){Eyebrow("СОБЫТИЯ",c.muted2,9f);Spacer(Modifier.height(9.dp))}
            chosen.forEach {e -> Paper(Modifier.fillMaxWidth().clickable {event(e,selected)},17.dp) {Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {Copy(e.time.ifBlank {"Весь день"},10f,c.primary,modifier=Modifier.background(c.accent,RoundedCornerShape(10.dp)).padding(8.dp));Spacer(Modifier.width(9.dp));Copy(e.title,12f,weight=700,modifier=Modifier.weight(1f));Glyph(R.drawable.ic_chevron,16.dp,c.muted)}};Spacer(Modifier.height(7.dp)) }
            if(dueTasks.isNotEmpty()) {
                Spacer(Modifier.height(if(chosen.isEmpty())2.dp else 12.dp));Eyebrow("ДЕЛА СО СРОКОМ",c.muted2,9f);Spacer(Modifier.height(9.dp))
                dueTasks.forEach {task->
                    ManagementTaskRow(task,state,completeTask,showDeadline=false) {editTask(task.id)}
                    Spacer(Modifier.height(7.dp))
                }
            }
        }
    }
}
@Composable private fun MonthArrow(icon: Int,label: String,click: ()->Unit) { Box(Modifier.size(36.dp,40.dp).clickable(onClick=click).semantics {contentDescription=label},contentAlignment=Alignment.Center) {Glyph(icon,16.dp)} }
