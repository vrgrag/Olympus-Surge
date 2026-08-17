import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// libGDX ships its native libraries inside plain jars, one per ABI, so they
// have to be unpacked into jniLibs before the APK is assembled.
val gdxAbis = mapOf(
    "armeabi-v7a" to "natives-armeabi-v7a",
    "arm64-v8a" to "natives-arm64-v8a",
    "x86_64" to "natives-x86_64",
)
// Unpacked into the default jniLibs source dir (git-ignored) because the
// Android source set API rejects lazily provided directories.
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
    // Namespace stays as com.olympussurge.game so existing Kotlin sources and
    // manifest ".ActivityName" references keep working. The public identifier
    // on the device (and in google-services.json) is applicationId below.
    namespace = "com.olympussurge.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.olympussurge.olympussurgegame"
        minSdk = 30
        targetSdk = 35
        // versionCode / versionName are picked from the uniqueness manifest.
        // Every future port of this same shape MUST bump both to values
        // that no sibling app is using — a shared 1/"1.0.0" pair is one
        // of the loudest cluster fingerprints in Play Console.
        versionCode = 3
        versionName = "1.0.1"

        ndk {
            abiFilters += gdxAbis.keys
        }
    }

    signingConfigs {
        create("release") {
            val storeFileProp = keystoreProps["storeFile"] as? String
            if (storeFileProp != null) {
                storeFile = file(storeFileProp)
                storePassword = keystoreProps["storePassword"] as? String
                keyAlias = keystoreProps["keyAlias"] as? String
                keyPassword = keystoreProps["keyPassword"] as? String
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // We use ComponentActivity + registerForActivityResult without ever
        // touching androidx.fragment, so the "InvalidFragmentVersionForActivityResult"
        // check is a false positive for this project. Everything else is
        // left at defaults.
        disable += "InvalidFragmentVersionForActivityResult"
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
    // ── project modules ──────────────────────────────────────────────
    implementation(project(":game"))

    // ── attribution + messaging (external SDKs) ─────────────────────
    implementation(libs.appsflyer.sdk)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    // ── network / json ──────────────────────────────────────────────
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // ── coroutines ──────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.android)

    // ── androidx runtime + splash + webview support ─────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ── compose stack ───────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // ── image loading (used by the alert channel for big pictures) ──
    implementation(libs.coil.compose)
}
