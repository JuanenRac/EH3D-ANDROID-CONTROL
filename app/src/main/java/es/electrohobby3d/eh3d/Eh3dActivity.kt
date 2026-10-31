// EH3D-ANDROID-CONTROL - the one activity: the theme, the language, the back button and what to show (the address and sign-in, or the app).
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import es.electrohobby3d.eh3d.update.AppUpdateViewModel

class Eh3dActivity : ComponentActivity() {
    private val model: Eh3dViewModel by viewModels()

    private val askNotices = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tr.init(this)
        if (Build.VERSION.SDK_INT >= 33) askNotices.launch(Manifest.permission.POST_NOTIFICATIONS)
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.setVisible(true) else if (event == Lifecycle.Event.ON_STOP) model.setVisible(false)
        })
        setContent { Eh3dTheme { Root(model) } }
    }
}

@Composable
private fun Root(model: Eh3dViewModel) {
    val updates: AppUpdateViewModel = viewModel()
    BackHandler(enabled = model.phase == Phase.SignedIn && model.stack.size > 1) { model.back() }
    // the mark first, as in the apps of the other programs, and then what the phase of the app asks for
    var splash by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
    if (splash) {
        Eh3dSplash { splash = false }
        return
    }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        when (model.phase) {
            Phase.Booting -> RestoringScreen()
            Phase.SignedOut, Phase.FirstSetup -> EntryScreen(model)
            Phase.SignedIn -> AppFrame(model)
        }
    }
    // the update is only looked for, never installed, without the person asking
    androidx.compose.runtime.LaunchedEffect(model.phase) { if (model.phase == Phase.SignedIn) updates.checkForUpdate() }
}
