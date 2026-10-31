package ru.sbros.app.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*

@Composable fun ThoughtsScreen(state: DemoState, open: (Int)->Unit) {
    val c=LocalSbrosColors.current
    var filter by rememberSaveable { mutableIntStateOf(0) }; var search by rememberSaveable { mutableStateOf(false) }; var query by rememberSaveable { mutableStateOf("") }
    val list=state.thoughts.filter { (filter==0||filter==1&&it.idea||filter==2&&!it.idea||filter==3&&it.favorite)&&(it.title+" "+it.body).contains(query,true) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding=if(maxWidth<350.dp)15.dp else 20.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=padding).padding(top=ContentTopPadding,bottom=18.dp)) {
            Heading(stringResource(R.string.thoughts),stringResource(R.string.thoughts_sub)) {
                IconAction(if(search)R.drawable.ic_x else R.drawable.ic_search,stringResource(R.string.search)) {search=!search;if(!search)query=""}
            }
            Spacer(Modifier.height(contentHeadingGap()))
            SoftHero(stringResource(R.string.thought_hero_label),stringResource(R.string.thought_hero_title),stringResource(R.string.thought_hero_sub))
            if(search) { Spacer(Modifier.height(16.dp)); Paper(Modifier.fillMaxWidth(),14.dp) {
                Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Glyph(R.drawable.ic_search,16.dp); Spacer(Modifier.width(9.dp)); BasicTextField(query,{query=it},Modifier.fillMaxWidth().semantics {contentDescription="Поиск мыслей"},textStyle=TextStyle(fontFamily=Onest,fontSize=12.sp,color=c.ink),singleLine=true,decorationBox={inner -> Box {if(query.isEmpty())Copy(stringResource(R.string.find_words),color=c.muted);inner()} }) }
            } }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf(R.string.all,R.string.ideas,R.string.diary,R.string.favorites).forEachIndexed { n,id -> Pill(stringResource(id),filter==n,{filter=n},solid=true) }
            }
            Spacer(Modifier.height(18.dp))
            if(list.isEmpty()) Copy(stringResource(R.string.empty_search),color=c.muted2,modifier=Modifier.padding(vertical=30.dp))
            var prior=-1
            list.forEach { thought ->
                if(prior!=thought.daysAgo) { if(prior!=-1)Spacer(Modifier.height(14.dp)); Eyebrow(stringResource(if(thought.daysAgo==0)R.string.today else if(thought.daysAgo==1)R.string.yesterday else R.string.this_week),c.muted,10f); Spacer(Modifier.height(11.dp)); prior=thought.daysAgo }
                Paper(Modifier.fillMaxWidth().clickable {open(thought.id)},22.dp) {
                    Column(Modifier.padding(start=16.dp,end=16.dp,top=14.dp,bottom=12.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Glyph(if(thought.idea)R.drawable.ic_bulb else R.drawable.ic_feather,14.dp); Spacer(Modifier.width(6.dp)); Copy(if(thought.idea)"Идея" else "Запись",10f,c.primary,700,Modifier.weight(1f)); Copy(thought.time,10f,c.muted) }
                        Spacer(Modifier.height(13.dp)); Copy(thought.title,16f,weight=750,line=20f,tracking=-.56f)
                        Spacer(Modifier.height(7.dp)); Copy(thought.body,11.6f,c.muted2,line=18.56f,maxLines=3)
                        Spacer(Modifier.height(15.dp)); androidx.compose.material3.HorizontalDivider(color=c.border)
                        Row(Modifier.fillMaxWidth().padding(top=11.dp),horizontalArrangement=Arrangement.SpaceBetween) { Copy(if(thought.favorite)"В избранном" else stringResource(R.string.open_entry),10f,c.muted); Glyph(if(thought.favorite)R.drawable.ic_heart else R.drawable.ic_arrowright,16.dp) }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}
