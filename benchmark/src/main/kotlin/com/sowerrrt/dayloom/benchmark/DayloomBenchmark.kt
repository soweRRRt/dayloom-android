package com.sowerrrt.dayloom.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class DayloomBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() =
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }

    @Test
    fun switchPrimaryTabs() =
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            requireNotNull(device.findObject(By.text(Pattern.compile("Habits|Привычки")))).click()
            device.waitForIdle()
            requireNotNull(device.findObject(By.text(Pattern.compile("Plan|План")))).click()
            device.waitForIdle()
            requireNotNull(device.findObject(By.text(Pattern.compile("Lists|Списки")))).click()
            device.waitForIdle()
        }

    private companion object {
        const val PACKAGE_NAME = "com.sowerrrt.dayloom"
    }
}
