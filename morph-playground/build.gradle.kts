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
        getByName("desktopTest").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.serialization.json)
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

val desktopTests = tasks.named<Test>("desktopTest") {
    exclude("**/VisualRegressionTest*")
}

tasks.register<Test>("visualRegression") {
    group = "verification"
    description = "Desktop visual comparison; use -Pmorph.visual.mode=candidate to write a review candidate only."
    dependsOn("desktopTestClasses")
    testClassesDirs = desktopTests.get().testClassesDirs
    classpath = desktopTests.get().classpath
    filter.includeTestsMatching("*.VisualRegressionTest")
    systemProperty("morph.visual.root", rootProject.projectDir.absolutePath)
    systemProperty("morph.visual.mode", providers.gradleProperty("morph.visual.mode").getOrElse("compare"))
    systemProperty("morph.visual.suite", providers.gradleProperty("morph.visual.suite").getOrElse("quick"))
    systemProperty("morph.visual.animation", providers.gradleProperty("morph.visual.animation").getOrElse("sample"))
    // Different suites may run alongside verifyMorph; never share Gradle's binary test results.
    val visualSuite = providers.gradleProperty("morph.visual.suite").getOrElse("quick")
    require(visualSuite in listOf("quick", "wide", "infer-wide"))
    if (visualSuite != "quick") {
        binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/visualRegression-$visualSuite/binary"))
        reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/visualRegression-$visualSuite"))
        reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/visualRegression-$visualSuite"))
    }
    outputs.upToDateWhen { false }
    maxHeapSize = "1g"
}

// Explicit publishing command: ordinary tests and report generation never perform network writes.
tasks.register<JavaExec>("publishVisualReport") {
    group = "publishing"
    description = "上传视觉报告图片并生成公开链接；从环境读取 GITHUB_COOKIE。"
    dependsOn("desktopTestClasses")
    classpath = desktopTests.get().classpath
    mainClass.set("li.songe.morph.playground.report.PublishVisualReportKt")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    systemProperty("morph.visual.root", rootProject.projectDir.absolutePath)
    systemProperty("morph.visual.suite", providers.gradleProperty("morph.visual.suite").getOrElse("quick"))
}
