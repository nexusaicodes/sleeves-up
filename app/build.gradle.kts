import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Optional release signing, populated from a git-ignored keystore.properties at the project root.
// When absent (fresh clone / CI without secrets) the release build falls back to debug signing so it
// still produces an installable artifact. See keystore.properties.template for the expected keys.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.checkin.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nexusai.checkin.app"
        minSdk = 33
        targetSdk = 36
        // Sourced from gradle.properties (VERSION_CODE / VERSION_NAME) — the single source of
        // truth. Override per-build with -PVERSION_CODE / -PVERSION_NAME. Fallbacks keep a fresh
        // checkout building if the properties are ever absent.
        versionCode = (project.findProperty("VERSION_CODE") as String? ?: "1").toInt()
        versionName = project.findProperty("VERSION_NAME") as String? ?: "1.0"

        // AGP template residue: there is no androidTest source set, so nothing runs this. Kept
        // as the declaration a future instrumentation set would need, and harmless until then —
        // adding one also means adding the androidx.test dependency that puts this class on a
        // classpath. See the test-source note further down this file.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Request native debug symbols in the bundle. Today's only native libs (CameraX, and the
            // libyuv it embeds) ship pre-stripped by their vendor, so nothing is extracted and Play's
            // "missing native symbols" warning persists — this is future-proofing for NDK code and
            // costs nothing (symbols are stored server-side and stripped before delivery).
            ndk {
                debugSymbolLevel = "FULL"
            }
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Needed for BuildConfig.VERSION_NAME / VERSION_CODE, which the About card renders and the
        // feedback draft carries. Nothing reads BuildConfig.DEBUG — the app has no debug-only branch.
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // An unreferenced resource is dead weight a reader has to rule out: two plausible empty-state
        // strings for one screen, and no way to tell from the file which one is drawn. Nothing else
        // catches it — verifyLicenseCoverage guards res/font against the classpath, ktlint and detekt
        // never see resources at all, and lint is the only gate that reads them.
        //
        // An error rather than the default warning, for the same reason detekt runs at maxIssues = 0:
        // a warning nobody has to clear is a finding that accumulates. Anything genuinely referenced
        // only indirectly — from the manifest, a theme, or the icon generator — takes a scoped
        // tools:ignore carrying the reason, never a blanket disable here.
        error += "UnusedResources"
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // CameraX
    implementation("androidx.camera:camera-core:1.5.0")
    implementation("androidx.camera:camera-camera2:1.5.0")
    implementation("androidx.camera:camera-lifecycle:1.5.0")
    implementation("androidx.camera:camera-view:1.5.0")

    // Device-unlock fallback, offered when the camera cannot run a check or has looked without
    // finding anyone for DeviceUnlock.DEVICE_UNLOCK_OFFERED_AFTER_MS
    implementation("androidx.biometric:biometric:1.1.0")

    // Declared only to raise the version biometric 1.1.0 would otherwise pin, and it is load-bearing:
    // that release depends on fragment 1.2.5, whose FragmentActivity.startActivityForResult still
    // rejects any request code above 16 bits, while ActivityResultRegistry — which every
    // rememberLauncherForActivityResult goes through — generates them from 65536 up. MainActivity is
    // a FragmentActivity (BiometricPrompt requires one), so the pair crashed the app outright at
    // every runtime permission request: the POST_NOTIFICATIONS ask after the welcome tour, and the
    // camera request inside PresenceGate. Nothing else in the graph pulls fragment forward, and
    // biometric 1.1.0 is still the newest stable, so the floor has to be stated here.
    implementation("androidx.fragment:fragment:1.8.2")

    // Hourly backstop pass: the session-service revive, the nudge re-arm and the send-ledger
    // prune all hang off it (see notify/nudge/NudgeWorker). Nudges are delivered by an alarm.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Testing
    // JUnit plus the coroutines test dispatcher, and nothing else: the suite is pure JVM and mocks
    // nothing, standing every seam up with a hand-written fake instead (app/src/test/.../Fakes.kt).
    // There is no androidTest source set, so no instrumentation dependency has anything to run.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// The open-source licence list in ui/about/OpenSourceLibraries.kt is hand-written: it groups ~220
