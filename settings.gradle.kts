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