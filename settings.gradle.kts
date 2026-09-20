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
        // Xposed API (legacy API 82 — same as the Auto Expand module, proven on the target device)
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "DuoStatusBar"
include(":app")
