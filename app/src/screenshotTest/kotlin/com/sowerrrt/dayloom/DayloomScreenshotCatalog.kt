package com.sowerrrt.dayloom

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.sowerrrt.dayloom.core.designsystem.DayloomAnimatedBackground
import com.sowerrrt.dayloom.core.designsystem.DayloomCard
import com.sowerrrt.dayloom.core.designsystem.DayloomSpacing
import com.sowerrrt.dayloom.core.designsystem.DayloomTheme
import com.sowerrrt.dayloom.core.designsystem.DayloomTopBar
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.ThemeMode

private enum class CatalogScreen { HOME, HABITS, PLANNER, LISTS, WISHLIST, SETTINGS }

@PreviewTest
@Preview(name = "home_ru_light_phone", locale = "ru", widthDp = 360, heightDp = 800)
@Preview(
    name = "home_en_dark_wide_large_font",
    locale = "en",
    widthDp = 800,
    heightDp = 600,
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeScreenshot() = Catalog(CatalogScreen.HOME)

@PreviewTest
@Preview(name = "habits_ru_light_phone", locale = "ru", widthDp = 360, heightDp = 800)
@Preview(
    name = "habits_en_dark_compact_large_font",
    locale = "en",
    widthDp = 320,
    heightDp = 640,
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HabitsScreenshot() = Catalog(CatalogScreen.HABITS)

@PreviewTest
@Preview(name = "planner_ru_light_phone", locale = "ru", widthDp = 360, heightDp = 800)
@Preview(
    name = "planner_en_dark_wide_large_font",
    locale = "en",
    widthDp = 800,
    heightDp = 600,
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PlannerScreenshot() = Catalog(CatalogScreen.PLANNER)

@PreviewTest
@Preview(name = "lists_ru_light_phone", locale = "ru", widthDp = 360, heightDp = 800)
@Preview(
    name = "lists_en_dark_compact_large_font",
    locale = "en",
    widthDp = 320,
    heightDp = 640,
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun ListsScreenshot() = Catalog(CatalogScreen.LISTS)

@PreviewTest
@Preview(name = "wishlist_ru_light_phone", locale = "ru", widthDp = 360, heightDp = 800)
@Preview(
    name = "wishlist_en_dark_wide_large_font",
    locale = "en",
    widthDp = 800,
    heightDp = 600,
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun WishlistScreenshot() = Catalog(CatalogScreen.WISHLIST)

@PreviewTest
@Preview(name = "settings_ru_light_phone", locale = "ru", widthDp = 360, heightDp = 800)
@Preview(
    name = "settings_en_dark_compact_large_font",
    locale = "en",
    widthDp = 320,
    heightDp = 640,
    fontScale = 1.3f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun SettingsScreenshot() = Catalog(CatalogScreen.SETTINGS)

@Composable
private fun Catalog(screen: CatalogScreen) {
    val russian = LocalConfiguration.current.locales[0].language == "ru"
    val copy = catalogCopy(screen, russian)
    DayloomTheme(ThemeMode.SYSTEM, AccentPalette.VIOLET) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            DayloomAnimatedBackground()
            Column(Modifier.fillMaxSize()) {
                DayloomTopBar(copy.title, showLogo = screen == CatalogScreen.HOME)
                LazyColumn(
                    contentPadding = PaddingValues(DayloomSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                ) {
                    item { Text(copy.description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(4) { index ->
                        DayloomCard(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DayloomSpacing.sm),
                            ) {
                                Checkbox(checked = index == 2, onCheckedChange = null)
                                Column(Modifier.weight(1f)) {
                                    Text(copy.items[index], style = MaterialTheme.typography.titleMedium)
                                    Text(copy.details[index], color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (screen == CatalogScreen.WISHLIST || screen == CatalogScreen.HOME) {
                                        LinearProgressIndicator(
                                            progress = { (index + 1) / 5f },
                                            modifier = Modifier.fillMaxWidth().padding(top = DayloomSpacing.xs),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CatalogCopy(
    val title: String,
    val description: String,
    val items: List<String>,
    val details: List<String>,
)

private fun catalogCopy(
    screen: CatalogScreen,
    russian: Boolean,
): CatalogCopy {
    val title =
        if (russian) {
            mapOf(
                CatalogScreen.HOME to "Главная",
                CatalogScreen.HABITS to "Привычки",
                CatalogScreen.PLANNER to "План",
                CatalogScreen.LISTS to "Списки",
                CatalogScreen.WISHLIST to "Желания",
                CatalogScreen.SETTINGS to "Настройки",
            ).getValue(screen)
        } else {
            screen.name.lowercase().replaceFirstChar(Char::uppercase)
        }
    return if (russian) {
        CatalogCopy(
            title,
            "Проверка структуры, контраста и крупного шрифта",
            listOf("Полить растения", "Утренняя разминка", "Купить продукты", "Читать 20 минут"),
            listOf("Сегодня · 05:00", "Каждый день · 10 минут", "Высокий приоритет", "В процессе"),
        )
    } else {
        CatalogCopy(
            title,
            "Layout, contrast, and large-text regression coverage",
            listOf("Water the plants", "Morning stretch", "Buy groceries", "Read for 20 minutes"),
            listOf("Today · 05:00", "Every day · 10 minutes", "High priority", "In progress"),
        )
    }
}
