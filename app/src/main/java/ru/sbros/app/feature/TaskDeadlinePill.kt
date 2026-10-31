package ru.sbros.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import java.time.LocalDate

@Composable internal fun TaskDeadlinePill(date: String?,compact: Boolean=false) {
    val deadline=date?.let {runCatching {LocalDate.parse(it)}.getOrNull()}?:return
    val c=LocalSbrosColors.current
    val label="До "+displayDate(deadline.toString())
    Row(Modifier.background(c.soft,RoundedCornerShape(10.dp)).padding(horizontal=if(compact)7.dp else 9.dp,vertical=if(compact)3.dp else 5.dp),
        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        if(!compact)Glyph(R.drawable.ic_calendar_nav,12.dp)
        Copy(label,if(compact)9f else 10f,c.primary,650,maxLines=1)
    }
}
