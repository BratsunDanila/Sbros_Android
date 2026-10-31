package ru.sbros.app.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ru.sbros.app.R
import ru.sbros.app.core.designsystem.*
import ru.sbros.app.data.*
import ru.sbros.app.data.ai.AiState
import ru.sbros.app.data.auth.AuthState

@Composable fun AppSettingsScreen(appearance: Appearance,change: (Appearance)->Unit,auth: AuthState,workspace: String,syncMessage: String,conflicts: Int,ai: AiState,hasLegacy: Boolean,
    login: ()->Unit,register: ()->Unit,logout: ()->Unit,sync: ()->Unit,importLocal: ()->Unit,switchWorkspace: ()->Unit,openConflicts: ()->Unit,openVersions: ()->Unit,openAi: ()->Unit,
    examples: ()->Unit,leaveExamples: ()->Unit,legacy: ()->Unit,resetDemo: ()->Unit,close: ()->Unit) {
    val c=LocalSbrosColors.current
    var page by rememberSaveable {mutableStateOf("")}
    var confirmReset by remember {mutableStateOf(false)}
    BackHandler(enabled=page.isNotEmpty()) {page=""}
    key(page) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val narrow=maxWidth<350.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=18.dp,bottom=20.dp)) {
                QuietAction(if(page.isEmpty())"Назад" else "Настройки",R.drawable.ic_left,c.muted2) {if(page.isEmpty())close() else page=""}
                Spacer(Modifier.height(12.dp))
                SettingsTitle(when(page){"account"->"Аккаунт";"extra"->"Дополнительно";else->"Настройки"},if(page=="extra")32f else if(narrow)40f else 43f)
                Spacer(Modifier.height(22.dp))
                when(page) {
                    "account" -> {
                        Paper(Modifier.fillMaxWidth(),23.dp) {
                            Column(Modifier.padding(18.dp)) {
                                Copy(auth.user?.name?.ifBlank {"Твой аккаунт"}?:"Без аккаунта",20f,weight=750,tracking=-.7f)
                                Spacer(Modifier.height(6.dp))
                                if(auth.user!=null) {
                                    Copy(auth.user.email,11f,c.muted2)
                                    Spacer(Modifier.height(10.dp))
                                    Copy(if(auth.needsLogin)"Войди снова, чтобы продолжить синхронизацию." else syncMessage,11f,c.muted2,line=18f)
                                } else Copy("Записи сохраняются на устройстве. Аккаунт нужен для синхронизации.",12f,c.muted2,line=20f)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        if(auth.user==null) {
                            ActionButton("Войти",login,Modifier.fillMaxWidth())
                            Spacer(Modifier.height(9.dp));ActionButton("Создать аккаунт",register,Modifier.fillMaxWidth(),primary=false)
                        } else {
                            if(auth.needsLogin){ActionButton("Войти снова",login,Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp))}
                            Paper(Modifier.fillMaxWidth(),23.dp) {
                                Column(Modifier.padding(horizontal=15.dp)) {
                                    SettingsRow("Синхронизировать",R.drawable.ic_review,sync)
                                    SettingsDivider()
                                    SettingsRow("Добавить записи в облако",R.drawable.ic_layers,importLocal)
                                    SettingsDivider()
                                    SettingsRow(if(workspace.startsWith("user:"))"Локальные записи" else "Записи аккаунта",R.drawable.ic_folder,switchWorkspace)
                                    if(conflicts>0){SettingsDivider();SettingsRow("Согласовать изменения ($conflicts)",R.drawable.ic_edit,openConflicts)}
                                    SettingsDivider()
                                    SettingsRow("Сохранённые версии",R.drawable.ic_layers,openVersions)
                                }
                            }
                            Spacer(Modifier.height(12.dp));QuietAction("Выйти из аккаунта",R.drawable.ic_left,c.muted2,onClick=logout)
                        }
                    }
                    "extra" -> {
                        Paper(Modifier.fillMaxWidth(),23.dp) {
                            Column(Modifier.padding(horizontal=15.dp)) {
                                if(workspace in listOf("demo","legacy"))SettingsRow("Вернуться к своим записям",R.drawable.ic_left,leaveExamples)
                                else SettingsRow("Посмотреть примеры",R.drawable.ic_book,examples)
                                if(workspace=="demo"){SettingsDivider();SettingsRow("Вернуть исходные примеры",R.drawable.ic_review){confirmReset=true}}
                                if(hasLegacy&&workspace!="legacy"){SettingsDivider();SettingsRow("Записи прежней версии",R.drawable.ic_layers,legacy)}
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        Copy("Удаление приложения удаляет локальные записи. Записи аккаунта можно восстановить после синхронизации.",11f,c.muted2,line=19f)
                    }
                    else -> {
                        Paper(Modifier.fillMaxWidth(),23.dp) {
                            Column(Modifier.padding(horizontal=15.dp)) {
                                SettingsRow("Аккаунт",R.drawable.ic_folder,{page="account"},when {
                                    auth.needsLogin->"Нужно войти снова"
                                    conflicts>0->"Есть изменения для согласования · $conflicts"
                                    auth.user!=null->auth.user.name.ifBlank {auth.user.email}
                                    else->"На устройстве · вход и регистрация"
                                })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Paper(Modifier.fillMaxWidth(),23.dp) {
                            Column(Modifier.padding(horizontal=15.dp,vertical=7.dp)) {
                                Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Copy("Палитра",12f,weight=650,modifier=Modifier.weight(1f))
                                    Row {
                                        listOf("lavender" to "Лаванда","sage" to "Шалфей","clay" to "Глина","ocean" to "Океан").forEach {(name,label)->
                                            Box(Modifier.size(40.dp,48.dp).clickable {change(appearance.copy(palette=name))}.semantics {contentDescription="Палитра $label";selected=name==appearance.palette},contentAlignment=Alignment.Center) {
                                                Box(Modifier.size(36.dp).border(if(name==appearance.palette)1.5.dp else 0.dp,if(name==appearance.palette)c.primary else c.card,CircleShape),contentAlignment=Alignment.Center) {
                                                    Box(Modifier.size(28.dp).background(palette(name,false).primary,CircleShape),contentAlignment=Alignment.Center) {
                                                        if(name==appearance.palette)Glyph(R.drawable.ic_check,15.dp,androidx.compose.ui.graphics.Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                SettingsDivider()
                                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(appearance.dark,role=Role.Switch,onValueChange={change(appearance.copy(dark=it))}),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                                    Glyph(if(appearance.dark)R.drawable.ic_moon else R.drawable.ic_sun,18.dp)
                                    Copy("Тёмная тема",12f,weight=650,modifier=Modifier.weight(1f))
                                    Switch(checked=appearance.dark,onCheckedChange=null,colors=SwitchDefaults.colors(
                                        checkedThumbColor=c.card,checkedTrackColor=c.primary,checkedBorderColor=c.primary,
                                        uncheckedThumbColor=c.muted,uncheckedTrackColor=c.soft,uncheckedBorderColor=c.border))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Paper(Modifier.fillMaxWidth(),23.dp) {
                            Column(Modifier.padding(horizontal=15.dp)) {
                                SettingsRow("ИИ · Token Harbor",R.drawable.ic_spark,openAi,if(ai.verified)"Подключение проверено" else if(ai.hasKey)"Личный ключ сохранён" else "Добавить личный ключ")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SettingsRow("Дополнительно",R.drawable.ic_layers) {page="extra"}
                    }
                }
            }
        }
    }
    if(confirmReset)AlertDialog(onDismissRequest={confirmReset=false},title={Copy("Вернуть примеры?",22f,weight=750)},text={Copy("Изменятся только записи демонстрации. Личные записи останутся.")},confirmButton={TextButton(onClick={confirmReset=false;resetDemo()}){Text("Вернуть")}},dismissButton={TextButton(onClick={confirmReset=false}){Text("Отмена")}})
}

@Composable private fun SettingsTitle(title: String,size: Float) {
    val c=LocalSbrosColors.current
    Text(buildAnnotatedString {append(title);withStyle(SpanStyle(color=c.primary)){append(".")}},
        color=c.ink,fontFamily=Onest,fontWeight=FontWeight(760),fontSize=size.sp,lineHeight=(size*1.06f).sp,letterSpacing=(-size*.083f).sp,
        style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)))
}

@Composable private fun SettingsDivider() {HorizontalDivider(color=LocalSbrosColors.current.border,thickness=1.dp)}

@Composable private fun SettingsRow(label: String,icon: Int,onClick: ()->Unit) {SettingsRow(label,icon,onClick,null)}

@Composable private fun SettingsRow(label: String,icon: Int,onClick: ()->Unit,subtitle: String?) {
    val c=LocalSbrosColors.current
    Row(Modifier.fillMaxWidth().heightIn(min=if(subtitle==null)48.dp else 62.dp).clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
        Glyph(icon,18.dp)
        Column(Modifier.weight(1f)) {
            Copy(label,12f,weight=650)
            if(subtitle!=null){Spacer(Modifier.height(3.dp));Copy(subtitle,10.5f,c.muted2,line=16f,maxLines=1)}
        }
        Glyph(R.drawable.ic_chevron,14.dp,c.muted)
    }
}
