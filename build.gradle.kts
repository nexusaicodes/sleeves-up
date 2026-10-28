// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 compiles Kotlin itself (built-in Kotlin) against the Kotlin Gradle plugin it bundles. This
// classpath entry raises that plugin to the Kotlin the compose compiler plugin below is built for;
// AGP documents it as the one way to choose a newer KGP.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

// Static analysis is applied to every project, including this root build script itself, so the
// `.gradle.kts` files are linted too. Detekt's own analysis runs without type resolution: it needs
// no compiled classpath, so `detekt` is runnable on a clean checkout and in CI without a build.
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    detekt {
        // Merge the project's overrides onto detekt's shipped defaults, so the checked-in config
        // only has to state what differs.
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        // Wired up, but the file deliberately does not exist and detekt tolerates that. A baseline
        // turns a finding into permanent invisible debt; the tree is clean, so a new finding gets a
        // real fix or a site-level @Suppress carrying its reason.
        baseline = rootProject.file("config/detekt/baseline.xml")
        // No androidTest set is listed because there is none: the suite is pure JVM and no
        // instrumentation dependency is declared. The kotlin/ dirs are listed against the day one
        // is added; absent dirs cost nothing.
        source.setFrom(
            "src/main/java",
            "src/main/kotlin",
            "src/test/java",
            "src/test/kotlin",
        )
    }

    ktlint {
        // Rule set and code style come from .editorconfig; the plugin only supplies the runner.
        version.set("1.5.0")
        android.set(true)
        ignoreFailures.set(false)
        reporters {
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        }
        filter {
            exclude { it.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
    tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
        jvmTarget = "17"
    }
}

// One entry point for both tools, so CI and the pre-commit path have a single task to call.
tasks.register("staticAnalysis") {
    group = "verification"
    description = "Runs ktlint and detekt across all modules."
    dependsOn(subprojects.map { "${it.path}:ktlintCheck" } + listOf("ktlintCheck"))
    dependsOn(subprojects.map { "${it.path}:detekt" } + listOf("detekt"))
}
