import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js(IR) {
        browser {
            binaries.executable()
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                // implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlin.stdlib.common)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    apiVersion = KotlinVersion.KOTLIN_1_8
                    languageVersion = KotlinVersion.KOTLIN_1_8
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
}
