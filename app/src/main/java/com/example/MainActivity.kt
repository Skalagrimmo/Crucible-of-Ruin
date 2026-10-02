package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.game.AssetCache
import com.example.game.GameScreen
import com.example.game.MainMenuScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pre-load and recycle all sound buffers and textures into static memory
        AssetCache.init()

        // Lock to landscape for mobile platformer ergonomics
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Setup edge-to-edge window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf("menu") }
                var startCheckpoint by remember { mutableIntStateOf(0) }
                var currentLevelId by remember { mutableStateOf("level_01") }
                var targetFps by remember { mutableIntStateOf(30) }

                when (currentScreen) {
                    "menu" -> {
                        MainMenuScreen(
                            onStartGame = { checkpoint, fps ->
                                startCheckpoint = checkpoint
                                currentLevelId = "level_01"
                                targetFps = fps
                                currentScreen = "game"
                            }
                        )
                    }
                    "game" -> {
                        BackHandler {
                            currentScreen = "menu"
                        }
                        GameScreen(
                            levelId = currentLevelId,
                            startCheckpoint = startCheckpoint,
                            initialTargetFps = targetFps,
                            onNextLevel = if (currentLevelId == "level_01") {
                                {
                                    currentLevelId = "level_02"
                                    startCheckpoint = 0
                                }
                            } else null,
                            onReturnToMainMenu = {
                                currentScreen = "menu"
                            }
                        )
                    }
                }
            }
        }

        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val decorView = window.decorView
        val controller = WindowCompat.getInsetsController(window, decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        AssetCache.release()
        super.onDestroy()
    }
}
