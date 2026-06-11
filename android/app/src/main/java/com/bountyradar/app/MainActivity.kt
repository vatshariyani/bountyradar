package com.bountyradar.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bountyradar.app.ui.LoginScreen
import com.bountyradar.app.ui.ProgramListScreen
import com.bountyradar.app.ui.RadarViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val vm: RadarViewModel = viewModel()
                    val auth by vm.authState.collectAsStateWithLifecycle()

                    // Ask for notification permission once we have a signed-in user.
                    val permLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { /* result ignored; list still works without it */ }
                    LaunchedEffect(auth.signedIn) {
                        if (auth.signedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    if (auth.signedIn) ProgramListScreen(vm) else LoginScreen(vm)
                }
            }
        }
    }
}
