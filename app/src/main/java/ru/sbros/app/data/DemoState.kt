package ru.sbros.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Context.settingsStore by preferencesDataStore("appearance")
data class Appearance(val palette: String = "lavender", val dark: Boolean = false, val welcomed: Boolean = false)
class SettingsRepository(val context: Context) {
    val aiTextConsent=context.settingsStore.data.map {it[booleanPreferencesKey("aiTextConsent")]?:false}
    suspend fun allowAiText(){context.settingsStore.edit {it[booleanPreferencesKey("aiTextConsent")]=true}}
    val mode = context.settingsStore.data.map {it[stringPreferencesKey("viewMode")]?:"local"}
    suspend fun mode(value: String){require(value in listOf("local","account","demo","legacy"));context.settingsStore.edit {p->val old=p[stringPreferencesKey("viewMode")]?:"local";if(value in listOf("demo","legacy")&&old !in listOf("demo","legacy"))p[stringPreferencesKey("returnMode")]=old;p[stringPreferencesKey("viewMode")]=value}}
    suspend fun leaveExamples(){context.settingsStore.edit {p->p[stringPreferencesKey("viewMode")]=p[stringPreferencesKey("returnMode")]?:"local"}}
    val pendingImport = context.settingsStore.data.map {it[stringPreferencesKey("pendingImport")]?:""}
    suspend fun pendingImport(value: String){context.settingsStore.edit {it[stringPreferencesKey("pendingImport")]=value}}
    val appearance = context.settingsStore.data.map { p -> Appearance(p[stringPreferencesKey("palette")]?:"lavender",p[booleanPreferencesKey("dark")]?:false,p[booleanPreferencesKey("welcomed")]?:false) }
    suspend fun update(value: Appearance) { context.settingsStore.edit { p ->
        p[stringPreferencesKey("palette")] = value.palette; p[booleanPreferencesKey("dark")] = value.dark; p[booleanPreferencesKey("welcomed")] = value.welcomed
    } }
}
data class DemoTask(val id: Int, val title: String, val area: String, val energy: Int, val hint: String, val status: String = "next", val projectId: Int? = null, val captureId: String?=null, val dueDate: String?=null)
data class DemoProject(val id: Int, val title: String, val area: String, val outcome: String)
data class DemoMaterial(val id: Int, val title: String, val body: String)
fun demoProjects()=listOf(DemoProject(1,"Курсовая работа сдана","Учёба и вуз","Работа принята преподавателем."),DemoProject(2,"Запустить первую версию приложения","Разработка","Рабочее приложение доступно для тестирования."))
fun demoMaterials()=listOf(DemoMaterial(1,"Идеи для будущих проектов","Всё, к чему можно вернуться позже."))
data class Thought(val id: Int, val title: String, val body: String, val idea: Boolean, val daysAgo: Int, val time: String, val favorite: Boolean = false, val sourceText: String? = null, val captureId: String?=null)
data class DemoEvent(val id: Int, val title: String, val date: String, val time: String)
data class DemoState(val tasks: List<DemoTask>, val thoughts: List<Thought>, val inbox: List<String>, val events: List<DemoEvent>,
    val focusedTaskId: Int? = null, val taskUndo: List<TaskCompletionUndo> = emptyList(),
    val projects: List<DemoProject> = demoProjects(), val materials: List<DemoMaterial> = demoMaterials(),
    val captures: List<StoredCapture> = emptyList(), val idHighWater: Map<String,Int> = emptyMap(),val recordRevisions: Map<String,String> = emptyMap(),val captureResults: Map<String,List<String>> = emptyMap())

data class StoredCapture(val id: String,val text: String,val createdAt: String,val status: String="inbox",val confirmedAt: String?=null,val source: String="text",val parentId: String?=null)
fun emptyState()=DemoState(emptyList(),emptyList(),emptyList(),emptyList(),projects=emptyList(),materials=emptyList())
fun DemoState.nextLocalId(kind: String): Int = maxOf(idHighWater[kind]?:0,when(kind){"action"->tasks.maxOfOrNull{it.id};"project"->projects.maxOfOrNull{it.id};"thought"->thoughts.maxOfOrNull{it.id};"event"->events.maxOfOrNull{it.id};"reference"->materials.maxOfOrNull{it.id};else->0}?:0)+1

