import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("jvm-test-suite")
    kotlin("jvm") version libs.versions.kotlin.get()
    alias(libs.plugins.jetbrains.changelog)
    alias(libs.plugins.jetbrains.intellij)
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
}

dependencies {
}

// Read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName = properties("pluginName")
    version = properties("platformVersion")
    type = properties("platformType")
    downloadSources = properties("platformDownloadSources").toBoolean()
    updateSinceUntilBuild = true

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins = properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty)
}

// Read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version = properties("pluginVersion")
    path = "${rootProject.projectDir}/CHANGELOG.md"
    header = provider { "[${version.get()}] - ${date()}" }
    itemPrefix = "-"
    keepUnreleasedSection = true
    unreleasedTerm = "[Unreleased]"
    groups = emptyList()
    // groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
}

// Java 11 compat started in 2020.3
val jvmLanguageLevel = 17

tasks {
    withType<JavaCompile> {
        options.release.set(jvmLanguageLevel)
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "$jvmLanguageLevel"

            // Match the lowest supported version for this platform
            // See https://plugins.jetbrains.com/docs/intellij/kotlin.html#kotlin-standard-library
            apiVersion = "1.8"
            languageVersion = "1.8"

            // Generates default method in Kotlin interfaces to be usable from Java
            // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.jvm/-jvm-default/
            freeCompilerArgs += "-Xjvm-default=all"
        }
    }

    withType<Test> {
        useJUnitPlatform()
        // needed for com.intellij.testFramework.UsefulTestCase.DELETE_ON_EXIT_HOOK_CLASS
        jvmArgs("--add-opens", "java.base/java.io=ALL-UNNAMED")
    }

    processResources {
        dependsOn(":excalidraw-assets:assemble")
        from(project(":excalidraw-assets").extra["webappFiles"] ?: error("webappFiles not found")) {
            into("assets")
        }
        // local assets will only be loaded if the following variable is set before Excalidraw loads
        // window.EXCALIDRAW_ASSET_PATH = "/";
        from(
            project(":excalidraw-assets").extra["webappExcalidrawAssets"] ?: error("webappExcalidrawAssets not found")
        ) {
            into("assets")
        }
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = provider { null } // removes until-build in plugin.xml

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription = markdownToHTML(
            File(rootProject.projectDir, "./README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n")
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider { changelog.renderItem(changelog.getLatest(), Changelog.OutputType.HTML) })
    }

    runPluginVerifier {
        ideVersions = properties("pluginVerifierIdeVersions").split(',').map(String::trim).filter(String::isNotEmpty)
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first())
    }

    runIde {
        dependsOn(processResources)
        systemProperties["idea.log.debug.categories"] = "#com.github.bric3.excalidraw"
    }
}


testing {
    suites {
        named("test", JvmTestSuite::class) {
            dependencies {
                implementation.add(libs.assertj.core)
                implementation.add(libs.mockk)
            }
        }

        withType(JvmTestSuite::class) {
            useJUnitJupiter(libs.versions.junit.jupiter.get())

            dependencies {
                // IntelliJ seems to ship with an old version of the platform launcher which causes a ClassNotFound in Gradle, pull it in manually
                runtimeOnly("org.junit.platform:junit-platform-launcher")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine")
                runtimeOnly("org.junit.vintage:junit-vintage-engine")
            }

            targets.configureEach {
                testTask.configure {
                    useJUnitPlatform {
                        includeEngines("junit-vintage", "junit-jupiter")
                    }

                    testLogging {
                        showStackTraces = true
                        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
                        events = setOf(
                            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
                            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
                            org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
                        )
                    }
                }
            }
        }
    }
}