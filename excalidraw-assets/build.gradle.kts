
import org.siouan.frontendgradleplugin.infrastructure.gradle.RunNpmTaskType
import org.siouan.frontendgradleplugin.infrastructure.gradle.RunYarnTaskType
import java.nio.file.Files
import java.nio.file.Path

plugins {
    alias(libs.plugins.siouan.frontendJdk17)
}

frontend {
    nodeVersion.set("20.9.0")
    // DON'T use the `build` directory if it also the output of the `react-scripts`
    // otherwise it causes 'Error: write EPIPE' because `node` location is also
    // in the output folder of react-scripts.
    // This projects set up the BUILD_PATH=./build/react-build/ for react-scripts
    nodeInstallDirectory.set(project.layout.buildDirectory.dir("node"))

    assembleScript.set("run build") // "build" script in package.json
    // not implemented yet ?
    //   checkScript.set("run check")
    verboseModeEnabled.set(true)
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
val webappExcalidrawAssets by extra(project.layout.buildDirectory.dir("assets"))
val webappExcalidrawAssetsPath by extra(project.layout.buildDirectory.dir("assets").map { it.asFile.absolutePath })
val webappFiles by extra(project.layout.buildDirectory.dir("react-build"))
val webappFilesPath by extra(project.layout.buildDirectory.dir("react-build").map { it.asFile.absolutePath })

/**
 * Note for future me:
 * Build cache doc: https://docs.gradle.org/current/userguide/build_cache.html
 * Debug task cacheability: -Dorg.gradle.caching.debug=true
 *
 * Disabling `outputs.cacheIf { true }` as it somehow breaks up-to-date check
 */
tasks {
    val updateBrowserList by registering(RunNpmTaskType::class) {
        group = "frontend"
        // Note npx is deprecated, use `npm exec` instead
        // https://docs.npmjs.com/cli/v10/commands/npm-exec#npx-vs-npm-exec
        description = "npx update-browserslist-db@latest"
        // Browserslist: caniuse-lite is outdated. Please run:
        //   npx update-browserslist-db@latest
        //   Why you should do it regularly: https://github.com/browserslist/update-db#readme
        args = "exec -- update-browserslist-db@latest"

        onlyIf {
            gradle.startParameter.taskNames.run {
                contains("assemble") || contains("updateBrowserList")
            }
        }
    }

    installFrontend {
        dependsOn(updateBrowserList)
    }

    val runYarnInstall by registering(RunYarnTaskType::class) {
        dependsOn(installFrontend)
        // this task is being run when the `clean` task is invoked, making this one fail
        // because `excalidraw-assets/build/node/bin/yarn` has been removed.
        onlyIf {
            gradle.startParameter.taskNames.run {
                !contains("clean")
            }
        }
        group = "frontend"
        description = "Runs the yarn install command to fetch packages described in `package.json`"

        inputs.files("package.json")
        outputs.files("yarn.lock")
        args = "install"
    }

    val copyExcalidrawAssets by registering(Copy::class) {
        dependsOn(runYarnInstall, installFrontend)
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
        finalizedBy(runYarnInstall)
        inputs.files("package.json", ".yarnrc.yml", "yarn.lock")
        outputs.dir("node_modules")

        val lockFilePath = "${projectDir}/yarn.lock"
        // The naive configuration below allows to skip the task if the last
        // successful execution did not change neither the `package.json` file,
        // nor the lock file, nor the `node_modules` directory.
        // Any other scenario where, for example, the lock file is regenerated
        // will lead to another execution before the task is "up-to-date" because
        // the lock file is both an input and an output of the task.
        val retainedMetadataFileNames = buildSet {
            add("${projectDir}/package.json")
            if (Files.exists(Path.of(lockFilePath))) {
                add(lockFilePath)
            }
        }

        inputs.files(retainedMetadataFileNames).withPropertyName("metadataFiles")
        outputs.dir("${projectDir}/node_modules").withPropertyName("nodeModulesDirectory")
    }

    assembleFrontend {
        dependsOn(copyExcalidrawAssets)
        inputs.files("package.json", "src", "public")
        outputs.dirs(webappFiles)
    }

    val stopYarnServer by registering(Exec::class) {
        commandLine("bash", "-c", "kill ${'$'}(lsof -t -i :${port.get()})")
        val lsof = providers.exec {
            isIgnoreExitValue = true
            commandLine("lsof", "-t", "-i", ":${port.get()}")
        }

        onlyIf {
            return@onlyIf lsof.standardOutput.asText.get().isNotEmpty()
        }
    }

    register<RunYarnTaskType>("runYarnStart") {
        dependsOn(installFrontend, stopYarnServer)
        group = "frontend"
        description = "Starts yarn, you'll need to actively kill the server after (`kill ${'$'}(lsof -t -i :${port.get()})`)"

        args = "run start"

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

    val deleteFrontendFiles by registering(Delete::class) {
        dependsOn(stopYarnServer)
        delete(
            "${projectDir}/node_modules/",
            "${projectDir}/.yarn/cache/",
            "${projectDir}/.yarn/install-state.gz",
            "${projectDir}/.frontend-gradle-plugin",
            webappFiles,
            webappExcalidrawAssets,
        )
    }

    clean {
        dependsOn(deleteFrontendFiles)
    }
}

open class YarnProxy @Inject constructor(
    objectFactory: ObjectFactory,
    execOperations: ExecOperations
) : RunYarnTaskType(
    objectFactory,
    execOperations
) {
    @set:Option(option = "command", description = "The command to pass to yarn")
    @get:Input
    var yarnArgs: String = ""
        set(value) {
            super.getArgs().set(value)
        }

    init {
        super.getArgs().set(yarnArgs)
    }
}