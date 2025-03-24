
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("jvm-test-suite")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.changelog)
    alias(libs.plugins.jetbrains.intellijPlatform)
    alias(libs.plugins.jetbrains.idea.ext)
}

// gradleProperty do not find sub-project gradle.properties
// https://github.com/gradle/gradle/issues/23572
fun ProviderFactory.localGradleProperty(name: String): Provider<String> = provider {
    if (project.hasProperty(name)) project.property(name)?.toString() else null
}

// Note that group, version are not provider aware
// https://github.com/gradle/gradle/issues/13672
group = providers.localGradleProperty("pluginGroup").get()
version = providers.localGradleProperty("pluginVersion").get()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
        jetbrainsRuntime()
    }
}

dependencies {
    intellijPlatform {
        create(
            type = providers.localGradleProperty("platformType"),
            version = providers.localGradleProperty("platformVersion"),
            useInstaller = false
        )
        jetbrainsRuntime() // needed to run the IDE with the correct JBR
        plugins(providers.localGradleProperty("platformPlugins").map { it.split(',') }.getOrElse(emptyList()))
        bundledPlugins(providers.localGradleProperty("platformBundledPlugins").map { it.split(',') }.getOrElse(emptyList()))

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)

        pluginVerifier()
        zipSigner()
    }

    testImplementation(kotlin("test"))
}

// Read more:
// * https://github.com/JetBrains/intellij-platform-gradle-plugin/
// * https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
intellijPlatform {
    projectName.set("excalidraw-intellij-plugin")
    pluginConfiguration {
        id = providers.localGradleProperty("pluginId")
        name = providers.localGradleProperty("pluginName")
        version = providers.localGradleProperty("pluginVersion")

        vendor {
            name = "Brice Dutheil"
            url = "https://github.com/bric3/excalidraw-intellij-plugin"
        }

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(rootProject.layout.projectDirectory.file("./README.md")).asText.map {
            it.lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n")
        }.map {
            markdownToHTML(it)
        }

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider {
            changelog.renderItem(
                changelog.getLatest(),
                Changelog.OutputType.HTML
            )
        })

        ideaVersion {
            sinceBuild = providers.localGradleProperty("pluginSinceBuild")
            untilBuild = provider { null } // removes until-build in plugin.xml
        }

        vendor {
            name = providers.localGradleProperty("pluginVendor")
            url = providers.localGradleProperty("pluginVendorUrl")
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")

        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.localGradleProperty("pluginVersion").map {
            Regex(".+-(\\[a-zA-Z]+).*")
                .find(it)
                ?.groupValues
                ?.getOrNull(1)
                ?: "default"
        }.map { listOf(it) }
    }

    pluginVerification {
        ides {
            ides(providers.localGradleProperty("pluginVerifierIdeVersions").map { it.split(',') }.getOrElse(emptyList()))

            // recommended()
            // channels = listOf(ProductRelease.Channel.RELEASE)
            // select {
            //     types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
            //     channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.RC)
            //     sinceBuild = "223"
            //     untilBuild = "241.*"
            // }
       }
    }

    buildSearchableOptions = false
}

intellijPlatformTesting {
    runIde {
        val runIntelliJUltimate by registering {
            type = IntelliJPlatformType.IntellijIdeaUltimate
            version = providers.localGradleProperty("platformVersion")
        }

        val runIntelliJLatest by register("runIntelliJLatest") {
            type = IntelliJPlatformType.IntellijIdeaCommunity
            version = "2025.1"
            sandboxDirectory = layout.buildDirectory.dir("idea-sandbox/runIntelliJLatest")
        }
    }
}

// Read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version = providers.localGradleProperty("pluginVersion")
    path = "${rootProject.projectDir}/CHANGELOG.md"
    header = provider { "[${version.get()}] - ${date()}" }
    itemPrefix = "-"
    keepUnreleasedSection = true
    unreleasedTerm = "[Unreleased]"
    groups = emptyList()
    // groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
}

// Java 11 compat started in 2020.3
// Java 17 compat started in 2021.3
// Java 21 compat started in 2024.2
val jvmLanguageLevel = 17

kotlin {
    jvmToolchain(jvmLanguageLevel)
}

