package com.tiss.replaycam

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tiss.replaycam.camera.CameraManager
import com.tiss.replaycam.store.ClipStore
import com.tiss.replaycam.ui.camera.CameraScreen
import com.tiss.replaycam.ui.home.HomeScreen
import com.tiss.replaycam.ui.library.LibraryScreen
import com.tiss.replaycam.ui.library.PlayerScreen
import com.tiss.replaycam.ui.pose.PoseAnalysisScreen
import com.tiss.replaycam.ui.pose.PoseResultScreen
import com.tiss.replaycam.ui.settings.SettingsScreen
import com.tiss.replaycam.ui.theme.ReplayCamTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val KEY_DELAY = doublePreferencesKey("default_delay")
private val KEY_FPS = intPreferencesKey("default_fps")
private val KEY_LENS = intPreferencesKey("default_lens")

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val clipStore = ClipStore.getInstance(this)

        setContent {
            ReplayCamTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                val defaultDelay by dataStore.data
                    .map { it[KEY_DELAY] ?: 5.0 }
                    .collectAsState(initial = 5.0)
                val defaultFps by dataStore.data
                    .map { it[KEY_FPS] ?: 30 }
                    .collectAsState(initial = 30)
                val defaultLens by dataStore.data
                    .map { it[KEY_LENS] ?: 0 }
                    .collectAsState(initial = 0)

                NavHost(navController = navController, startDestination = "home") {

                    composable("home") {
                        HomeScreen(
                            navController = navController,
                            clipStore = clipStore,
                            onOpenCamera = { navController.navigate("camera") }
                        )
                    }

                    composable("camera") {
                        val cameraManager: CameraManager = viewModel()
                        CameraScreen(
                            cameraManager = cameraManager,
                            defaultDelay = defaultDelay,
                            defaultFps = defaultFps,
                            defaultLens = defaultLens,
                            onDismiss = { navController.popBackStack() }
                        )
                    }

                    composable("library") {
                        LibraryScreen(
                            navController = navController,
                            clipStore = clipStore
                        )
                    }

                    composable(
                        route = "player/{clipId}",
                        arguments = listOf(navArgument("clipId") { type = NavType.StringType })
                    ) { back ->
                        val clipId = back.arguments?.getString("clipId") ?: return@composable
                        PlayerScreen(
                            clipId = clipId,
                            navController = navController,
                            clipStore = clipStore
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            navController = navController,
                            clipStore = clipStore,
                            defaultDelay = defaultDelay,
                            onDelayChange = { v ->
                                scope.launch { dataStore.edit { it[KEY_DELAY] = v } }
                            },
                            defaultFps = defaultFps,
                            onFpsChange = { v ->
                                scope.launch { dataStore.edit { it[KEY_FPS] = v } }
                            },
                            defaultLens = defaultLens,
                            onLensChange = { v ->
                                scope.launch { dataStore.edit { it[KEY_LENS] = v } }
                            }
                        )
                    }

                    composable("pose") {
                        PoseAnalysisScreen(
                            navController = navController,
                            clipStore = clipStore
                        )
                    }

                    composable(
                        route = "pose_result/{clipId}",
                        arguments = listOf(navArgument("clipId") { type = NavType.StringType })
                    ) { back ->
                        val clipId = back.arguments?.getString("clipId") ?: return@composable
                        PoseResultScreen(
                            clipId = clipId,
                            navController = navController,
                            clipStore = clipStore
                        )
                    }
                }
            }
        }
    }
}
