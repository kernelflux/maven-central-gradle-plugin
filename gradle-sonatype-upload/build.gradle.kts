import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.plugin.publish)
    id("java-library")
    id("kotlin")
    id("java-gradle-plugin")
    `kotlin-dsl`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}


tasks.withType(KotlinCompile::class.java) {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}



group = "com.kernelflux.maven.publish"
version = "0.0.39"

@Suppress("UnstableApiUsage")
gradlePlugin {
    website = "https://github.com/kernelflux/gradle-sonatype-upload"
    vcsUrl = "https://github.com/kernelflux/gradle-sonatype-upload"
    plugins {
        create("mavenRepositoryPlugin") {
            id = group.toString()
            implementationClass = "com.kernelflux.maven.publish.SonatypeUploadPlugin"
            displayName = "Gradle Maven Publish Plugin"
            description = "Publish your artifacts(jar/aar/plugin) to sonatype's central portal."
            tags = listOf("Release Plugin", "MavenCentral", "Sonatype")
        }
    }
}

dependencies {
    implementation(libs.agp.core)
    implementation(libs.agp.api)
    implementation(libs.dokka.gradle.plugin)
}
