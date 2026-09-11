import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set(project.name)
        useEsModules()
        browser {
            commonWebpackConfig {
                outputFileName = "${project.name}.js"
            }
        }
        binaries.executable()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":morph-compose"))
            implementation(libs.compose.material)
            implementation(libs.material.icons.extended)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("desktopMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.core)
                runtimeOnly(libs.slf4j.simple)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "li.songe.morph.playground.desktop.MainKt"
    }
}
