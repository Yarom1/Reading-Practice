package com.korimbekef.reading.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.korimbekef.reading.AppContainer
import com.korimbekef.reading.ReadingApp
import com.korimbekef.reading.core.GameType
import com.korimbekef.reading.ui.components.Owl
import com.korimbekef.reading.ui.screens.GamePlayScreen
import com.korimbekef.reading.ui.screens.GamesScreen
import com.korimbekef.reading.ui.screens.HomeScreen
import com.korimbekef.reading.ui.screens.LibraryScreen
import com.korimbekef.reading.ui.screens.MemoryGameScreen
import com.korimbekef.reading.ui.screens.MyWordsScreen
import com.korimbekef.reading.ui.screens.OnboardingScreen
import com.korimbekef.reading.ui.screens.TreasureScreen
import com.korimbekef.reading.ui.screens.parent.AboutScreen
import com.korimbekef.reading.ui.screens.parent.AddPassageScreen
import com.korimbekef.reading.ui.screens.parent.ChangePinScreen
import com.korimbekef.reading.ui.screens.parent.ChildSettingsScreen
import com.korimbekef.reading.ui.screens.parent.ContentScreen
import com.korimbekef.reading.ui.screens.parent.ParentGateScreen
import com.korimbekef.reading.ui.screens.parent.ParentHomeScreen
import com.korimbekef.reading.ui.screens.parent.ParentWordsScreen
import com.korimbekef.reading.ui.screens.parent.ReportScreen
import com.korimbekef.reading.ui.screens.parent.VoiceSettingsScreen
import com.korimbekef.reading.ui.screens.reading.ReadingScreen
import com.korimbekef.reading.ui.theme.KColors

/** True when the active child is a girl, so texts use the feminine forms. */
val LocalGirl = staticCompositionLocalOf { false }

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIBRARY = "library?level={level}"
    fun library(level: Int = 0) = "library?level=$level"
    const val READ = "read/{id}?restart={restart}"
    fun read(id: String, restart: Boolean = false) = "read/${Uri.encode(id)}?restart=$restart"
    const val GAMES = "games"
    const val GAME = "game/{type}?mine={mine}"
    fun game(type: GameType, mine: Boolean = false) = "game/${type.name}?mine=$mine"
    const val WORDS = "words"
    const val TREASURE = "treasure"
    const val PARENT_GATE = "parent/gate"
    const val PARENT = "parent"
    const val PARENT_REPORT = "parent/report"
    const val PARENT_CHILD = "parent/child/{id}"
    fun parentChild(id: Long) = "parent/child/$id"
    const val PARENT_VOICE = "parent/voice"
    const val PARENT_CONTENT = "parent/content"
    const val PARENT_ADD_PASSAGE = "parent/content/add"
    const val PARENT_WORDS = "parent/words"
    const val PARENT_PIN = "parent/pin"
    const val PARENT_ABOUT = "parent/about"
}

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as ReadingApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

