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
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bountyradar.app.ui.LoginScreen
import com.bountyradar.app.ui.RadarApp
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.theme.BountyRadarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val vm: RadarViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()

            BountyRadarTheme(themeMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val auth by vm.authState.collectAsStateWithLifecycle()

                    val permLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { /* ignored; in-app list still works */ }
                    LaunchedEffect(auth.signedIn) {
                        if (auth.signedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    if (auth.signedIn) RadarApp(vm) else LoginScreen(vm)
                }
            }
        }
    }
}
