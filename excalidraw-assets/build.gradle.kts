
import org.siouan.frontendgradleplugin.infrastructure.gradle.RunYarn
import java.io.ByteArrayOutputStream

plugins {
    id("org.siouan.frontend-jdk11") version "6.0.0"
}

frontend {
    nodeVersion.set("16.15.1")
    yarnEnabled.set(true)
    yarnVersion.set("1.22.19")
    // DON'T use the `build` directory if it also the output of the `react-scripts`
    // otherwise it causes 'Error: write EPIPE' because node location is also
    // in the location of the output folder of react-scripts.
    // This projects set up the BUILD_PATH=./build/react-build/ for react-scripts
    nodeInstallDirectory.set(project.layout.buildDirectory.dir("node"))

    assembleScript.set("run build") // "build" script in package.json
}

val port = providers.provider {
    // PORT might be is in package.json "/scripts/start" value
    // dumb solution to extract the port if possible
    val defaultPort = 3000
    file("package.json").useLines { lines ->
        val startScriptRegex = Regex("\"start\"\\s?:\\s?\"[^\"]+\"")
        lines
            .filter { startScriptRegex.containsMatchIn(it) }
            .map { Regex("\".*PORT=(\\d+).*\"").find(it)?.groups?.get(1)?.value }
            .map { it?.toInt() }
            .first() ?: defaultPort
    }
}
val webappExcalidrawAssets by extra("${project.buildDir}/assets")
val webappFiles by extra("${project.buildDir}/react-build")

/**
 * Note for future me:
 * Build cache doc : https://docs.gradle.org/current/userguide/build_cache.html
 * Debug task cacheability: -Dorg.gradle.caching.debug=true
 *
 * Disabling `outputs.cacheIf { true }` as it somehow breaks up-to-date check
 */
tasks {
    installNode {
        inputs.property("nodeVersion", frontend.nodeVersion)
        outputs.dir(frontend.nodeInstallDirectory)
    }

    enableYarnBerry {
        outputs.dir(".yarn/releases/")
        // yarnBerryEnableScript = "set version berry"
    }

    installYarn {
        yarnVersion.set(frontend.yarnVersion)
        outputs.file(frontend.yarnVersion.map { ".yarn/releases/yarn-$it.cjs" })
        // yarnInstallScript = "set version 1.22.19"
    }

    installYarnGlobally {
////        outputs.cacheIf { true }
//        // put yarn else where to not pollute Node install folder
//         outputs.dir(frontend.nodeInstallDirectory.map { "$it/lib/node_modules/yarn" })
////        outputs.dir(project.layout.buildDirectory.dir("yarn"))
        onlyIf { frontend.nodeInstallDirectory.map { !file("${it}/lib/node_modules/yarn").exists() }.get() }
    }

    val runYarnInstall by registering(RunYarn::class) {
        dependsOn(installFrontend)
        group = "frontend"
        description = "Runs the yarn install command to fetch packages described in `package.json`"
        
        inputs.files("package.json")
        outputs.files("yarn.lock")
        script.set("install")
    }

    val copyExcalidrawAssets by registering(Copy::class) {
        dependsOn(installFrontend)
        group = "frontend"
        description = "copy necessary files to run the embedded app"

        val excalidrawDist = "node_modules/@excalidraw/excalidraw/dist"
        from(excalidrawDist)
        include("excalidraw-assets/*")  // production assets
        into(webappExcalidrawAssets)

        inputs.dir(excalidrawDist)
        outputs.dir(webappExcalidrawAssets)
    }

    installFrontend {
        inputs.files("package.json", ".yarnrc.yml", "yarn.lock")
        outputs.dir("node_modules")
        finalizedBy(runYarnInstall)
    }

    assembleFrontend {
        dependsOn(copyExcalidrawAssets)
        inputs.files("package.json", "src", "public")
        outputs.dirs(webappFiles)
    }

    val stopYarnServer by registering(Exec::class) {
        commandLine("bash", "-c", "kill ${'$'}(lsof -t -i :${port.get()})")

        onlyIf {
            val output = ByteArrayOutputStream()
            exec {
                isIgnoreExitValue = true
                commandLine("lsof","-t", "-i", ":${port.get()}")
                standardOutput = output
            }

            return@onlyIf output.toString().isNotEmpty()
        }
    }

    register<RunYarn>("runYarnStart") {
        dependsOn(installFrontend, stopYarnServer)
        group = "frontend"
        description = "Starts yarn, you'll need to actively kill the server after (`kill ${'$'}(lsof -t -i :${port.get()})`)"

        script.set("run start")

        doFirst {
            logger.warn(
                """
                Unfortunately node won't be killed on ctrl+c, you to actively kill it:
                    $ kill ${'$'}(lsof -t -i :${port.get()})
    
                An alternative would be (from the project's root folder):
                    $ yarn --cwd excalidraw-assets start
    
                """.trimIndent()
            )
        }
        doLast {
            logger.warn("""test after""")
        }
    }

    register<YarnProxy>("yarn") {
        group = "frontend"
        description = "Run yarn script, e.g. for 'yarn add -D eslint', you can use './gradlew yarn --command=\"add -D eslint\"'"
    }

    clean {
        delete(
            "node_modules",
            webappFiles,
            webappExcalidrawAssets,
        )
    }
}

open class YarnProxy @Inject constructor(
    projectLayout: ProjectLayout,
    objectFactory: ObjectFactory,
    execOperations: ExecOperations
) : RunYarn(
    projectLayout,
    objectFactory,
    execOperations
) {
    @Suppress("UnstableApiUsage")
    @set:Option(option = "command", description = "The command to pass to yarn")
    @get:Input
    var script: String = ""
        set(value) {
            super.getScript().set(value)
        }

    init {
        super.getScript().set(script)
    }
}