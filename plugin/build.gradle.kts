
import org.jetbrains.changelog.date
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.9.0"
    id("org.jetbrains.changelog") version "1.3.1"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.mockito:mockito-inline:4.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
}

// Read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))
    downloadSources.set(properties("platformDownloadSources").toBoolean())
    updateSinceUntilBuild.set(true)

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

// Read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(properties("pluginVersion"))
    path.set("${rootProject.projectDir}/CHANGELOG.md")
    header.set(provider { "[${version.get()}] - ${date()}" })
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Unreleased]")
    groups.set(emptyList())
//    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
}

// Java 11 compat started in 2020.3
val JVM_LANGUAGE_LEVEL = 11

tasks {
    // Java 11 compat started in 2020.3
    withType<JavaCompile> {
        options.release.set(JVM_LANGUAGE_LEVEL)
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "$JVM_LANGUAGE_LEVEL"

            // Match the lowest supported version for this platform
            // See https://plugins.jetbrains.com/docs/intellij/kotlin.html#kotlin-standard-library
            apiVersion = "1.4"
            languageVersion = "1.4"

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
        from("${project(":excalidraw-assets").projectDir}/${project(":excalidraw-assets").extra["webappFiles"]}") {
            into("assets")
        }
        // local assets will only be loaded if the following variable is set before Excalidraw loads
        // window.EXCALIDRAW_ASSET_PATH = "/";
        from("${project(":excalidraw-assets").projectDir}/${project(":excalidraw-assets").extra["webappAssets"]}") {
            into("assets")
        }
    }

    patchPluginXml {
        version.set(properties("pluginVersion"))
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(provider { null }) // removes until-build in plugin.xml

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            File(rootProject.projectDir, "./README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider { changelog.getLatest().toHTML() })
    }

    runPluginVerifier {
        ideVersions.set(properties("pluginVerifierIdeVersions").split(',').map(String::trim).filter(String::isNotEmpty))
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))
        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }

    runIde {
        systemProperties["idea.log.debug.categories"] = "#com.github.bric3.excalidraw"
    }
}