@Composable
fun AppRoot() {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val child by container.children.activeChild.collectAsStateWithLifecycle(initialValue = null)
    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize().background(KColors.Cream), contentAlignment = Alignment.Center) { Owl() }
        return
    }
    val startRoute = remember { if (s.onboardingDone) Routes.HOME else Routes.ONBOARDING }
    val nav = rememberNavController()
    CompositionLocalProvider(LocalGirl provides (child?.isGirl == true)) {
        AppNavHost(nav, startRoute)
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, startRoute: String) {
    fun NavHostController.goHome() = navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }

    NavHost(
        navController = nav,
        startDestination = startRoute,
        modifier = Modifier.fillMaxSize().background(KColors.Cream),
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onRead = { id -> nav.navigate(Routes.read(id)) },
                onLibrary = { nav.navigate(Routes.library()) },
                onGames = { nav.navigate(Routes.GAMES) },
                onWords = { nav.navigate(Routes.WORDS) },
                onTreasure = { nav.navigate(Routes.TREASURE) },
                onParents = { nav.navigate(Routes.PARENT_GATE) },
            )
        }
        composable(
            Routes.LIBRARY,
            arguments = listOf(navArgument("level") { type = NavType.IntType; defaultValue = 0 }),
        ) { entry ->
            LibraryScreen(
                initialLevel = entry.arguments?.getInt("level") ?: 0,
                onBack = { nav.popBackStack() },
                onRead = { id -> nav.navigate(Routes.read(id)) },
            )
        }
        composable(
            Routes.READ,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("restart") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            ReadingScreen(
                passageId = id,
                restart = entry.arguments?.getBoolean("restart") ?: false,
                onBack = { nav.popBackStack() },
                onOpenPassage = { next, restart ->
                    nav.navigate(Routes.read(next, restart)) { popUpTo(Routes.READ) { inclusive = true } }
                },
                onLibrary = {
                    nav.navigate(Routes.library()) { popUpTo(Routes.HOME) }
                },
            )
        }
        composable(Routes.GAMES) {
            GamesScreen(
                onBack = { nav.popBackStack() },
                onPlay = { type, mine -> nav.navigate(Routes.game(type, mine)) },
            )
        }
        composable(
            Routes.GAME,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("mine") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val type = runCatching { GameType.valueOf(entry.arguments?.getString("type").orEmpty()) }.getOrDefault(GameType.WORD_TO_PICTURE)
            val mine = entry.arguments?.getBoolean("mine") ?: false
            if (type == GameType.MEMORY) {
                MemoryGameScreen(onBack = { nav.popBackStack() })
            } else {
                GamePlayScreen(type = type, onlyMyWords = mine, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.WORDS) {
            MyWordsScreen(
                onBack = { nav.popBackStack() },
                onPractice = { nav.navigate(Routes.game(GameType.LISTEN_AND_CHOOSE, mine = true)) },
            )
        }
        composable(Routes.TREASURE) { TreasureScreen(onBack = { nav.popBackStack() }) }

        // ---- parent zone ----
        composable(Routes.PARENT_GATE) {
            ParentGateScreen(
                onBack = { nav.popBackStack() },
                onUnlocked = { nav.navigate(Routes.PARENT) { popUpTo(Routes.PARENT_GATE) { inclusive = true } } },
            )
        }
        composable(Routes.PARENT) {
            ParentHomeScreen(
                onExit = { nav.goHome() },
                onReport = { nav.navigate(Routes.PARENT_REPORT) },
                onChild = { id -> nav.navigate(Routes.parentChild(id)) },
                onVoice = { nav.navigate(Routes.PARENT_VOICE) },
                onContent = { nav.navigate(Routes.PARENT_CONTENT) },
                onWords = { nav.navigate(Routes.PARENT_WORDS) },
                onPin = { nav.navigate(Routes.PARENT_PIN) },
                onAbout = { nav.navigate(Routes.PARENT_ABOUT) },
            )
        }
        composable(Routes.PARENT_REPORT) { ReportScreen(onBack = { nav.popBackStack() }) }
        composable(
            Routes.PARENT_CHILD,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            ChildSettingsScreen(childId = entry.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
        }
        composable(Routes.PARENT_VOICE) { VoiceSettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PARENT_CONTENT) {
            ContentScreen(onBack = { nav.popBackStack() }, onAdd = { nav.navigate(Routes.PARENT_ADD_PASSAGE) })
        }
        composable(Routes.PARENT_ADD_PASSAGE) { AddPassageScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PARENT_WORDS) { ParentWordsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PARENT_PIN) { ChangePinScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PARENT_ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}

/** System screens for installing / configuring text-to-speech voices (parent area only). */
object SystemIntents {
    fun installVoiceData(context: Context): Boolean = runCatching {
        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun ttsSettings(context: Context): Boolean = runCatching {
        context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
