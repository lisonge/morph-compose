import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
}

private object Cfg {
    const val androidCompileSdk = 37
    const val androidMinSdk = 21
    val jvmJavaTargetVersion = JavaVersion.VERSION_21
    val androidJavaTargetVersion = JavaVersion.VERSION_11
    val jvmKotlinTarget = JvmTarget.fromTarget(jvmJavaTargetVersion.majorVersion)
    val androidKotlinTarget = JvmTarget.fromTarget(androidJavaTargetVersion.majorVersion)
}

private val publishedModuleNames = setOf(
    "morph-compose",
)

allprojects {
    group = "li.songe.morph"
    version = "0.2.0"
}

tasks.register("verifyReleaseVersion") {
    inputs.property("releaseTag", providers.environmentVariable("GITHUB_REF_NAME").orElse(""))
    inputs.property("expectedTag", "v$version")
    doLast {
        val releaseTag = inputs.properties.getValue("releaseTag")
        val expectedTag = inputs.properties.getValue("expectedTag")
        check(releaseTag == expectedTag) {
            "Release tag '$releaseTag' does not match project version '${expectedTag.toString().removePrefix("v")}' " +
                "(expected '$expectedTag')"
        }
    }
}

subprojects {
    if (name in publishedModuleNames) {
        pluginManager.apply("com.vanniktech.maven.publish")
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        if (name in publishedModuleNames) {
            extensions.configure(KotlinBaseExtension::class.java) {
                explicitApi()
            }
        }

        extensions.configure(KotlinMultiplatformExtension::class.java) {
            jvmToolchain(Cfg.jvmJavaTargetVersion.majorVersion.toInt())

            targets.withType(KotlinJvmTarget::class.java).configureEach {
                compilerOptions {
                    jvmTarget.set(Cfg.jvmKotlinTarget)
                }
            }

            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                compileSdk = Cfg.androidCompileSdk
                minSdk = Cfg.androidMinSdk
                compilerOptions {
                    jvmTarget.set(Cfg.androidKotlinTarget)
                }
            }
        }
    }

    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        configure<MavenPublishBaseExtension> {
            coordinates(project.group.toString(), project.name, project.version.toString())

            if (providers.gradleProperty("signing.keyId").isPresent) {
                publishToMavenCentral()
                signAllPublications()
            }

            val repositoryUrl = "https://github.com/lisonge/morph-compose"
            pom {
                name.set("Morph Icons for Compose")
                description.set("Smooth ImageVector morphing animations for Compose Multiplatform")
                url.set(repositoryUrl)
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("lisonge")
                        email.set("i@songe.li")
                        url.set("https://github.com/lisonge")
                        organization.set("lisonge")
                        organizationUrl.set("https://github.com/lisonge")
                    }
                }
                scm {
                    url.set(repositoryUrl)
                    connection.set("scm:git:https://github.com/lisonge/morph-compose.git")
                    developerConnection.set("scm:git:ssh://git@github.com/lisonge/morph-compose.git")
                }
            }
        }
    }
}
