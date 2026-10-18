import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidLibrary {
        namespace = "com.waytun.shared"
        compileSdk = 37
        minSdk = 29
        // Off by default for KMP Android libraries; Compose resources (strings) ship as Android assets through it.
        androidResources { enable = true }
    }
    jvm("desktop")
    // Compile-only purity check: with a non-JVM target in the build, commonMain can no longer see the JDK, so
    // anything that wouldn't build for iOS later fails now. `./gradlew :shared:compileCommonMainKotlinMetadata -Pwaytun.purityCheck`
    if (providers.gradleProperty("waytun.purityCheck").isPresent) iosSimulatorArm64()

    jvmToolchain(21)

    // android + desktop share "jvmCommon" for code that genuinely needs the JVM (javax.crypto, sockets, DNS);
    // iOS targets will join commonMain later and get their own actuals.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withCompilations { it.target.platformType == KotlinPlatformType.androidJvm || it.target.platformType == KotlinPlatformType.jvm }
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-opt-in=kotlin.uuid.ExperimentalUuidApi", "-opt-in=kotlin.io.encoding.ExperimentalEncodingApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.components.resources)
            api(libs.cmp.material.icons.core)
            api(libs.haze)
            api(libs.jb.lifecycle.viewmodel.compose)
            api(libs.jb.lifecycle.runtime.compose)
            implementation(libs.jb.navigation.compose)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            api(libs.androidx.room.runtime)
        }
        getByName("jvmCommonMain").dependencies {
            implementation(libs.zxing.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.java)
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.waytun.app.res"
    generateResClass = always
}
