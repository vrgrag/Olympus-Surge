import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Apply the Google Services plugin only if google-services.json is
// present so the project still builds without Firebase credentials
// (the gray flow will simply skip push registration).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Local, git-ignored QA switches for the gray flow. Absent on CI and on
// any machine that has not opted in, so the release path cannot depend
// on them existing.
val oracleProps = Properties().apply {
    val f = rootProject.file("oracle.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun oracleProp(key: String): String = (oracleProps[key] as String?).orEmpty().trim()

fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// libGDX ships its native libraries inside plain jars, one per ABI, so they
// have to be unpacked into jniLibs before the APK is assembled.
val gdxAbis = mapOf(
    "armeabi-v7a" to "natives-armeabi-v7a",
    "arm64-v8a" to "natives-arm64-v8a",
    "x86_64" to "natives-x86_64",
)
val nativesDir = layout.projectDirectory.dir("src/main/jniLibs")

val unpackNatives = gdxAbis.map { (abi, classifier) ->
    val configuration = configurations.create("gdxNatives${abi.replace("-", "")}")
    dependencies.add(
        configuration.name,
        "com.badlogicgames.gdx:gdx-platform:${libs.versions.gdx.get()}:$classifier",
    )
    tasks.register<Copy>("unpackGdxNatives${abi.replace("-", "")}") {
        from(provider { configuration.map { zipTree(it) } })
        include("**/*.so")
        eachFile { path = name }
        includeEmptyDirs = false
        into(nativesDir.dir(abi))
    }
}

android {
    // Keep the Kotlin namespace stable across the game codebase so
    // the existing `R` / `BuildConfig` references in
    // com.olympussurge.game.* continue to resolve. Only the
    // `applicationId` (the actual APK identity used by Play Store)
    // changes per fingerprint requirement.
    namespace = "com.olympussurge.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.olympussurge.olympussurgegame"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        ndk {
            abiFilters += gdxAbis.keys
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.containsKey("storeFile")) {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false

            // No applicationIdSuffix on purpose: AppsFlyer and the
            // OneLink are registered against the production package and
            // attribution never binds to a suffixed one.
            buildConfigField("String", "PROBE_LINK", quoted(oracleProp("oracle.probeLink")))
            buildConfigField("String", "FORCE_AF_STATUS", quoted(oracleProp("oracle.forceStatus")))
            buildConfigField("String", "FORCE_PARAMS", quoted(oracleProp("oracle.forceParams")))
            buildConfigField(
                "boolean",
                "STICKY_VERDICT",
                (oracleProp("oracle.stickyVerdict").ifEmpty { "true" }).lowercase(),
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Compiled out under every circumstance — a QA switch that
            // can reach a store build is a QA switch that will.
            buildConfigField("String", "PROBE_LINK", "\"\"")
            buildConfigField("String", "FORCE_AF_STATUS", "\"\"")
            buildConfigField("String", "FORCE_PARAMS", "\"\"")
            buildConfigField("boolean", "STICKY_VERDICT", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.named("preBuild") {
    dependsOn(unpackNatives)
}

dependencies {
    implementation(project(":game"))
    implementation(project(":core"))
    implementation(project(":engine"))

    // ── AndroidX + Compose ──
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    // Not used directly — forces the ancient fragment that
    // play-services-basement drags in up to a version whose
    // Activity Result plumbing actually works.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // ── Coroutines + serialization for the gray-flow gateways ──
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // ── Gray-flow shell stack ──
    implementation(libs.okhttp)
    implementation(libs.appsflyer.sdk)
    implementation(libs.install.referrer)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
