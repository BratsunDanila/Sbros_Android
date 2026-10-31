package ru.sbros.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import ru.sbros.app.R

@Composable fun Copy(text: String, size: Float = 12f, color: Color = LocalSbrosColors.current.ink,
    weight: Int = 400, modifier: Modifier = Modifier, line: Float = size*1.5f,
    tracking: Float = 0f, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(text,modifier,color,fontSize=size.sp,fontFamily=Onest,fontWeight=FontWeight(weight),
        letterSpacing=tracking.sp,lineHeight=line.sp,textAlign=align,maxLines=maxLines,overflow=TextOverflow.Ellipsis,
        style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)))
}
@Composable fun Glyph(@DrawableRes icon: Int, size: Dp=20.dp, color: Color=LocalSbrosColors.current.primary, description: String?=null) {
    Icon(painterResource(icon),description,Modifier.size(size),tint=color)
}
@Composable fun IconAction(icon: Int, description: String, onClick: ()->Unit) {
    val c=LocalSbrosColors.current
    Box(Modifier.size(48.dp).semantics { contentDescription=description }.clickable(onClick=onClick),contentAlignment=Alignment.Center) {
        Box(Modifier.size(39.dp).background(c.card,RoundedCornerShape(15.dp)).border(1.dp,c.border,RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center) { Glyph(icon,19.dp,c.muted2) }
    }
}
val ContentTopPadding=18.dp
@Composable fun contentHeadingGap(): Dp = if(LocalConfiguration.current.screenHeightDp<=740)15.dp else 20.dp

@Composable fun Heading(title: String, subtitle: String, calendar: Boolean=false, action: @Composable ()->Unit) {
    val c=LocalSbrosColors.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow=maxWidth<315.dp
        Column {
            val size=if(narrow) {if(calendar)36f else 40f} else 43f
            Text(buildAnnotatedString { append(title); withStyle(SpanStyle(color=c.primary)) { append(".") } },modifier=Modifier.testTag("screen-title-$title"),fontFamily=Onest,fontWeight=FontWeight(760),fontSize=size.sp,letterSpacing=(-size*.083f).sp,lineHeight=(size*1.06f).sp,color=c.ink,style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)))
            Spacer(Modifier.height(8.dp)); Copy(subtitle,color=c.muted2,line=18.6f,modifier=Modifier.heightIn(min=19.dp))
        }
        Box(Modifier.align(Alignment.TopEnd).offset(x=4.dp,y=(-4).dp)) { action() }
    }
}
@Composable fun Eyebrow(text: String, color: Color=LocalSbrosColors.current.primary, size: Float=9f) {
    Copy(text,size,color,800,line=size*1.4f,tracking=size*.13f)
}
@Composable fun Paper(modifier: Modifier=Modifier, radius: Dp=21.dp, gradient: Boolean=false, content: @Composable BoxScope.()->Unit) {
    val c=LocalSbrosColors.current
    Box(modifier.clip(RoundedCornerShape(radius)).background(if(gradient) Brush.linearGradient(listOf(c.accent,c.soft)) else Brush.linearGradient(listOf(c.card,c.card))).border(1.dp,c.border,RoundedCornerShape(radius)),content=content)
}
@Composable fun ActionButton(label: String, onClick: ()->Unit, modifier: Modifier=Modifier, primary: Boolean=true, enabled: Boolean=true, icon: Int?=null, minHeight: Dp=49.dp,labelSize: Float=12f) {
    val c=LocalSbrosColors.current
    val interaction=remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .97f else 1f,tween(180),label="button press")
    Row(modifier.heightIn(min=minHeight).scale(scale).alpha(if(enabled) 1f else .38f).clip(RoundedCornerShape(16.dp))
        .background(if(primary)c.primary else c.card).then(if(primary)Modifier else Modifier.border(1.dp,c.border,RoundedCornerShape(16.dp)))
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,onClick=onClick).padding(horizontal=13.dp,vertical=12.dp),
        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        Copy(label,labelSize,if(primary)c.onPrimary else c.muted2,750)
        if(icon!=null) { Spacer(Modifier.width(9.dp)); Glyph(icon,16.dp,if(primary)c.onPrimary else c.primary) }
    }
}
@Composable fun QuietAction(label: String, icon: Int?=null, color: Color=LocalSbrosColors.current.primary, trailing: Boolean=false, onClick: ()->Unit) {
    Row(Modifier.heightIn(min=40.dp).clickable(onClick=onClick).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        if(icon!=null&&!trailing) Glyph(icon,15.dp,color); Copy(label,11.5f,color,700);if(icon!=null&&trailing)Glyph(icon,15.dp,color)
    }
}
@Composable fun Pill(label: String, selected: Boolean, onClick: ()->Unit, modifier: Modifier=Modifier, solid: Boolean=false, icon: Int?=null) {
    val c=LocalSbrosColors.current
    val bg by animateColorAsState(if(selected) { if(solid)c.primary else c.accent } else c.card,tween(240),label="filter")
    val fg=if(selected&&solid)c.onPrimary else if(selected)c.primary else c.muted2
    Row(modifier.heightIn(min=40.dp).clip(RoundedCornerShape(if(solid)100.dp else 13.dp)).background(bg).border(1.dp,if(selected)bg else c.border,RoundedCornerShape(if(solid)100.dp else 13.dp)).clickable(onClick=onClick).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
        if(icon!=null) { Glyph(icon,15.dp,fg); Spacer(Modifier.width(6.dp)) }; Copy(label,11f,fg,650,maxLines=1)
    }
}
// Quiet vector geometry from the HTML. No raster mockups or embedded web content.
@Composable fun Rings(modifier: Modifier=Modifier, focus: Boolean=false, kind: String="thoughts") {
    val c=LocalSbrosColors.current
    Canvas(modifier) {
        val dimensions=when {focus->listOf(floatArrayOf(188f,-162f,-41f),floatArrayOf(152f,-144f,-23f));kind=="calendar"->listOf(floatArrayOf(190f,-138f,-55f),floatArrayOf(148f,-119f,-34f));kind=="overview"->listOf(floatArrayOf(206f,-123f,-61f),floatArrayOf(157f,-99f,-32f));else->listOf(floatArrayOf(227f,-146f,-40f),floatArrayOf(171f,-128f,1f))}
        dimensions.forEach {d -> val width=d[0].dp.toPx();val origin=Offset(size.width+d[1].dp.toPx(),d[2].dp.toPx());val center=origin+Offset(width/2,width/2)
            rotate(if(focus)30f else -28f,center) {drawRoundRect(c.primary.copy(alpha=if(focus).12f else .19f),origin,Size(width,width),CornerRadius(width*.47f,width*.47f),style=Stroke(1.dp.toPx()))}
        }
    }
}
@Composable fun SoftHero(label: String, title: String, subtitle: String, modifier: Modifier=Modifier, calendar: Boolean=false) {
    val c=LocalSbrosColors.current
    Paper(modifier.fillMaxWidth().heightIn(min=if(calendar)94.dp else 154.dp),if(calendar)24.dp else 26.dp,true) {
        Rings(Modifier.matchParentSize(),kind=if(calendar)"calendar" else "thoughts")
        Copy("✧",if(calendar)31f else 28f,c.primary.copy(alpha=.47f),modifier=Modifier.align(Alignment.BottomEnd).padding(end=25.dp,bottom=21.dp))
        if(!calendar) Copy("✳",21f,c.primary.copy(alpha=.45f),modifier=Modifier.align(Alignment.TopEnd).padding(top=38.dp,end=62.dp))
        Column(Modifier.padding(horizontal=if(calendar)17.dp else 20.dp,vertical=if(calendar)14.dp else 22.dp)) {
            Eyebrow(label)
            Spacer(Modifier.height(if(calendar)5.dp else 12.dp))
            Copy(title,if(calendar)16f else 20f,weight=750,line=if(calendar)19.5f else 23.6f,tracking=if(calendar)-.64f else -1.1f)
            Spacer(Modifier.height(if(calendar)5.dp else 11.dp))
            Copy(subtitle,10f,c.muted2,line=15.5f)
        }
    }
}
