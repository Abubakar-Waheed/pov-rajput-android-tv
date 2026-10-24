plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// OneDrive can lock generated test directories. Script builds keep all outputs local.
providers.gradleProperty("povBuildRoot").orNull?.let { layout.buildDirectory.set(file(it)) }

android {
    namespace = "dev.pov.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.pov.tv.rajput"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "0.2.6-rajput"
        testInstrumentationRunner = providers.gradleProperty("povTestRunner")
            .orElse("dev.pov.tv.PlaybackAuditInstrumentation").get()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    val releaseStore = providers.environmentVariable("POV_RELEASE_STORE_FILE").orNull
    if (!releaseStore.isNullOrBlank()) {
        signingConfigs.create("distribution") {
            storeFile = file(releaseStore)
            storePassword = providers.environmentVariable("POV_RELEASE_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("POV_RELEASE_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("POV_RELEASE_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Opt-in local sideload release: retain the installed preview certificate and data.
            // Normal release builds remain unsigned for signing with the distributor's own key.
            if (providers.gradleProperty("povPreviewRelease").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
            if (!releaseStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
}
