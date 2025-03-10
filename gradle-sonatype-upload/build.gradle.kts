plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.plugin.publish)
    id("common")
    id("java-gradle-plugin")
    `kotlin-dsl`
    //alias(libs.plugins.sonatype.uploader)
}

group = "com.kernelflux.maven.publish"
version = "0.0.1"

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

}
