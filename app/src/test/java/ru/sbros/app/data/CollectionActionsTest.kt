package ru.sbros.app.data

import org.junit.Assert.*
import org.junit.Test

class CollectionActionsTest {
    @Test fun manualInboxMoveKeepsFullOriginalAndDoesNotCreateTasks() {
        val start=fixtures().copy(inbox=listOf("  Дневниковая запись\nВторая строка целиком  ","Другая запись"))
        val original=start.inbox.first()
        val moved=start.inboxToThought(0,original,"12:30")
        assertEquals(start.tasks,moved.tasks)
        assertEquals(listOf("Другая запись"),moved.inbox)
        assertEquals(original,moved.thoughts.first().body)
        assertEquals(original,moved.thoughts.first().sourceText)
        assertFalse(moved.thoughts.first().idea)
        assertSame(moved,moved.inboxToThought(0,original,"12:31"))
    }
    @Test fun editingATaskPreservesOriginalHintAndProjectAndReconcilesFocus() {
        val start=fixtures().reconcileTaskFocus()
        val updated=start.saveTask(1,"Исправленное дело","waiting","Учёба",1)
        val task=updated.tasks.first {it.id==1}
        assertEquals("Исправленное дело",task.title)
        assertEquals(start.tasks.first().hint,task.hint)
        assertEquals(1,task.projectId)
        assertEquals(2,updated.focusedAction?.id)
        assertFalse(updated.availableActions.any {it.id==1})
    }
    @Test fun projectEditsAndDeletionNeverDeleteTheirTasks() {
        val start=fixtures()
        val updated=start.saveProject(1,"Новый результат","Другая сфера","Готовый результат")
        assertEquals(start.tasks,updated.tasks)
        assertEquals("Готовый результат",updated.projects.first().outcome)
        val deleted=updated.deleteProject(1)
        assertEquals(start.tasks.size,deleted.tasks.size)
        assertTrue(deleted.tasks.none {it.projectId==1})
        assertTrue(deleted.tasks.any {it.projectId==2})
        assertEquals(start.inbox,deleted.inbox)
    }
    @Test fun newProjectActionsAndMaterialsUseUniqueIdsAndKeepFullText() {
        var state=fixtures().saveTask(null,"Дело проекта","next","Личное",1)
        state=state.saveTask(null,"Ещё одно","waiting","Личное",1)
        assertEquals(state.tasks.size,state.tasks.map {it.id}.distinct().size)
        assertEquals(1,state.tasks.last().projectId)
        state=state.saveMaterial(null,"Новый материал","Строка 1\nСтрока 2")
        val id=state.materials.last().id
        state=state.saveMaterial(id,"Переименованный материал","Полный текст\nбез обрезки")
        assertEquals(2,state.materials.size)
        assertEquals("Полный текст\nбез обрезки",state.materials.last().body)
    }
    @Test fun completingAndUndoingWaitingDoesNotPromoteItToNext() {
        val completed=fixtures().reconcileTaskFocus().completeTask(5,1)
        assertEquals("done",completed.tasks.first {it.id==5}.status)
        val restored=completed.undoTaskCompletion(1)
        assertEquals("waiting",restored.tasks.first {it.id==5}.status)
        assertFalse(restored.availableActions.any {it.id==5})
        assertEquals(1,restored.focusedAction?.id)
    }
    @Test fun invalidOrDeletedTargetsDoNotCreatePhantomRecords() {
        val state=fixtures()
        assertSame(state,state.saveTask(999,"Нет такой записи","next","Личное"))
        assertSame(state,state.saveTask(null,"Новое","next","Личное",999))
        assertSame(state,state.saveProject(999,"Проект","",""))
        assertSame(state,state.saveMaterial(999,"Материал",""))
        assertSame(state,state.saveMaterial(null," ","Текст"))
    }
}
