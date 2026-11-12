import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kr.pleosapp.getoff"
    compileSdk = 35

    defaultConfig {
        // 가칭. 최종 패키지명은 사용자 확인 후 확정(CLAUDE.md)
        applicationId = "kr.pleosapp.getoff"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "backend"
    productFlavors {
        // fake: SDK 없이 실행되는 데모·테스트 빌드 / pleos: 실제 Pleos SDK 어댑터(검증 전에는 안내 모드 스텁)
        create("fake") { dimension = "backend"; applicationIdSuffix = ".fake"; versionNameSuffix = "-fake" }
        create("pleos") { dimension = "backend" }
    }

    // 서명 정보는 저장소 밖(local.properties)에서만 읽는다. 없으면 서명하지 않은 release가 만들어진다.
    val localProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (localProps.getProperty("release.storeFile") != null) {
            create("release") {
                storeFile = file(localProps.getProperty("release.storeFile"))
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { abortOnError = true }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    // 실제 SDK는 pleos 변형에만 포함한다(fake 변형·core는 SDK 비의존).
    "pleosImplementation"("ai.pleos.playground:Vehicle:2.3.2")
}
