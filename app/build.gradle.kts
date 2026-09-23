import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Single source of truth for the version. Bump both for every release:
//   versionCode: integer, +1 each release (Android uses it to decide what is an upgrade)
//   versionName: semantic version MAJOR.MINOR.PATCH, matches the git tag vX.Y.Z
val appVersionCode = 2
val appVersionName = "1.1.0"

// Release signing is read from keystore.properties (git-ignored). Without it, release falls back to the debug key.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile")?.let { file(it).exists() } == true

// Built-in OpenAI key. OPENAI_API_KEY in the shell is baked into every debug build, and into a release
// build only when FINTRACK_EMBED_KEY=1 is also set: a *personal* build for your own phone. Anyone holding
// such an APK can extract the key, so those files are named "-personal" and must never be shared or
// attached to a GitHub release. The app still lets you change or remove the key in Settings.
// Both settings can live in secrets.properties at the repo root (git-ignored); the environment overrides it.
val secretProps = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String = System.getenv(name)?.takeIf { it.isNotEmpty() } ?: secretProps.getProperty(name, "")
val buildKey = secret("OPENAI_API_KEY").filter { it.isLetterOrDigit() || it in "-_" }
val embedRequested = secret("FINTRACK_EMBED_KEY") == "1"
val personalRelease = embedRequested && buildKey.isNotEmpty()
if (embedRequested && buildKey.isEmpty()) {
    logger.warn("FINTRACK_EMBED_KEY=1 but no OPENAI_API_KEY is set: the release build will have no built-in key.")
}

android {
    namespace = "com.pft.financetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pft.financetracker"
        minSdk = 31
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            // The app starts with OPENAI_API_KEY already saved, so you never type it into the phone.
            // This bakes the key into the APK: never share a debug build made with it set.
            buildConfigField("String", "SEED_OPENAI_KEY", "\"$buildKey\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // No key unless this is an explicitly requested personal build (see buildKey above).
            buildConfigField("String", "SEED_OPENAI_KEY", "\"${if (personalRelease) buildKey else ""}\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
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
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        // Exported Room schemas as *debug* assets so MigrationTest (Robolectric reads the app's merged assets,
        // not unit-test assets) can open a real v1 database and upgrade it. Release builds do not include them.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
    // ML Kit ships a native OCR model per ABI, which makes a single universal APK very large. Splitting
    // by ABI gives each phone only its own copy (~25 MB instead of ~68 MB); the universal APK is still
    // built for sideloading when you do not know the target.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    applicationVariants.all {
        outputs.all {
            val abi = (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .filters.find { it.filterType == "ABI" }?.identifier
            // A release with a built-in key is marked in its file name, so it cannot be mistaken for one to share.
            val personal = if (buildType.name == "release" && personalRelease) "-personal" else ""
            outputFileName = "FinTrack-v${versionName}-${abi ?: "universal"}-${buildType.name}$personal.apk"
        }
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)
    // On-device OCR for bill photos. Bundled model: ships in the APK, works offline, downloads nothing.
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
