package ru.sbros.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import ru.sbros.app.data.*

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ProcessPersistenceTest {
    private val graph get()=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SbrosApplication).graph
    @Before fun isolatedEmulator(){Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("sbros_isolated")=="true"&&(Build.HARDWARE in listOf("ranchu","goldfish")||Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("Emulator",ignoreCase=true)||Build.MODEL.contains("sdk",ignoreCase=true)))}
    @Test fun a_seedBeforeForceStop()=runBlocking {
        graph.settings.mode("local");graph.settings.update(Appearance(welcomed=true))
        graph.data.change("local"){s->
            val projects=if(s.projects.any{it.title=="Перезапуск: проект"})s else s.saveProject(null,"Перезапуск: проект","Личное","Результат")
            val project=projects.projects.first{it.title=="Перезапуск: проект"}
            val actions=if(projects.tasks.any{it.title=="Перезапуск: дело"})projects else projects.saveTask(null,"Перезапуск: дело","waiting","Личное",project.id)
            val captures=if(actions.captures.any{it.id=="30000000-0000-0000-0000-000000000003"})actions else actions.copy(captures=actions.captures+StoredCapture("30000000-0000-0000-0000-000000000003","Оригинал между процессами\nцеликом","2021-02-03T04:05:06Z"))
            captures
        }
        graph.data.moveToThought("local","30000000-0000-0000-0000-000000000003")
        graph.data.draft("local","Черновик между процессами")
    }
    @Test fun b_verifyAfterForceStop()=runBlocking {
        val saved=graph.data.read("local");val action=saved.data.tasks.first{it.title=="Перезапуск: дело"}
        Assert.assertEquals("waiting",action.status)
        Assert.assertEquals("Перезапуск: проект",saved.data.projects.first{it.id==action.projectId}.title)
        val capture=saved.data.captures.first{it.id=="30000000-0000-0000-0000-000000000003"}
        Assert.assertEquals("2021-02-03T04:05:06Z",capture.createdAt)
        Assert.assertEquals("Оригинал между процессами\nцеликом",capture.text)
        Assert.assertTrue(saved.data.thoughts.any{it.captureId==capture.id&&it.sourceText==capture.text})
        Assert.assertEquals("Черновик между процессами",saved.draft)
    }
}