tasks {
    withType<JavaCompile> {
        options.release.set(jvmLanguageLevel)
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.fromTarget("$jvmLanguageLevel")

            // Match the lowest supported version for this platform
            // See https://plugins.jetbrains.com/docs/intellij/kotlin.html#kotlin-standard-library
            apiVersion = KotlinVersion.KOTLIN_1_8
            languageVersion = KotlinVersion.KOTLIN_1_8

            // Generates default method in Kotlin interfaces to be usable from Java
            // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.jvm/-jvm-default/
            freeCompilerArgs.add("-Xjvm-default=all")
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
        // Local Excalidraw assets will only be loaded if the following variable
        //
        // `window.EXCALIDRAW_ASSET_PATH = "/";`
        //
        // is set before Excalidraw itself loads. The excalidraw assets are copied
        // to the same folder as web-app (thus using "/")
        from(
            project(":excalidraw-assets").extra["webappExcalidrawAssets"] ?: error("webappExcalidrawAssets not found")
        ) {
            into("assets")
        }
    }

    val listProductsReleases by registering() {
        dependsOn(printProductsReleases)
        val outputF = layout.buildDirectory.file("listProductsReleases.txt").also {
            outputs.file(it)
        }
        val content = printProductsReleases.flatMap { it.productsReleases }.map { it.joinToString("\n") }

        doLast {
            outputF.orNull?.asFile?.writeText(content.get())
        }
    }

    prepareSandbox {
        // Should be in runIde but here due to https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1723
        sandboxDirectory = intellijPlatform.sandboxContainer.dir("current")
    }

    runIde {
        dependsOn(processResources)

        // Is there a better way?
        @Suppress("UNCHECKED_CAST")
        val webappPath = (project(":excalidraw-assets").extra["webappFilesPath"] ?: error("webappFilesPath not found"))
            .let { (it as Provider<String>).get() }
        @Suppress("UNCHECKED_CAST")
        val webappExcalidrawAssetsPath = (project(":excalidraw-assets").extra["webappExcalidrawAssetsPath"] ?: error("webappExcalidrawAssetsPath not found"))
            .let { (it as Provider<String>).get() }

        systemProperties(
            "excalidraw.internal.webappPath" to webappPath,
            "excalidraw.internal.webappExcalidrawAssetsPath" to webappExcalidrawAssetsPath,

            // Log levels can be configured from 'Help | Diagnostic Tools | Debug Log Settings',
            // eventually suffixing with the level (e.g. `com.github.bric3.excalidraw:trace`).
            // Or use the following system properties.
            // Configure multiple categories with a comma.
            "idea.log.debug.categories" to "#com.github.bric3.excalidraw",
            // "idea.log.trace.categories" to "com.github.bric3.excalidraw",
            "ide.show.tips.on.startup.default.value" to false,
            "idea.trust.all.projects" to true,
            "jb.consents.confirmation.enabled" to false,
            "ide.browser.jcef.contextMenu.devTools.enabled" to "true",
        )
    }

    withType(RunIdeTask::class).configureEach {
        systemProperties(
            "idea.log.debug.categories" to "#com.github.bric3.excalidraw",
            "ide.experimental.ui" to "true",
            "ide.show.tips.on.startup.default.value" to false,
            "idea.trust.all.projects" to true,
            "jb.consents.confirmation.enabled" to false
        )
    }

    // Latest available EAP release
    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-faq.html#how-to-check-the-latest-available-eap-release
    printProductsReleases {
        channels = listOf(ProductRelease.Channel.EAP)
        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
        untilBuild = provider { null }

        doLast {
            productsReleases.get().max()
        }
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

idea {
    module {
        isDownloadSources = true
    }

    // project?.settings {
    //     taskTriggers {
    //         // Tell IDE to generate sources
    //         afterSync(":excalidraw-assets:...")
    //     }
    // }
}

@Suppress("UnstableApiUsage")
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
                runtimeOnly.add("org.junit.platform:junit-platform-launcher")
                runtimeOnly.add("org.junit.jupiter:junit-jupiter-engine")
                runtimeOnly.add("org.junit.vintage:junit-vintage-engine")
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