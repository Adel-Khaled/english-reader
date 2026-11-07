package com.englishreader.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.os.LocaleListCompat
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** End-to-end UI flow on a device: open book → tap word → see translation → save → dictionary. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitText(text: String, substring: Boolean = false, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun settle() { rule.waitForIdle(); Thread.sleep(900) }

    @Test fun a_readTapTranslateAndSave() {
        val title = "The Lighthouse Keeper's Notebook"
        waitText(title)
        settle(); screenshot("01_home")

        rule.onAllNodesWithText(title)[0].performClick()
        val paragraph = "People in the town said it was abandoned"
        waitText(paragraph, substring = true)
        settle(); screenshot("02_reader")

        // Tap exactly on the word "curious" using the text layout.
        val node = rule.onNodeWithText(paragraph, substring = true)
        val sem = node.fetchSemanticsNode()
        val text = sem.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)!!.joinToString("") { it.text }
        val results = mutableListOf<TextLayoutResult>()
        sem.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(results)
        val start = text.indexOf("curious")
        val box = results.first().getBoundingBox(start + 3)
        node.performTouchInput { click(Offset(box.center.x, box.center.y)) }

        waitText("Definition", timeout = 40_000)
        rule.waitUntil(40_000) { rule.onAllNodesWithText("Save to My Dictionary").fetchSemanticsNodes().any { n ->
            n.config.getOrNull(SemanticsActions.OnClick) != null && !n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } || rule.onAllNodesWithText("Saved").fetchSemanticsNodes().isNotEmpty() }
        settle(); screenshot("03_word_sheet")

        if (rule.onAllNodesWithText("Saved").fetchSemanticsNodes().isEmpty()) {
            rule.onNodeWithText("Save to My Dictionary").performClick()
            waitText("Saved")
        }
        runBlocking { assertNotNull(container().vocab.findByWord("curious")) }
        settle(); screenshot("04_word_saved")

        Espresso.pressBack() // close sheet
        settle(); screenshot("05_reader_underlined")
        Espresso.pressBack() // back to home
        waitText("My Dictionary")
        rule.onAllNodesWithText("My Dictionary")[0].performClick()
        waitText("curious")
        settle(); screenshot("06_dictionary")

        rule.onAllNodesWithText("curious")[0].performClick()
        waitText("Personal notes")
        settle(); screenshot("07_word_edit")
        Espresso.pressBack()
    }

    @Test fun b_libraryProgressSettings() {
        waitText("Library")
        rule.onAllNodesWithText("Library")[0].performClick()
        waitText("Featured free books")
        settle(); screenshot("08_library")

        rule.onAllNodesWithText("Peter Pan")[0].performClick()
        waitText("Download & read")
        settle(); screenshot("09_book_detail")
        Espresso.pressBack()

        waitText("Progress")
        rule.onAllNodesWithText("Progress")[0].performClick()
        waitText("Words saved")
        settle(); screenshot("10_progress")

        rule.onAllNodesWithText("Settings")[0].performClick()
        waitText("Offline Arabic translation pack")
        settle(); screenshot("11_settings")

        rule.onAllNodesWithText("Dark")[0].performClick()
        settle()
        rule.onAllNodesWithText("Home")[0].performClick()
        waitText("Read, learn, and grow your English")
        settle(); screenshot("12_home_dark")
        runBlocking { container().settings.setAppTheme(com.englishreader.app.data.prefs.AppTheme.SYSTEM) }
    }

    @Test fun c_arabicInterface() {
        waitText("Home")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        }
        Thread.sleep(2500)
        rule.waitUntil(20_000) { rule.onAllNodesWithText("قاموسي").fetchSemanticsNodes().isNotEmpty() }
        settle(); screenshot("13_home_arabic")
        rule.onAllNodesWithText("قاموسي")[0].performClick()
        settle(); screenshot("14_dictionary_arabic")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
        Thread.sleep(1500)
    }
}
