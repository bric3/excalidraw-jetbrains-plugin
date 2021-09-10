
import org.siouan.frontendgradleplugin.infrastructure.gradle.AssembleTask
import org.siouan.frontendgradleplugin.infrastructure.gradle.InstallDependenciesTask
import org.siouan.frontendgradleplugin.infrastructure.gradle.RunNpmYarn

plugins {
    id("org.siouan.frontend-jdk11") version "5.3.0"
}


val webappAssets by extra("build/assets")
val webappFiles by extra("build/gulp-dist")

frontend {
    nodeVersion.set("16.3.0")
    yarnEnabled.set(true)
    yarnVersion.set("1.22.10")
    // DONT use the `build` directory if it also the output of the `react-scripts`
    // otherwise it causes 'Error: write EPIPE' because node location is also
    // in the location of the output folder of react-scripts.
    // This projects set up the BUILD_PATH=./build/react-build/ for react-scripts
    nodeInstallDirectory.set(project.layout.buildDirectory.dir("node"))
    yarnInstallDirectory.set(project.layout.buildDirectory.dir("yarn"))

    assembleScript.set("run build") // "build" script in package.json
}

tasks.named<InstallDependenciesTask>("installFrontend") {
    inputs.files("package.json")
    outputs.dir("node_modules")
}

tasks.named<AssembleTask>("assembleFrontend") {
    dependsOn(tasks.named("copyProductionAssets"))
    inputs.files("package.json", "gulpfile.js", "src", "public")
    outputs.dirs(webappFiles)
}

tasks.register<RunNpmYarn>("start") {
    dependsOn(tasks.named("installFrontend"))
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