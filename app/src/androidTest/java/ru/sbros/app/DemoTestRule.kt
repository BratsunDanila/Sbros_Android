package ru.sbros.app
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.rules.ExternalResource
import ru.sbros.app.data.Appearance

/** Existing v15 visual fixtures are deliberately isolated from real user records. */
class DemoTestRule: ExternalResource() {
    private val graph get()=(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SbrosApplication).graph
    override fun before()=runBlocking {
        graph.data.resetDemo();graph.settings.update(Appearance(welcomed=true));graph.settings.mode("demo")
        withTimeout(5_000){graph.activeScope.first{it=="demo"}};Unit
    }
    override fun after()=runBlocking {graph.settings.mode("local")}
}
