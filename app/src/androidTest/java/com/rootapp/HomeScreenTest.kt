package com.rootapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rootapp.ui.home.HomeScreen
import com.rootapp.ui.theme.RootTheme
import com.rootapp.ui.theme.TimeOfDay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented Compose UI test - runs on the emulator via connectedDebugAndroidTest. */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun home_greets_user_and_shows_the_reflection_cta() {
        composeRule.setContent {
            RootTheme(timeOfDay = TimeOfDay.NIGHT, minimalist = false) {
                HomeScreen(userName = "Sam", onStartReflection = {})
            }
        }
        composeRule.onNodeWithText("Hey, Sam").assertIsDisplayed()
        composeRule.onNodeWithText("How are you feeling right now?").assertIsDisplayed()
        composeRule.onNodeWithText("Start a reflection session").assertIsDisplayed()
    }

    @Test fun tapping_the_cta_fires_the_callback() {
        var started = false
        composeRule.setContent {
            RootTheme(timeOfDay = TimeOfDay.DAY, minimalist = true) {
                HomeScreen(userName = "Sam", onStartReflection = { started = true })
            }
        }
        composeRule.onNodeWithText("Start a reflection session").performClick()
        assertTrue("onStartReflection should have fired", started)
    }
}
