plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// group = providers.localGradleProperty("pluginGroup").get()
// version = providers.localGradleProperty("pluginVersion").get()

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
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
