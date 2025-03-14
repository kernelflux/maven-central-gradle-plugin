# Sonatype Plugin for Gradle

gradle-sonatype-upload is a custom Android Gradle plugin aimed at simplifying the process of uploading Kotlin/Java/Plugin libraries to Sonatype Maven Central. It automatically configures the maven-publish and signing plugins and is compatible with AGP 8.x.

## Latest Version

The latest version is ``0.0.39``. It requires at least __Gradle 8.11.1 and __Java 11__.
To use it with Groovy DSL:

```gradle
plugins {
  id "com.kernelflux.maven.publish" version "0.0.39"
}
```

To use it with KTS :

```toml
[versions]
sonatypeuploader = "0.0.39"

[plugins]
sonatype-uploader = { id = "com.kernelflux.maven.publish", version.ref = "sonatypeuploader" }
```

```kts
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.sonatype.uploader) apply false
}
```

```kts
plugins {
    alias(libs.plugins.sonatype.uploader)
}

sonatypeUpload {
    uploadBundleName = "export_sample_bundle_v${sVersion}"
    username = findProperty("sonatypeUsername") as? String
    password = findProperty("sonatypePassword") as? String

    groupId = sGroupId
    artifactId = sArtifactId
    version = sVersion

    signingKeyFile = rootProject.rootDir.absolutePath + findProperty("signingKeyFile") as? String
    signingPass = findProperty("signingPass") as? String

    pom = Action<MavenPom> {
        name.set(sArtifactId)
        description.set("This is a sdk sample module")
        version.set(sVersion)
        url.set("https://github.com/kernelflux")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("kernelflux")
                name.set("0kt12")
            }
        }
        scm {
            url.set("https://github.com/kernelflux")
            connection.set("https://github.com/kernelflux")
            developerConnection.set("https://github.com/kernelflux")
        }
    }
}
```

# License

[gradle-sonatype-upload/LICENSE at master · kernelflux/gradle-sonatype-upload · GitHub](https://github.com/kernelflux/gradle-sonatype-upload/blob/master/LICENSE)

Copyright (C) 2025 kernelflux - 0kt12

Licensed under the Apache License, Version 2.0
