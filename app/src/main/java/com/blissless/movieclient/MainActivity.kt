package com.blissless.movieclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blissless.movieclient.ui.ChizukiTheme
import com.blissless.movieclient.ui.TestScreen
import com.blissless.movieclient.viewmodel.TestViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ChizukiTheme {
                val vm: TestViewModel = viewModel()

                // Re-scan installed extensions every time the activity comes
                // back to the foreground — the user may have just sideloaded
                // a new extension APK from outside the app.
                val owner = LocalLifecycleOwner.current
                LaunchedEffect(owner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) vm.refreshExtensions()
                    }
                    owner.lifecycle.addObserver(observer)
                }

                TestScreen(viewModel = vm)
            }
        }
    }
}
