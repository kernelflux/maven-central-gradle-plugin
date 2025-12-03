import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    id("java-library") // ✅ 需要对外暴露 API
    alias(libs.plugins.plugin.publish)
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
version = "1.0.7"


gradlePlugin {
    website = "https://github.com/kernelflux/maven-central-gradle-plugin"
    vcsUrl = "https://github.com/kernelflux/maven-central-gradle-plugin"
    plugins {
        create("mavenRepositoryPlugin") {
            id = group.toString()
            implementationClass = "com.kernelflux.maven.publish.MavenCentralPlugin"
            displayName = "Gradle Maven Publish Plugin"
            description = "Publish your artifacts(jar/aar/plugin) to maven central portal."
            tags = listOf("Release Plugin", "MavenCentral", "Sonatype")
        }
    }
}

dependencies {
    compileOnly(libs.agp.core)
    implementation(gradleApi())
    implementation(libs.dokka.gradle.plugin)
}
