package com.englishreader.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.englishreader.app.R
import com.englishreader.app.ui.dictionary.DictionaryScreen
import com.englishreader.app.ui.dictionary.ReviewScreen
import com.englishreader.app.ui.dictionary.WordEditScreen
import com.englishreader.app.ui.home.HomeScreen
import com.englishreader.app.ui.library.BookDetailScreen
import com.englishreader.app.ui.library.LibraryScreen
import com.englishreader.app.ui.progress.ProgressScreen
import com.englishreader.app.ui.practice.FillBlanksScreen
import com.englishreader.app.ui.practice.PracticeHubScreen
import com.englishreader.app.ui.practice.ReadingPracticeScreen
import com.englishreader.app.ui.practice.SpeakingPracticeScreen
import com.englishreader.app.ui.practice.WritingPracticeScreen
import com.englishreader.app.ui.reader.ReaderScreen
import com.englishreader.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val DICTIONARY = "dictionary"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"
    const val BOOK = "book/{bookId}"
    const val READER = "reader/{bookId}"
    const val WORD = "word/{wordId}"
    const val REVIEW = "review"
    const val PRACTICE = "practice"
    const val PRACTICE_ACTIVITY = "practice/{kind}?ids={ids}"

    fun book(id: String) = "book/$id"
    fun reader(id: String) = "reader/$id"
    fun word(id: Long) = "word/$id"
    fun practice(kind: String, ids: List<Long>) = "practice/$kind?ids=" + ids.joinToString(",")
}

private data class TopLevel(val route: String, @StringRes val label: Int, val selected: ImageVector, val unselected: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    TopLevel(Routes.LIBRARY, R.string.nav_library, Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    TopLevel(Routes.DICTIONARY, R.string.nav_dictionary_short, Icons.Filled.Translate, Icons.Outlined.Translate),
    TopLevel(Routes.PRACTICE, R.string.nav_practice, Icons.Filled.School, Icons.Outlined.School),
    TopLevel(Routes.PROGRESS, R.string.nav_progress, Icons.Filled.Insights, Icons.Outlined.Insights),
)

fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = topLevel.any { it.route == current }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        val selected = current == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateTopLevel(item.route) },
                            icon = { Icon(if (selected) item.selected else item.unselected, null) },
                            label = { Text(stringResource(item.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = if (showBar) padding.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f)),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenBook = { nav.navigate(Routes.reader(it)) },
                    onBrowseLibrary = { nav.navigateTopLevel(Routes.LIBRARY) },
                    onReview = { nav.navigate(Routes.REVIEW) },
                    onOpenWord = { nav.navigate(Routes.word(it)) },
                    onPractice = { nav.navigateTopLevel(Routes.PRACTICE) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenDetail = { nav.navigate(Routes.book(it)) },
                    onRead = { nav.navigate(Routes.reader(it)) },
                )
            }
            composable(Routes.DICTIONARY) {
                DictionaryScreen(
                    onOpenWord = { nav.navigate(Routes.word(it)) },
                    onReview = { nav.navigate(Routes.REVIEW) },
                )
            }
            composable(Routes.PROGRESS) { ProgressScreen(onOpenBook = { nav.navigate(Routes.reader(it)) }, onSettings = { nav.navigate(Routes.SETTINGS) }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PRACTICE) {
                PracticeHubScreen(
                    onOpen = { kind, ids -> nav.navigate(Routes.practice(kind, ids)) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onLibrary = { nav.navigateTopLevel(Routes.LIBRARY) },
                )
            }
            composable(
                Routes.PRACTICE_ACTIVITY,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("ids") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val kind = entry.arguments?.getString("kind").orEmpty()
                val ids = entry.arguments?.getString("ids").orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
                val back: () -> Unit = { nav.popBackStack() }
                when (kind) {
                    "reading" -> ReadingPracticeScreen(ids, back)
                    "blanks" -> FillBlanksScreen(ids, back)
                    "writing" -> WritingPracticeScreen(ids, back)
                    else -> SpeakingPracticeScreen(ids, back)
                }
            }
            composable(Routes.BOOK, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("bookId").orEmpty()
                BookDetailScreen(
                    bookId = id,
                    onBack = { nav.popBackStack() },
                    onRead = { nav.navigate(Routes.reader(it)) },
                )
            }
            composable(Routes.READER, arguments = listOf(navArgument("bookId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("bookId").orEmpty()
                ReaderScreen(bookId = id, onBack = { nav.popBackStack() })
            }
            composable(Routes.WORD, arguments = listOf(navArgument("wordId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("wordId") ?: 0L
                WordEditScreen(wordId = id, onBack = { nav.popBackStack() })
            }
            composable(Routes.REVIEW) { ReviewScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
