package ru.sbros.app

import android.os.Bundle
import androidx.activity.*
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.sbros.app.data.*

class MainActivity: ComponentActivity() {
    private val graph get()=(application as SbrosApplication).graph
    private val model: SbrosViewModel by viewModels { viewModelFactory { initializer { SbrosViewModel(createSavedStateHandle(),graph.settings,graph.data,graph.activeScope) } } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance by model.appearance.collectAsState()
            val workspace by model.workspace.collectAsState()
            val auth by graph.auth.state.collectAsState()
            val a=appearance?:Appearance()
            SideEffect {
                WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=!a.dark;isAppearanceLightNavigationBars=!a.dark }
                if(android.os.Build.VERSION.SDK_INT>=29) window.isNavigationBarContrastEnforced=false
            }
            val scopeAllowed=!workspace.scope.startsWith("user:")||workspace.scope=="user:${auth.user?.id}"
            ru.sbros.app.core.designsystem.SbrosTheme(a.palette,a.dark) { key(workspace.scope) {SbrosApp(model,a,appearance!=null&&workspace.loaded&&!auth.restoring&&scopeAllowed)} }
        }
    }
}
