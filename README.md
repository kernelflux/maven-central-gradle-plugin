# Maven Central Plugin for Gradle

A Gradle plugin that simplifies publishing Kotlin/Java/Android libraries to Maven Central. Automatically configures `maven-publish` and `signing` plugins. Compatible with AGP 8.x.

## Latest Version

**1.0.7** - Requires AGP 8.10.1+ and Java 11+

## Quick Start

### Groovy DSL

```gradle
plugins {
  id "com.kernelflux.maven.publish" version "1.0.7"
}
```

### Kotlin DSL

```toml
[versions]
mavencentraluploader = "1.0.7"

[plugins]
maven-central-uploader = { id = "com.kernelflux.maven.publish", version.ref = "mavencentraluploader" }
```

```kotlin
plugins {
    alias(libs.plugins.maven.central.uploader)
}

mavenCentralUpload {
    groupId = "com.example"
    artifactId = "my-library"
    version = "1.0.0"
    
    username = findProperty("sonatypeUsername") as? String
    password = findProperty("sonatypePassword") as? String
    
    signingKeyFile = findProperty("signingKeyFile") as? String
    signingPass = findProperty("signingPass") as? String
    
    pom = Action<MavenPom> {
        name.set("My Library")
        description.set("Library description")
        url.set("https://github.com/example")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("developer")
                name.set("Developer Name")
            }
        }
    }
}
```

## Usage

1. Configure `mavenCentralUpload` block in your `build.gradle.kts`
2. Run `./gradlew publishReleasePublicationToMavenLocal` to publish locally
3. Run `./gradlew deployToMavenCentral` to upload to Maven Central

The `deployToMavenCentral` task is available under the **Maven Central** task group in Gradle.

## License

Copyright (C) 2025 kernelflux - 0kt12

Licensed under the Apache License, Version 2.0
