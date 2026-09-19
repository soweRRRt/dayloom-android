pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Dayloom"

include(
    ":app",
    ":benchmark",
    ":core:model",
    ":core:designsystem",
    ":core:ui",
    ":core:storage",
    ":core:security",
    ":core:notifications",
    ":core:updates",
    ":feature:home",
    ":feature:habits",
    ":feature:planner",
    ":feature:lists",
    ":feature:wishlist",
    ":feature:vault",
    ":feature:settings",
)
