import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.central.uploader)
}

val sGroupId = "com.kernelflux.sdk"
val sArtifactId = "sample"
val sVersion = "1.0.2"


android {
    namespace = "com.kernelflux.sdk.sample"
    compileSdk = 36

    defaultConfig {
        minSdk = 23

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }


    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

}

mavenCentralUpload{
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
