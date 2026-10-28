plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.ander.rotas"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "br.com.ander.rotas"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }
    val signingEnvironment = listOf(
        "ANDER_KEYSTORE_PATH", "ANDER_KEYSTORE_PASSWORD", "ANDER_KEY_ALIAS", "ANDER_KEY_PASSWORD"
    ).associateWith { System.getenv(it)?.takeIf(String::isNotBlank) }
    require(signingEnvironment.values.all { it == null } || signingEnvironment.values.all { it != null }) {
        "Configure todas as quatro variáveis ANDER_KEYSTORE_PATH, ANDER_KEYSTORE_PASSWORD, ANDER_KEY_ALIAS e ANDER_KEY_PASSWORD para preservar a assinatura."
    }
    signingConfigs {
        getByName("debug") {
            if (signingEnvironment.values.all { it != null }) {
                storeFile = file(signingEnvironment.getValue("ANDER_KEYSTORE_PATH")!!)
                storePassword = signingEnvironment.getValue("ANDER_KEYSTORE_PASSWORD")
                keyAlias = signingEnvironment.getValue("ANDER_KEY_ALIAS")
                keyPassword = signingEnvironment.getValue("ANDER_KEY_PASSWORD")
            }
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.maplibre.gl:android-sdk:11.5.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
