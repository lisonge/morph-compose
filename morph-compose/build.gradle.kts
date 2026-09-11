import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    android {
        namespace = "li.songe.morph.compose"
        withHostTest {}
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.animation)
            api(libs.compose.foundation)
            api(libs.compose.runtime)
            api(libs.compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("jvmTest").dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