fun fixtures() = DemoState(
    tasks = listOf(
        DemoTask(1,"Отправить старосте утверждённую тему работы в Telegram","Учёба и вуз",0,"Из мысли: «…старосте тему скинуть надо…»",projectId=1),
        DemoTask(2,"Купить корм коту по дороге домой","Дом и быт",0,"Из мысли: «…корм заканчивается…»"),
        DemoTask(3,"Набросать план введения для курсовой","Учёба и вуз",1,"Начни с трёх основных пунктов.",projectId=1),
        DemoTask(4,"Разобрать идеи для нового приложения","Разработка",2,"Составь список проблем, которые хочется решить.",projectId=2),
        DemoTask(5,"Дождаться ответа преподавателя","Учёба и вуз",0,"Напомнить, если долго не отвечает.","waiting",1),
        DemoTask(6,"Попробовать керамику","Для себя",0,"Идея на будущее.","someday"),
    ),
    thoughts = listOf(
        Thought(1,"Про маленькие паузы","Поймал себя на мысли, что самые интересные идеи появляются, когда перестаёшь торопиться. Хочется чаще оставлять для этого место.",false,0,"17:20"),
        Thought(2,"Идея для приложения","А что, если сделать пространство, куда можно просто выгружать мысли — и не превращать каждую из них в задачу? Пусть полезное само находится, когда понадобится.",true,1,"13:20",true),
        Thought(3,"Вечер без планов","Сегодня было много всего. Приятно понимать, что не обязательно решать всё прямо сейчас. Иногда хочется просто сохранить момент.",false,2,"21:20"),
        Thought(4,"Список вещей, которые вдохновляют","Собирать необычные книги, красивые интерфейсы, интересные места и небольшие открытия. Не как цели, а просто на память.",true,5,"18:20"),
    ),
    inbox=listOf("Надо записаться к стоматологу и не забыть про подарок маме"),
    events=listOf(DemoEvent(1,"Встреча с преподавателем",LocalDate.now().plusDays(2).toString(),"15:00")),
)

internal fun encode(s: DemoState) = JSONObject().apply {
    put("tasks",JSONArray(s.tasks.map { JSONObject().put("id",it.id).put("title",it.title).put("area",it.area).put("energy",it.energy).put("hint",it.hint).put("status",it.status).put("projectId",it.projectId).put("dueDate",it.dueDate?:JSONObject.NULL) }))
    put("thoughts",JSONArray(s.thoughts.map { JSONObject().put("id",it.id).put("title",it.title).put("body",it.body).put("idea",it.idea).put("days",it.daysAgo).put("time",it.time).put("favorite",it.favorite).put("sourceText",it.sourceText) }))
    put("inbox",JSONArray(s.inbox)); put("events",JSONArray(s.events.map { JSONObject().put("id",it.id).put("title",it.title).put("date",it.date).put("time",it.time) }))
    put("focusedTaskId",s.focusedTaskId)
    put("taskUndo",JSONArray(s.taskUndo.map {JSONObject().put("token",it.token).put("taskId",it.taskId).put("focusBefore",it.focusBefore).put("statusBefore",it.statusBefore)}))
    put("projects",JSONArray(s.projects.map {JSONObject().put("id",it.id).put("title",it.title).put("area",it.area).put("outcome",it.outcome)}))
    put("materials",JSONArray(s.materials.map {JSONObject().put("id",it.id).put("title",it.title).put("body",it.body)}))
}.toString()
internal fun decode(raw: String): DemoState {
    val j=JSONObject(raw)
    fun objects(key: String) = j.getJSONArray(key).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    return DemoState(objects("tasks").map { DemoTask(it.getInt("id"),it.getString("title"),it.getString("area"),it.getInt("energy"),it.getString("hint"),it.getString("status"),if(it.isNull("projectId"))null else it.getInt("projectId"),dueDate=if(it.isNull("dueDate"))null else it.optString("dueDate").takeIf {date->date.isNotBlank()}) },
        objects("thoughts").map { Thought(it.getInt("id"),it.getString("title"),it.getString("body"),it.getBoolean("idea"),it.getInt("days"),it.getString("time"),it.getBoolean("favorite"),if(it.isNull("sourceText"))null else it.optString("sourceText").takeIf {text->text.isNotBlank()}) },
        j.getJSONArray("inbox").let { a -> (0 until a.length()).map { a.getString(it) } },
        objects("events").map { DemoEvent(it.getInt("id"),it.getString("title"),it.getString("date"),it.getString("time")) },
        if(j.has("focusedTaskId")&&!j.isNull("focusedTaskId"))j.getInt("focusedTaskId") else null,
        j.optJSONArray("taskUndo")?.let {a->(0 until a.length()).map {i->a.getJSONObject(i).let {u->TaskCompletionUndo(u.getLong("token"),u.getInt("taskId"),if(u.has("focusBefore")&&!u.isNull("focusBefore"))u.getInt("focusBefore") else null,u.optString("statusBefore","next"))}}}?:emptyList(),
        if(j.has("projects"))objects("projects").map {DemoProject(it.getInt("id"),it.getString("title"),it.getString("area"),it.getString("outcome"))} else demoProjects(),
        if(j.has("materials"))objects("materials").map {DemoMaterial(it.getInt("id"),it.getString("title"),it.getString("body"))} else demoMaterials())
}
