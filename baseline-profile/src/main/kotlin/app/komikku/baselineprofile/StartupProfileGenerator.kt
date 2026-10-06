package app.komikku.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// KMK -->

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = TARGET_PACKAGE_NAME,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}

// KMK <--
