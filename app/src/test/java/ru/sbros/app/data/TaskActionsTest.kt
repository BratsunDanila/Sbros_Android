package ru.sbros.app.data

import org.junit.Assert.*
import org.junit.Test

class TaskActionsTest {
    private fun state(count: Int) = DemoState(
        (1..count).map {DemoTask(it,"Дело $it","Личное",it%3,"Первый шаг")}
            + listOf(DemoTask(100,"Жду","Личное",0,"","waiting"),DemoTask(101,"Потом","Личное",0,"","someday"),DemoTask(102,"Готово","Личное",0,"","done")),
        listOf(Thought(1,"Мысль","Исходник",false,0,"12:00")),listOf("Сохранённый сброс"),emptyList(),
    ).reconcileTaskFocus()

    @Test fun allFiftyActionsAreAvailableAcrossEnergyLevelsWithoutDuplicates() {
        val s=state(50)
        assertEquals(50,s.availableActions.size)
        assertEquals(49,s.remainingActions.size)
        assertEquals(50,(s.remainingActions.map {it.id}+s.focusedAction!!.id).toSet().size)
        assertFalse(s.remainingActions.any {it.id==s.focusedAction?.id})
        assertTrue(s.availableActions.all {it.status=="next"})
    }
    @Test fun selectingAnotherTaskReturnsThePreviousOneToTheList() {
        val s=state(50).focusTask(49)
        assertEquals(49,s.focusedAction?.id)
        assertTrue(s.remainingActions.any {it.id==1})
        assertFalse(s.remainingActions.any {it.id==49})
        assertEquals(1,s.focusTask(100).nextTask().nextTask().focusedAction?.id)
    }
    @Test fun completingAListItemPreservesFocusAndUndoPreservesLaterEdits() {
        val s=state(4).focusTask(3).completeTask(2,1)
        assertEquals(3,s.focusedAction?.id)
        val edited=s.copy(tasks=s.tasks.map {if(it.id==2)it.copy(title="Исправленное название")else it},inbox=s.inbox+"Новый сброс")
        val restored=edited.undoTaskCompletion(1)
        assertEquals("Исправленное название",restored.tasks.first {it.id==2}.title)
        assertEquals(3,restored.focusedAction?.id)
        assertEquals(2,restored.inbox.size)
    }
    @Test fun rapidCompletionsAndTargetedUndoDoNotUndoEachOther() {
        var s=state(50)
        for(id in 1..50)s=s.completeTask(id,id.toLong())
        assertNull(s.focusedAction)
        assertTrue(s.availableActions.isEmpty())
        s=s.undoTaskCompletion(25)
        assertEquals(listOf(25),s.availableActions.map {it.id})
        assertEquals(49,s.tasks.count {it.id<=50&&it.status=="done"})
        assertEquals(listOf("Сохранённый сброс"),s.inbox)
        assertEquals("Исходник",s.thoughts.single().body)
    }
    @Test fun duplicateCompletionAndStaleUndoAreHarmless() {
        val once=state(1).completeTask(1,1)
        assertSame(once,once.completeTask(1,2))
        val twice=once.undoTaskCompletion(1).completeTask(1,3)
        assertSame(twice,twice.undoTaskCompletion(1))
        assertTrue(twice.availableActions.isEmpty())
    }
    @Test fun emptySingleTaskAndExternalUpdatesReconcileSelection() {
        assertNull(state(0).focusedAction)
        val one=state(1)
        assertEquals(1,one.nextTask().focusedAction?.id)
        assertTrue(one.remainingActions.isEmpty())
        val many=state(3).focusTask(3)
        assertEquals(3,many.copy(tasks=many.tasks+DemoTask(4,"Новое","",0,"")).reconcileTaskFocus().focusedAction?.id)
        assertEquals(1,many.copy(tasks=many.tasks.map {if(it.id==3)it.copy(status="waiting")else it}).reconcileTaskFocus().focusedAction?.id)
    }
}