// resolved artifacts into the upstream projects a reader can act on, and carries the corrections a
// generator reading POMs alone gets wrong where a POM is silent — today that is CameraX's embedded
// libyuv BSD, the one non-Apache row among the Maven groups — the two bundled typefaces carry the
// OFL, and are matched against res/font/ rather than against a coordinate. What it cannot do is
// notice a new dependency, so this task supplies the half that can be automated: every group id on
// the release runtime classpath must be covered by some entry's `coordinates`, or the build fails
// naming it.
val licenseSourceFile = layout.projectDirectory
    .file("src/main/java/com/checkin/app/ui/about/OpenSourceLibraries.kt")

/** What aapt packages out of res/font — a typeface binary, or an XML family that references them. */
val fontExtensions = setOf("ttf", "otf", "ttc", "xml")

tasks.register("verifyLicenseCoverage") {
    group = "verification"
    description = "Fails if a release-classpath group id or a res/font typeface has no licence entry."

    val source = licenseSourceFile
    val fonts = layout.projectDirectory.dir("src/main/res/font")
    val classpath = configurations.named("releaseRuntimeClasspath")
    val stamp = layout.buildDirectory.file("reports/licenseCoverage.txt")
    inputs.file(source)
    inputs.dir(fonts)
    // Gradle only skips a task that declares both inputs and outputs, and resolving the release
    // classpath is not cheap — it is the dependency resolution deliberately kept out of
    // staticAnalysis. Without a declared output the inputs above buy nothing and every `check`
    // pays for it again.
    outputs.file(stamp)

    doLast {
        val coordinates = Regex("""coordinates\s*=\s*"([^"]+)"""")
            .findAll(source.asFile.readText())
            .map { it.groupValues[1] }
            .toList()
        // The group is everything before the first colon, so "androidx.camera:*" and
        // "org.jspecify:jspecify" both yield theirs. Font entries yield "font", which matches no
        // Maven group — they are checked against res/font/ further down instead.
        val declared = coordinates.map { it.substringBefore(':') }
        val fontCoordinates = coordinates.filter { it.startsWith("font:") }.toSet()
        check(declared.isNotEmpty()) {
            "Parsed no coordinates out of ${source.asFile.name}. The entry format changed, and this " +
                "check would otherwise pass by finding nothing to compare against."
        }

        val resolved = classpath.get().incoming.resolutionResult.allComponents
            .mapNotNull { (it.id as? ModuleComponentIdentifier)?.group }
            .toSortedSet()

        val uncovered = resolved.filterNot { group ->
            declared.any { pattern ->
                // "androidx.*" covers androidx and everything beneath it; anything else is exact.
                val prefix = pattern.removeSuffix(".*")
                if (prefix == pattern) group == pattern else group == prefix || group.startsWith("$prefix.")
            }
        }

        check(uncovered.isEmpty()) {
            "These group ids ship in the APK with no entry in ${source.asFile.name}:\n" +
                uncovered.joinToString("\n") { "  - $it" } +
                "\n\nAdd an entry per upstream project, taking the licence from that artifact's POM " +
                "rather than assuming Apache-2.0."
        }

        // Fonts are redistributed the same as any dependency but have no Maven coordinate, so the
        // classpath check above cannot see them. They are matched by resource name instead —
        // otherwise a typeface dropped into res/font/ would ship unattributed and pass silently.
        // Only files aapt would actually package as a font count: anything else in the directory is
        // not redistributed and has no licence to state, and treating it as a typeface fails the
        // build naming a file that is not one (a Finder visit leaves a .DS_Store here).
        val fontNames = fonts.asFile.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() in fontExtensions }
            .map { it.nameWithoutExtension }
            .sorted()
        val unattributed = fontNames.filterNot { name ->
            // "font:outfit_*" covers every weight cut from that family; anything else is exact.
            fontCoordinates.any { pattern ->
                val stem = pattern.removePrefix("font:")
                if (stem.endsWith("*")) name.startsWith(stem.dropLast(1)) else name == stem
            }
        }

        check(unattributed.isEmpty()) {
            "These fonts ship in the APK with no entry in ${source.asFile.name}:\n" +
                unattributed.joinToString("\n") { "  - res/font/$it" } +
                "\n\nAdd an entry with coordinates \"font:<resource name>\", taking the copyright " +
                "from the font's own name table rather than guessing."
        }

        // The reverse direction. The two checks above fail on something shipped with no entry;
        // neither fails on an entry covering nothing shipped, so a dependency that leaves the graph
        // strands its attribution and the screen keeps crediting a library the APK does not carry.
        // That is not a licence risk, but it is a false statement in the one screen whose whole job
        // is to be accurate about what the app redistributes, and nothing else would ever catch it:
        // javax.inject sat there for months after ML Kit took it off the classpath.
        val inert = coordinates.filterNot { pattern ->
            if (pattern.startsWith("font:")) {
                val stem = pattern.removePrefix("font:")
                fontNames.any { if (stem.endsWith("*")) it.startsWith(stem.dropLast(1)) else it == stem }
            } else {
                val prefix = pattern.substringBefore(':').removeSuffix(".*")
                resolved.any { it == prefix || it.startsWith("$prefix.") }
            }
        }

        check(inert.isEmpty()) {
            "These entries in ${source.asFile.name} cover nothing the app ships:\n" +
                inert.joinToString("\n") { "  - $it" } +
                "\n\nA dependency or typeface was removed and its attribution left behind. Delete " +
                "the entry — the Licenses screen must not credit what the APK does not carry."
        }

        val summary = "Licence coverage: ${resolved.size} group ids and ${fontNames.size} fonts, " +
            "all covered by ${declared.size} entries."
        logger.lifecycle(summary)
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("$summary\n")
    }
}

