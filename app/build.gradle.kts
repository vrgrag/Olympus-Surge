import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
    namespace = "com.olympussurge.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.olympussurge.game"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += gdxAbis.keys
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias = keystoreProps["keyAlias"] as String
            keyPassword = keystoreProps["keyPassword"] as String
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
}
