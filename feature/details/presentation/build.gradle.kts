plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)

                implementation(projects.core.domain)
                implementation(projects.core.presentation)
                implementation(projects.feature.details.domain)

                implementation(libs.jetbrains.markdown)

                implementation(compose.components.resources)
                implementation(libs.liquid)
                implementation(libs.kotlinx.datetime)

                implementation(compose.components.uiToolingPreview)
                implementation(libs.bundles.landscapist)
            }
        }

        androidMain {
            dependencies {

            }
        }

        jvmMain {
            dependencies {
                val javafxVersion = "21.0.2"
                val currentOS = org.gradle.internal.os.OperatingSystem.current()
                val platform = when {
                    currentOS.isWindows -> "win"
                    currentOS.isMacOsX -> "mac"
                    else -> "linux"
                }
                implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
                implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")
                implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
                implementation("org.openjfx:javafx-web:$javafxVersion:$platform")
                implementation("org.openjfx:javafx-swing:$javafxVersion:$platform")
            }
        }
    }

}