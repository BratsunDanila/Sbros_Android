package ru.sbros.app.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*

@Composable internal fun ProjectStepPill(title: String,compact: Boolean=false) {
    val c=LocalSbrosColors.current
    Row(Modifier.widthIn(max=280.dp).background(c.soft,RoundedCornerShape(10.dp)).padding(horizontal=8.dp,vertical=if(compact)3.dp else 5.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Glyph(R.drawable.ic_folder,12.dp)
        Copy("Шаг проекта · $title",if(compact)9f else 10f,c.primary,650,modifier=Modifier.weight(1f,fill=false),maxLines=1)
    }
}
