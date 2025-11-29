# Maven Central Gradle 插件

一个简化将 Kotlin/Java/Android 库发布到 Maven Central 的 Gradle 插件。自动配置 `maven-publish` 和 `signing` 插件。兼容 AGP 8.x。

## 最新版本

**1.0.6** - 需要 AGP 8.10.1+ 和 Java 11+

## 快速开始

### Groovy DSL

```gradle
plugins {
  id "com.kernelflux.maven.publish" version "1.0.6"
}
```

### Kotlin DSL

```toml
[versions]
mavencentraluploader = "1.0.6"

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

## 使用方法

1. 在 `build.gradle.kts` 中配置 `mavenCentralUpload` 块
2. 运行 `./gradlew publishReleasePublicationToMavenLocal` 发布到本地
3. 运行 `./gradlew deployToMavenCentral` 上传到 Maven Central

`deployToMavenCentral` 任务位于 Gradle 的 **Maven Central** 任务组中。

## 许可证

Copyright (C) 2025 kernelflux - 0kt12

Licensed under the Apache License, Version 2.0

