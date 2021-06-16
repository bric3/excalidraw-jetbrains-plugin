import org.siouan.frontendgradleplugin.infrastructure.gradle.AssembleTask
import org.siouan.frontendgradleplugin.infrastructure.gradle.RunNpmYarn

plugins {
    id("org.siouan.frontend-jdk11") version "5.2.0"
}


frontend {
    nodeVersion.set("16.3.0")
    yarnEnabled.set(true)
    yarnVersion.set("1.22.10")
    // DONT use the build directory as it will cause an 'Error: write EPIPE'
    // BAD: nodeInstallDirectory.set(project.layout.buildDirectory.dir("node"))
    // BAD: yarnInstallDirectory.set(project.layout.buildDirectory.dir("yarn"))
    nodeInstallDirectory.set(rootProject.layout.buildDirectory.dir("web/node"))
    yarnInstallDirectory.set(rootProject.layout.buildDirectory.dir("web/yarn"))

    assembleScript.set("run build") // "build" script in package.json
}

tasks.named<AssembleTask>("assembleFrontend") {
    inputs.files("package.json", "gulpfile.js", "src", "public")
    outputs.files("build/gulp-dist/index.html")
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