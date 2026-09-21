package com.sowerrrt.dayloom.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() =
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            includeInStartupProfile = false,
            filterPredicate = { rule -> rule.contains("Lcom/sowerrrt/dayloom/") },
        ) {
            pressHome()
            startActivityAndWait()

            // Critical user journey: open every primary workspace so Compose and the
            // data-backed screens are compiled before a real user reaches them.
            listOf("Habits|Привычки", "Plan|План", "Lists|Списки", "More|Ещё").forEach { label ->
                device.findObject(By.text(Pattern.compile(label)))?.click()
                device.waitForIdle()
            }
        }

    private companion object {
        const val PACKAGE_NAME = "com.sowerrrt.dayloom"
    }
}
