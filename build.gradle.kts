// Need to declare kotlin plugins in parent build.gradle.kts file to avoid the following error
// > e: org.jetbrains.kotlin.gradle.plugin.ide.dependencyResolvers.IdeVisibleMultiplatformSourceDependencyResolver failed on ...
// org.jetbrains.kotlin.gradle.utils.IsolatedKotlinClasspathClassCastException: The Kotlin Gradle plugin was loaded multiple times in different subprojects, which is not supported and may break the build.
// This might happen in subprojects that apply the Kotlin plugins with the Gradle 'plugins { ... }' DSL if they specify explicit versions, even if the versions are equal.
// Please add the Kotlin plugin to the common parent project or the root project, then remove the versions in the subprojects.
// If the parent project does not need the plugin, add 'apply false' to the plugin line.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
