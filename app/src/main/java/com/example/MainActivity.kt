package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.TopNavBar
import com.example.ui.screens.*
import com.example.ui.theme.FrenchStudyCompanionTheme
import com.example.viewmodel.FrenchCompanionViewModel
import com.example.viewmodel.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FrenchStudyCompanionTheme {
                val viewModel: FrenchCompanionViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()
                val context = LocalContext.current

                // Request microphone and camera permissions on launch for smooth user experience
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { /* permissions handled */ }

                LaunchedEffect(Unit) {
                    val permissionsNeeded = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.CAMERA)
                    }
                    if (permissionsNeeded.isNotEmpty()) {
                        permissionLauncher.launch(permissionsNeeded.toTypedArray())
                    }
                }

                // Handle system back navigation
                BackHandler(enabled = currentScreen != Screen.TRANSLATE) {
                    viewModel.navigateBack()
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    topBar = {
                        Column(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background)
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            TopNavBar(
                                currentScreen = currentScreen,
                                onSelectScreen = { screen ->
                                    viewModel.navigateTo(screen)
                                }
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentScreen) {
                            Screen.TRANSLATE -> TranslateScreen(viewModel = viewModel)
                            Screen.MY_CLASS -> DocumentsScreen(viewModel = viewModel)
                            Screen.STUDY -> StudyScreen(viewModel = viewModel)
                            Screen.QUIZ_ME -> QuizScreen(viewModel = viewModel)
                            Screen.ASK_ANYTHING -> AskAnythingScreen(viewModel = viewModel)
                            Screen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
