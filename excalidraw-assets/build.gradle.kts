
import org.siouan.frontendgradleplugin.infrastructure.gradle.AssembleTask
import org.siouan.frontendgradleplugin.infrastructure.gradle.InstallDependenciesTask
import org.siouan.frontendgradleplugin.infrastructure.gradle.RunYarn

plugins {
    id("org.siouan.frontend-jdk11") version "6.0.0"
}


val webappAssets by extra("build/assets")
val webappFiles by extra("build/gulp-dist")

frontend {
    nodeVersion.set("16.15.1")
    yarnEnabled.set(true)
    yarnVersion.set("1.22.19")
    // DONT use the `build` directory if it also the output of the `react-scripts`
    // otherwise it causes 'Error: write EPIPE' because node location is also
    // in the location of the output folder of react-scripts.
    // This projects set up the BUILD_PATH=./build/react-build/ for react-scripts
    nodeInstallDirectory.set(project.layout.buildDirectory.dir("node"))

    assembleScript.set("run build") // "build" script in package.json
}

tasks.named<InstallDependenciesTask>("installFrontend") {
    inputs.files("package.json")
    outputs.dir("node_modules")
    finalizedBy("runYarnInstall")
}

tasks.register<RunYarn>("runYarnInstall") {
    dependsOn(tasks.named("installFrontend"))
    inputs.files("package.json")
    outputs.files("yarn.lock")
    script.set("install")
}

tasks.named<AssembleTask>("assembleFrontend") {
    dependsOn(tasks.named("copyProductionAssets"))
    inputs.files("package.json", "gulpfile.js", "src", "public")
    outputs.dirs(webappFiles)
}

tasks.register<RunYarn>("runYarnStart") {
    dependsOn(tasks.named("installFrontend"))
    group = "Frontend"
    description = "Starts yarn, you'll need to actively kill the server after (`kill ${'$'}(lsof -t -i :3000)`)"

    script.set("run start")
    doFirst {
        logger.warn(
            """
            Unfortunately node won't be killed on ctrl+c, you to actively kill it:
                $ kill ${'$'}(lsof -t -i :3000)

            A better alternative would be (from the project's root folder):
                $ yarn --cwd excalidraw-assets start

            """.trimIndent())
    }
    doLast {
        logger.warn("""test after""")
    }
}

tasks.register<Copy>("copyProductionAssets") {
    dependsOn(tasks.named("installFrontend"))

    val excalidrawDist = "node_modules/@excalidraw/excalidraw/dist"
    from(excalidrawDist)
    include("excalidraw-assets/*")  // production assets
    into(webappAssets)
    
    inputs.dir(excalidrawDist)
    outputs.dir(webappAssets)
}