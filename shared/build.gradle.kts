import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
    androidLibrary {
        namespace = "com.waytun.shared"
        compileSdk = 37
        minSdk = 29
    }
    jvm("desktop")
    // Compile-only purity check: with a non-JVM target in the build, commonMain can no longer see the JDK, so
    // anything that wouldn't build for iOS later fails now. `./gradlew :shared:compileCommonMainKotlinMetadata -Pwaytun.purityCheck`
    if (providers.gradleProperty("waytun.purityCheck").isPresent) linuxX64()

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
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            api(libs.androidx.room.runtime)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
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
