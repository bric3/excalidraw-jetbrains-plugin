pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.develocity") version "4.3.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}

rootProject.name = "excalidraw-jetbrains-plugin"

include(
    "plugin",
    "excalidraw-assets"
)

// https://docs.gradle.org/current/userguide/build_cache.html
buildCache {
    local {
        directory = File(rootDir, ".gradle/build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        // publishAlways()
        val isCI = providers.environmentVariable("CI").isPresent
        publishing.onlyIf { isCI }
        tag("CI")

        buildScanPublished {
            File("build-scan.txt").printWriter().use { writer ->
                writer.println(buildScanUri)
            }
        }

        if (providers.environmentVariable("GITHUB_ACTIONS").isPresent) {
            link("GitHub Repository", "https://github.com/" + System.getenv("GITHUB_REPOSITORY"))
            link(
                "GitHub Commit",
                "https://github.com/" + System.getenv("GITHUB_REPOSITORY") + "/commits/" + System.getenv("GITHUB_SHA")
            )


            listOf(
                "GITHUB_ACTION_REPOSITORY",
                "GITHUB_EVENT_NAME",
                "GITHUB_ACTOR",
                "GITHUB_BASE_REF",
                "GITHUB_HEAD_REF",
                "GITHUB_JOB",
                "GITHUB_REF",
                "GITHUB_REF_NAME",
                "GITHUB_REPOSITORY",
                "GITHUB_RUN_ID",
                "GITHUB_RUN_NUMBER",
                "GITHUB_SHA",
                "GITHUB_WORKFLOW"
            ).forEach { e ->
                val v = System.getenv(e)
                if (v != null) {
                    value(e, v)
                }
            }

            providers.environmentVariable("GITHUB_SERVER_URL").orNull?.let { ghUrl ->
                val ghRepo = System.getenv("GITHUB_REPOSITORY")
                val ghRunId = System.getenv("GITHUB_RUN_ID")
                link("Summary", "$ghUrl/$ghRepo/actions/runs/$ghRunId")
                link("PRs", "$ghUrl/$ghRepo/pulls")

                // see .github/workflows/build.yaml
                providers.environmentVariable("GITHUB_PR_NUMBER")
                    .orNull
                    .takeUnless { it.isNullOrBlank() }
                    .let { prNumber ->
                        link("PR", "$ghUrl/$ghRepo/pulls/$prNumber")
                    }
            }
        }
    }
}