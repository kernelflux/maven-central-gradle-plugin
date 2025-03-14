# Sonatype Plugin for Gradle

gradle-sonatype-upload is a custom Android Gradle plugin aimed at simplifying the process of uploading Kotlin/Java/Plugin libraries to Sonatype Maven Central. It automatically configures the maven-publish and signing plugins and is compatible with AGP 8.x.

## Latest Version

The latest version is ``0.0.1``. It requires at least __Gradle 8.11 and __Java 11__.
To use it with Groovy DSL:

```gradle
plugins {
  id "com.kernelflux.maven.publish" version "0.0.1"
}
```

To use it with KTS :

```
[versions]
sonatypeuploader = "0.0.1"

[plugins]
sonatype-uploader = { id = "com.kernelflux.maven.publish", version.ref = "sonatypeuploader" }
```

```
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.sonatype.uploader) apply false
}
```

```
plugins {
    alias(libs.plugins.sonatype.uploader)
}


```
