rootProject.name = "excalidraw-jetbrains-plugin"

include(
    "plugin",
    "excalidraw-assets"
)

pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        gradlePluginPortal()
    }
}

// https://docs.gradle.org/current/userguide/build_cache.html
buildCache {
    local {
        directory = File(rootDir, ".gradle/build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}