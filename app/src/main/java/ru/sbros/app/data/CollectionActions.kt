package ru.sbros.app.data

fun DemoState.saveTask(id: Int?,title: String,status: String,area: String,projectId: Int?=null,dueDate: String?=tasks.firstOrNull {it.id==id}?.dueDate): DemoState {
    if(title.isBlank()||title.length>500||status !in listOf("next","waiting","someday","done")||!validTaskDeadline(dueDate))return this
    if(id!=null&&tasks.none {it.id==id})return this
    if(projectId!=null&&projects.none {it.id==projectId})return this
    val old=tasks.firstOrNull {it.id==id}
    val deadline=dueDate?.takeIf {it.isNotBlank()}
    val task=old?.copy(title=title.trim(),status=status,area=area.trim().ifBlank {"Личное"},projectId=projectId,dueDate=deadline)
        ?:DemoTask(nextLocalId("action"),title.trim(),area.trim().ifBlank {"Личное"},0,"",status,projectId,dueDate=deadline)
    return copy(tasks=if(old==null)tasks+task else tasks.map {if(it.id==id)task else it}).reconcileTaskFocus()
}
fun DemoState.saveProject(id: Int?,title: String,area: String,outcome: String): DemoState {
    if(title.isBlank()||title.length>500||(id!=null&&projects.none {it.id==id}))return this
    val project=DemoProject(id?:nextLocalId("project"),title.trim(),area.trim().ifBlank {"Личное"},outcome.trim())
    return copy(projects=if(id==null)projects+project else projects.map {if(it.id==id)project else it})
}
fun DemoState.deleteProject(id: Int): DemoState = copy(
    projects=projects.filterNot {it.id==id},
    tasks=tasks.map {if(it.projectId==id)it.copy(projectId=null)else it},
)
fun DemoState.saveMaterial(id: Int?,title: String,body: String): DemoState {
    if(title.isBlank()||title.length>500||(id!=null&&materials.none {it.id==id}))return this
    val material=DemoMaterial(id?:nextLocalId("reference"),title.trim(),body)
    return copy(materials=if(id==null)materials+material else materials.map {if(it.id==id)material else it})
}
fun DemoState.inboxToThought(index: Int,raw: String,time: String): DemoState {
    if(inbox.getOrNull(index)!=raw||raw.isBlank())return this
    val thought=Thought(nextLocalId("thought"),raw.trim().lineSequence().first().take(60),raw,false,0,time,sourceText=raw)
    return copy(thoughts=listOf(thought)+thoughts,inbox=inbox.filterIndexed {i,_->i!=index})
}
