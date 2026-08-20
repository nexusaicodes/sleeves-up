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
        minSdk = 34
        targetSdk = 36
        // Sourced from gradle.properties (VERSION_CODE / VERSION_NAME) — the single source of
        // truth. Override per-build with -PVERSION_CODE / -PVERSION_NAME. Fallbacks keep a fresh
        // checkout building if the properties are ever absent.
        versionCode = (project.findProperty("VERSION_CODE") as String? ?: "1").toInt()
        versionName = project.findProperty("VERSION_NAME") as String? ?: "1.0"

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
        // Needed for BuildConfig.DEBUG, which gates the debug-only nudge harness in Settings.
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
    // finding anyone for AuthGate.BIOMETRIC_FALLBACK_AFTER_MS
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

    // Periodic evaluation pass for engagement nudges (see notify/engagement/NudgeWorker)
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
// libyuv BSD, the one non-Apache row left. What it cannot do is notice a new
// dependency, so this task supplies the half that can be automated: every group id on the release
// runtime classpath must be covered by some entry's `coordinates`, or the build fails naming it.
val licenseSourceFile = layout.projectDirectory
    .file("src/main/java/com/checkin/app/ui/about/OpenSourceLibraries.kt")

/** What aapt packages out of res/font — a typeface binary, or an XML family that references them. */
val fontExtensions = setOf("ttf", "otf", "ttc", "xml")

tasks.register("verifyLicenseCoverage") {
    group = "verification"
    description = "Fails if a group id on the release runtime classpath has no licence entry."

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

        val summary = "Licence coverage: ${resolved.size} group ids and ${fontNames.size} fonts, " +
            "all covered by ${declared.size} entries."
        logger.lifecycle(summary)
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("$summary\n")
    }
}

tasks.named("check") { dependsOn("verifyLicenseCoverage") }
