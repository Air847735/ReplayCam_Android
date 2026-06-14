package com.tiss.replaycam.ui.pose

import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.tiss.replaycam.store.ClipStore
import com.tiss.replaycam.store.SavedClip
import com.tiss.replaycam.ui.library.LibraryScreen

@Composable
fun PoseAnalysisScreen(
    navController: NavController,
    clipStore: ClipStore
) {
    LibraryScreen(
        navController = navController,
        clipStore = clipStore,
        title = "骨架分析",
        onSelectClip = { clip ->
            navController.navigate("pose_result/${clip.id}")
        }
    )
}
