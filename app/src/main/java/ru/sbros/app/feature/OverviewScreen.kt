package ru.sbros.app.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*

@Composable fun OverviewScreen(state: DemoState, settings: ()->Unit, open: (String)->Unit) {
    val c=LocalSbrosColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=if(maxWidth<350.dp)15.dp else 20.dp).padding(top=ContentTopPadding,bottom=24.dp)) {
            Heading(stringResource(R.string.overview),stringResource(R.string.overview_sub)) {IconAction(R.drawable.ic_sliders,stringResource(R.string.settings),settings)}
            Spacer(Modifier.height(contentHeadingGap()))
            Paper(Modifier.fillMaxWidth().clickable {open("inbox")},25.dp,true) {
                Rings(Modifier.matchParentSize(),kind="overview")
                Row(Modifier.padding(horizontal=19.dp,vertical=21.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow(stringResource(R.string.inbox_label));Spacer(Modifier.height(12.dp))
                        Copy(if(state.inbox.isEmpty())"Можно выдохнуть." else stringResource(R.string.inbox_title),20f,weight=750,line=24.4f,tracking=-.9f)
                        Spacer(Modifier.height(8.dp));Copy(if(state.inbox.isEmpty())"Сейчас все записи на своих местах." else "${state.inbox.size} мысль ждёт тебя. Не обязательно прямо сейчас.",10.5f,c.muted2,line=16.8f)
                        Spacer(Modifier.height(10.dp));QuietAction(if(state.inbox.isEmpty())"Всё разобрано" else stringResource(R.string.open_inbox),R.drawable.ic_arrowright,trailing=true) {open("inbox")}
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(46.dp).background(c.card,RoundedCornerShape(17.dp)),contentAlignment=Alignment.Center) {
                        Glyph(R.drawable.ic_inbox,24.dp)
                        if(state.inbox.isNotEmpty())Box(Modifier.align(Alignment.TopEnd).offset(x=6.dp,y=(-5).dp).size(18.dp).background(c.primary,CircleShape),contentAlignment=Alignment.Center){Copy("${state.inbox.size}",9f,c.onPrimary,700)}
                    }
                }
            }
            Spacer(Modifier.height(25.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Eyebrow(stringResource(R.string.your_system),c.muted);Copy(stringResource(R.string.everything_else),9.5f,c.muted,650)}
            Spacer(Modifier.height(13.dp))
            val names=listOf(R.string.projects,R.string.waiting,R.string.someday,R.string.materials)
            val keys=listOf("projects","waiting","someday","notes");val icons=listOf(R.drawable.ic_folder,R.drawable.ic_clock,R.drawable.ic_bulb,R.drawable.ic_note)
            val counts=listOf(state.projects.size,state.tasks.count {it.status=="waiting"},state.tasks.count {it.status=="someday"},state.materials.size)
            repeat(2) {row -> Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                repeat(2) { col -> val n=row*2+col
                    Paper(Modifier.weight(1f).heightIn(min=124.dp).testTag("overview-${keys[n]}").clickable {open(keys[n])},21.dp) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Box(Modifier.size(32.dp).background(c.soft,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {Glyph(icons[n],17.dp)}
                                Copy("${counts[n]}",13f,c.primary,800)
                            }
                            Spacer(Modifier.height(18.dp));Copy(stringResource(names[n]),13f,weight=760,tracking=-.32f)
                        }
                        Box(Modifier.align(Alignment.BottomEnd).padding(end=13.dp,bottom=11.dp)) {Glyph(R.drawable.ic_arrowright,15.dp,c.muted.copy(alpha=.7f))}
                    }
                }
            }; if(row==0)Spacer(Modifier.height(10.dp)) }
            Spacer(Modifier.height(14.dp))
            Paper(Modifier.fillMaxWidth(),21.dp) {
                Column(Modifier.padding(horizontal=13.dp,vertical=3.dp)) {
                    QuietRow(stringResource(R.string.all_actions),R.drawable.ic_bolt,state.tasks.count {it.status=="next"}) {open("next")}
                    androidx.compose.material3.HorizontalDivider(color=c.border)
                    QuietRow(stringResource(R.string.completed),R.drawable.ic_check,state.tasks.count {it.status=="done"}) {open("done")}
                }
            }
            Spacer(Modifier.height(18.dp))
            Paper(Modifier.fillMaxWidth(),21.dp,true) {
                Row(Modifier.padding(horizontal=14.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
                    Copy("✳",29f,c.primary)
                    Column(Modifier.weight(1f)) {Eyebrow(stringResource(R.string.task_check_label),size=8f);Spacer(Modifier.height(5.dp));Copy(stringResource(R.string.task_check_title),13f,weight=700);Spacer(Modifier.height(5.dp));Copy(stringResource(R.string.task_check_sub),10f,c.muted2)}
                }
            }
            QuietAction(stringResource(R.string.capture_history),R.drawable.ic_clock,c.muted2) {open("history")}
        }
    }
}
@Composable private fun QuietRow(label: String, icon: Int, count: Int, click: ()->Unit) {
    val c=LocalSbrosColors.current
    Row(Modifier.fillMaxWidth().heightIn(min=54.dp).clickable(onClick=click),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp).background(c.soft,RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Glyph(icon,16.dp)}
        Copy(label,12f,weight=700,modifier=Modifier.weight(1f));Copy("$count",11f,c.muted);Glyph(R.drawable.ic_chevron,15.dp,c.muted)
    }
}
