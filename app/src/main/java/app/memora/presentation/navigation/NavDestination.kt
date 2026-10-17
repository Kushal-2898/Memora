package app.memora.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import app.memora.R

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    data object Home : Screen(
        route = "home",
        titleRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home",
    )

    data object Scanner : Screen(
        route = "scanner",
        titleRes = R.string.nav_scanner,
        selectedIcon = Icons.Filled.DocumentScanner,
        unselectedIcon = Icons.Outlined.DocumentScanner,
        testTag = "nav_tab_scanner",
    )

    data object History : Screen(
        route = "history",
        titleRes = R.string.nav_history,
        selectedIcon = Icons.Filled.Archive,
        unselectedIcon = Icons.Outlined.Archive,
        testTag = "nav_tab_history",
    )

    data object Settings : Screen(
        route = "settings",
        titleRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings",
    )

    data object Onboarding : Screen(
        route = "onboarding",
        titleRes = R.string.onboarding_title_1,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "screen_onboarding",
    )

    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(Home, Scanner, History, Settings)
        // Backward-compatible alias for existing references
        val Capture: Scanner get() = Scanner
    }
}
