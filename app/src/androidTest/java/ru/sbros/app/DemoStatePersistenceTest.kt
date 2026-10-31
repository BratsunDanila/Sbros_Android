package ru.sbros.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.sbros.app.data.*
import ru.sbros.app.data.local.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DemoStatePersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(name: String)=Room.databaseBuilder(context,SbrosDatabase::class.java,name).addMigrations(SbrosDatabase.MIGRATION_1_2).build()
    @Test fun reopeningFileRestoresRecordsOriginalsLinksDraftAndCompletion()= runBlocking {
        val name="persistence-${UUID.randomUUID()}.db"
        var db=open(name)
        try {
            var repo=RoomDataRepository(db)
            repo.change("local"){it.saveProject(null,"Проект","Учёба","Результат").saveTask(null,"Дело","next","Учёба",1)}
            val raw="Полный исходник\nВторая строка с пробелами  "
            repo.saveCapture("local",raw)
            val capture=repo.read("local").data.captures.single()
            repo.moveToThought("local",capture.id)
            repo.change("local"){it.saveMaterial(null,"Материал с источником","Полный текст").copy(events=listOf(DemoEvent(1,"Событие с источником","2026-10-29","12:00")))}
            repo.transaction{
                val rows=repo.dao.records("local").filter{it.kind in listOf("project","event","reference")}
                rows.forEach{it.captureId=capture.id};repo.dao.putRecords(rows)
            }
            repo.change("local"){it.completeTask(1,41)}
            repo.draft("local","Черновик не теряется")
            val before=repo.transaction{repo.dao.records("local").associate{it.id to it.createdAt}}
            db.close();db=open(name);repo=RoomDataRepository(db)
            val restored=repo.read("local")
            assertEquals("Черновик не теряется",restored.draft)
            assertEquals("done",restored.data.tasks.single().status)
            assertEquals(1,restored.data.tasks.single().projectId)
            assertEquals(raw,restored.data.thoughts.single().body)
            assertEquals(raw,restored.data.thoughts.single().sourceText)
            assertEquals(capture.id,restored.data.thoughts.single().captureId)
            assertEquals("committed",restored.data.captures.single().status)
            assertTrue(restored.data.captureResults.getValue(capture.id).containsAll(listOf("Проект","Материал с источником","Событие с источником")))
            assertTrue(repo.transaction{repo.dao.records("local").filter{it.kind in listOf("project","event","reference")}.all{it.captureId==capture.id}})
            assertEquals(before,repo.transaction{repo.dao.records("local").associate{it.id to it.createdAt}})
            repo.change("local"){it.undoTaskCompletion(41)}
            assertEquals("next",repo.read("local").data.tasks.single().status)
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun importRequiresAnExplicitCallPreservesDatesAndNeverMixesAccounts()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,SbrosDatabase::class.java).build()
        try {
            val repo=RoomDataRepository(db)
            repo.change("local"){it.saveProject(null,"Личный проект","","Итог").saveTask(null,"Связанный шаг","next","",1)}
            repo.saveCapture("local","Личная запись");val capture=repo.read("local").data.captures.single()
            repo.moveToThought("local",capture.id)
            repo.enableSync("A")
            assertTrue(repo.read("user:A").data.thoughts.isEmpty())
            val originals=repo.transaction{repo.dao.records("local").associate{it.id to it.createdAt}}
            repo.change("user:A"){it.saveProject(null,"Уже есть в аккаунте","","")}
            repo.importLocal("A");repo.importLocal("A")
            val account=repo.read("user:A").data
            assertEquals(2,account.projects.size)
            assertEquals(account.projects.first{it.title=="Личный проект"}.id,account.tasks.single().projectId)
            assertEquals(capture.id,account.thoughts.single().captureId)
            assertTrue(repo.transaction{repo.dao.records("user:A")}.filter{it.id in originals}.all{it.createdAt==originals[it.id]})
            assertEquals(1,repo.read("local").data.thoughts.size)
            assertTrue(repo.read("user:B").data.tasks.isEmpty())
            assertTrue(repo.read("demo").data.thoughts.none{it.body=="Личная запись"})
        } finally {db.close()}
    }
    @Test fun deletedIdsAreNotReusedAndUndoCannotOverwriteALaterEdit()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(context,SbrosDatabase::class.java).build()
        try {
            val repo=RoomDataRepository(db)
            repo.change("local"){it.saveTask(null,"Первое","next","")}
            val deleted=repo.change("local"){it.copy(tasks=emptyList())}
            repo.change("local"){it.saveTask(null,"Второе","next","")}
            assertEquals(2,repo.read("local").data.tasks.single().id)
            repo.undo(deleted.receipt)
            assertEquals(2,repo.read("local").data.tasks.size)
            val edit=repo.change("local"){it.saveTask(1,"Правка 1","next","")}
            repo.change("local"){it.saveTask(1,"Правка 2","next","")}
            repo.undo(edit.receipt)
            assertEquals("Правка 2",repo.read("local").data.tasks.first{it.id==1}.title)
            val row=repo.transaction{repo.dao.records("local").first{it.kind=="action"}}
            repo.change("local"){it.focusTask(2)}
            assertEquals(row.changeId,repo.transaction{repo.dao.records("local").first{it.id==row.id}.changeId})
            val revision=repo.read("local").data.recordRevisions.getValue("action:1")
            repo.change("local"){it.saveTask(1,"Обновление другого устройства","next","")}
            assertTrue(runCatching{repo.checkedChange("local","action",1,revision){it.saveTask(1,"Устаревшая форма","next","")}}.exceptionOrNull() is StaleEditor)
            assertEquals("Обновление другого устройства",repo.read("local").data.tasks.first{it.id==1}.title)
        } finally {db.close()}
    }
    @Test fun migrationOneToTwoPreservesExistingText()=runBlocking {
        val name="migration-${UUID.randomUUID()}.db"
        val file=context.getDatabasePath(name);file.parentFile!!.mkdirs()
        val schema=JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("ru.sbros.app.data.local.SbrosDatabase/2.json").bufferedReader().readText()).getJSONObject("database").getJSONArray("entities")
        val sql=SQLiteDatabase.openOrCreateDatabase(file,null)
        try {
            for(i in 0 until schema.length()) {
                val entity=schema.getJSONObject(i);val table=entity.getString("tableName")
                sql.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}",table).replace("`deletedAt` TEXT, ",""))
                val indices=entity.getJSONArray("indices")
                for(j in 0 until indices.length())sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",table))
            }
            sql.execSQL("INSERT INTO workspaces(scope,draft,uiState,syncEnabled) VALUES('local','Не потерять','{}',0)")
            val payload=JSONObject().put("title","Старый материал").put("body","Полный исходный текст").toString()
            sql.execSQL("INSERT INTO records(scope,id,kind,localId,payload,createdAt,updatedAt,serverVersion,changeId,dirty) VALUES('local',?,'reference',1,?,'2020-01-01T00:00:00Z','2020-01-01T00:00:00Z',0,?,1)",arrayOf(UUID.randomUUID().toString(),payload,UUID.randomUUID().toString()))
            sql.version=1
        } finally {sql.close()}
        val db=open(name)
        try {val restored=RoomDataRepository(db).read("local");assertEquals("Не потерять",restored.draft);assertEquals("Полный исходный текст",restored.data.materials.single().body)}finally {db.close();context.deleteDatabase(name)}
    }
}