// CLAUDE.md is the tree's map, and the way it goes wrong is mechanical: a file is added, renamed or
// moved and the prose keeps the old name. Two audits running months apart found the same class of
// defect — a paragraph naming a symbol that no longer existed, and a file the map never mentioned —
// so it is checked rather than re-read. Only the file-name half is automated: what a file *does* is
// prose and stays human, exactly as with the licence list.
val docMapFile: RegularFile = rootProject.layout.projectDirectory.file("CLAUDE.md")

tasks.register("verifyDocMap") {
    group = "verification"
    description =
        "Fails if CLAUDE.md and the main sources disagree, or a comment names a gone member of a project type."

    val doc = docMapFile
    val mainSource = layout.projectDirectory.dir("src/main/java")
    // The map names test files too (Fakes.kt, the guards), so the dangling-path half searches both
    // trees. Only main is required to be *documented* — a fixture is found from the test that uses it.
    val testSource = layout.projectDirectory.dir("src/test/java")
    val stamp = layout.buildDirectory.file("reports/docMap.txt")
    val buildScript = layout.projectDirectory.file("build.gradle.kts")
    inputs.file(doc)
    inputs.dir(mainSource)
    inputs.dir(testSource)
    inputs.file(buildScript)
    outputs.file(stamp)

    doLast {
        val text = doc.asFile.readText()
        // Bare-word match, so a name counts however the prose wraps it in backticks or punctuation.
        val words = Regex("""[A-Za-z_][A-Za-z0-9_]*""").findAll(text).map { it.value }.toSet()

        val sourceFiles = mainSource.asFile.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        check(sourceFiles.isNotEmpty()) {
            "Found no .kt files under ${mainSource.asFile}. This check would otherwise pass by " +
                "finding nothing to compare against."
        }

        val undocumented = sourceFiles
            .filterNot { it.nameWithoutExtension in words }
            .map { it.relativeTo(mainSource.asFile).path }
            .sorted()
        check(undocumented.isEmpty()) {
            "These files exist but CLAUDE.md never names them:\n" +
                undocumented.joinToString("\n") { "  - $it" } +
                "\n\nAdd each to the Key Source Paths list with a phrase saying what it holds. A file " +
                "the map omits is one a newcomer can only find by grepping for something they cannot name."
        }

        // The other direction: the map naming a .kt path that no longer resolves, which is what a
        // rename leaves behind. Only explicit paths are checked — a bare symbol may legitimately name
        // something deleted, and this file documents plenty of those on purpose.
        val namedPaths = Regex("""`([A-Za-z0-9_/.]+\.kt)`""").findAll(text).map { it.groupValues[1] }.toSet()
        val allSources = sourceFiles +
            testSource.asFile.walkTopDown().filter { it.isFile && it.extension == "kt" }
        val dangling = namedPaths.filterNot { path ->
            allSources.any { it.path.endsWith(path) } ||
                rootProject.layout.projectDirectory.file(path).asFile.exists() ||
                layout.projectDirectory.file(path).asFile.exists()
        }.sorted()
        check(dangling.isEmpty()) {
            "CLAUDE.md names these files, and none of them exists:\n" +
                dangling.joinToString("\n") { "  - $it" } +
                "\n\nA rename updates both ends, or the map sends a reader somewhere empty."
        }

        // Third direction, and the one the two above cannot see: a comment naming a member that has
        // been renamed or deleted off a project type. Both halves are deliberately narrow, because a
        // noisy gate here is worse than none — this file's own lint reasoning applies.
        //
        // Only *top-level* project types count as owners. That excludes platform names the prose
        // legitimately discusses without calling (Intent.ACTION_SEND, Face.getScore), and it excludes
        // nested types whose simple name collides with a platform one — `ServiceReconciler.Result` is
        // why, since NudgeWorker rightly writes `Result.failure()` for a method it never calls.
        //
        // What it does NOT catch: a reference whose *owner* is gone entirely, which is how
        // `AuthGate.BIOMETRIC_FALLBACK_AFTER_MS` survived in this file. Catching that needs a rule
        // that can tell a deleted project type from a platform one, and every version tried flagged
        // real platform references — measured at 14 false positives to 1 true one. Left uncaught on
        // purpose rather than gated dishonestly.
        val commentPattern = Regex("""/\*(?s:.*?)\*/|//[^\n]*""")
        val sourceText = (sourceFiles + buildScript.asFile).joinToString("\n") { it.readText() }
        val codeOnly = commentPattern.replace(sourceText, " ")
        val codeWords = Regex("""[A-Za-z_][A-Za-z0-9_]*""").findAll(codeOnly).map { it.value }.toSet()
        val topLevelTypes = Regex(
            """(?m)^(?:internal |private |public |abstract |sealed |data |open )*(?:class|object|interface)\s+([A-Z][A-Za-z0-9_]*)""",
        ).findAll(codeOnly).map { it.groupValues[1] }.toSet()

        val staleMembers = sortedSetOf<String>()
        commentPattern.findAll(sourceText).forEach { comment ->
            Regex("""\b([A-Z][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)\b""")
                .findAll(comment.value)
                .forEach { match ->
                    val owner = match.groupValues[1]
                    val member = match.groupValues[2]
                    if (owner in topLevelTypes && member !in codeWords) staleMembers += "$owner.$member"
                }
        }
        check(staleMembers.isEmpty()) {
            "These comments name a member that no longer exists on a project type:\n" +
                staleMembers.joinToString("\n") { "  - $it" } +
                "\n\nA rename updates the prose beside it, or the comment sends a reader after a " +
                "symbol they cannot grep for."
        }

        val summary = "Doc map: ${sourceFiles.size} main sources all named in CLAUDE.md, " +
            "${namedPaths.size} explicit paths all resolve, ${topLevelTypes.size} project types " +
            "with no stale member references."
        logger.lifecycle(summary)
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("$summary\n")
    }
}

tasks.named("check") { dependsOn("verifyLicenseCoverage", "verifyDocMap") }
